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
import com.heartline.app.media.MediaSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LyricsForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.heartline.app.START_LYRICS"
        const val ACTION_STOP = "com.heartline.app.STOP_LYRICS"
        const val ACTION_PLAY_PAUSE = "com.heartline.app.PLAY_PAUSE"
        const val ACTION_PREV = "com.heartline.app.PREV"
        const val ACTION_NEXT = "com.heartline.app.NEXT"
        const val ACTION_MINUS = "com.heartline.app.MINUS"
        const val ACTION_PLUS = "com.heartline.app.PLUS"
        const val ACTION_FAV = "com.heartline.app.FAV"
        const val CHANNEL_ID = "heartline_live_lyrics"
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
                    val track = state.track
                    NotificationModel(
                        title = track?.title ?: "HEARTLINE",
                        subtitle = track?.artist ?: "Waiting for music…",
                        currentLyric = state.lyrics.getOrNull(state.currentLineIndex)?.text
                            ?: state.message.orEmpty(),
                        nextLyric = state.lyrics.getOrNull(state.currentLineIndex + 1)?.text.orEmpty(),
                        isPlaying = track?.isPlaying == true,
                        isFavourite = state.isFavourite
                    )
                }
                .distinctUntilChanged()
                .collect { model ->
                    if (canPostNotifications()) {
                        try {
                            NotificationManagerCompat.from(this@LyricsForegroundService)
                                .notify(NOTIFICATION_ID, buildNotification(model))
                        } catch (_: SecurityException) {
                            // Permission may be revoked between the check and the notify call.
                        }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> MediaSessionRepository.transportPlayPause()
            ACTION_PREV -> MediaSessionRepository.transportPrevious()
            ACTION_NEXT -> MediaSessionRepository.transportNext()
            ACTION_MINUS -> MediaSessionRepository.adjustOffset(-500)
            ACTION_PLUS -> MediaSessionRepository.adjustOffset(500)
            ACTION_FAV -> MediaSessionRepository.toggleFavourite()
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
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(model: NotificationModel): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun serviceAction(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LyricsForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fullText = listOf(model.currentLyric, model.nextLyric)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .ifBlank { "Play a song and HEARTLINE will find the words." }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_heartline)
            .setContentTitle(model.title)
            .setContentText(model.currentLyric.ifBlank { model.subtitle })
            .setSubText(model.subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setContentIntent(open)
            .setDeleteIntent(serviceAction(ACTION_STOP, 99))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_previous, "Previous", serviceAction(ACTION_PREV, 2))
            .addAction(
                if (model.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (model.isPlaying) "Pause" else "Play",
                serviceAction(ACTION_PLAY_PAUSE, 3)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", serviceAction(ACTION_NEXT, 4))
            .addAction(0, "−0.5s", serviceAction(ACTION_MINUS, 5))
            .addAction(0, "+0.5s", serviceAction(ACTION_PLUS, 6))
            .addAction(0, if (model.isFavourite) "Unfavourite" else "Favourite", serviceAction(ACTION_FAV, 7))
            .addAction(0, "Stop", serviceAction(ACTION_STOP, 8))
            .build()
    }

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
        val subtitle: String = "Waiting for music…",
        val currentLyric: String = "",
        val nextLyric: String = "",
        val isPlaying: Boolean = false,
        val isFavourite: Boolean = false
    )
}
