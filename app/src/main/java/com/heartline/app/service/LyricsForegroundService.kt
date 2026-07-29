package com.heartline.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.heartline.app.MainActivity
import com.heartline.app.R
import com.heartline.app.data.PlaybackMode
import com.heartline.app.media.MediaSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LyricsForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.heartline.app.START_LYRICS"
        const val ACTION_STOP = "com.heartline.app.STOP_LYRICS"
        const val ACTION_MINUS = "com.heartline.app.MINUS"
        const val ACTION_PLUS = "com.heartline.app.PLUS"
        const val ACTION_RESET = "com.heartline.app.RESET_SYNC"
        const val CHANNEL_ID = "heartline_live_lyrics_v22"
        const val NOTIFICATION_ID = 2405
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification(NotificationModel()))
        scope.launch {
            MediaSessionRepository.state
                .map { state ->
                    NotificationModel(
                        title = state.track?.title ?: "HEARTLINE",
                        artist = state.track?.artist ?: "Waiting for music…",
                        currentLyric = state.lyrics.getOrNull(state.currentLineIndex)?.text
                            ?: state.plainLyrics?.lineSequence()?.firstOrNull().orEmpty()
                            ?: state.message.orEmpty(),
                        nextLyric = state.lyrics.getOrNull(state.currentLineIndex + 1)?.text.orEmpty(),
                        mode = state.playbackMode,
                        offsetMs = state.perTrackOffsetMs
                    )
                }
                .distinctUntilChanged()
                .collect(::postNotificationSafely)
        }
    }

    private fun postNotificationSafely(model: NotificationModel) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(model))
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and notify().
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MINUS -> MediaSessionRepository.adjustOffset(-500)
            ACTION_PLUS -> MediaSessionRepository.adjustOffset(500)
            ACTION_RESET -> {
                val offset = MediaSessionRepository.state.value.perTrackOffsetMs
                if (offset != 0L) MediaSessionRepository.adjustOffset(-offset)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(model: NotificationModel): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val lyric = model.currentLyric.ifBlank { "Play a song and HEARTLINE will find the words." }
        val modeText = model.mode.name.lowercase().replaceFirstChar(Char::uppercase)
        val offsetText = "%+.1fs".format(model.offsetMs / 1000.0)
        val expandedText = buildString {
            append(lyric)
            if (model.nextLyric.isNotBlank()) append("\n\n${model.nextLyric}")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heartline_notification)
            .setContentTitle(model.title)
            .setContentText(lyric)
            .setSubText("${model.artist} · $modeText · $offsetText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setBigContentTitle(model.title)
                    .setSummaryText("${model.artist} · Lyrics $modeText · Offset $offsetText")
            )
            .setContentIntent(open)
            .setDeleteIntent(serviceAction(ACTION_STOP, 99))
            .addAction(R.drawable.ic_sync_earlier, "Earlier", serviceAction(ACTION_MINUS, 2))
            .addAction(R.drawable.ic_sync_reset, "Reset", serviceAction(ACTION_RESET, 3))
            .addAction(R.drawable.ic_sync_later, "Later", serviceAction(ACTION_PLUS, 4))
            .setColor(ContextCompat.getColor(this, R.color.ic_launcher_background))
            .setColorized(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LyricsForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class NotificationModel(
        val title: String = "HEARTLINE",
        val artist: String = "Waiting for music…",
        val currentLyric: String = "",
        val nextLyric: String = "",
        val mode: PlaybackMode = PlaybackMode.AUTO,
        val offsetMs: Long = 0L
    )
}
