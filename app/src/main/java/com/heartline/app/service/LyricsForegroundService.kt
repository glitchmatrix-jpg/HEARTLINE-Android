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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.heartline.app.HeartlineApplication
import com.heartline.app.MainActivity
import com.heartline.app.R
import com.heartline.app.data.ListenerConnection
import com.heartline.app.data.PlaybackMode
import com.heartline.app.media.MediaSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class LyricsForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.heartline.app.START_LYRICS"
        const val ACTION_STOP = "com.heartline.app.STOP_LYRICS"
        const val ACTION_MINUS = "com.heartline.app.MINUS"
        const val ACTION_PLUS = "com.heartline.app.PLUS"
        const val ACTION_RESET = "com.heartline.app.RESET_SYNC"
        const val ACTION_RECONNECT = "com.heartline.app.RECONNECT"
        const val CHANNEL_ID = "heartline_live_lyrics_v23"
        const val NOTIFICATION_ID = 2405
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cachedArtworkUri: String? = null
    private var cachedArtwork: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification(NotificationModel()))
        val app = applicationContext as HeartlineApplication
        scope.launch {
            combine(MediaSessionRepository.state, app.settings.settings) { state, settings ->
                NotificationModel(
                    title = state.track?.title ?: "HEARTLINE",
                    artist = state.track?.artist ?: "Waiting for music…",
                    currentLyric = state.lyrics.getOrNull(state.currentLineIndex)?.text
                        ?: state.plainLyrics?.lineSequence()?.firstOrNull().orEmpty()
                        ?: state.message.orEmpty(),
                    nextLyric = state.lyrics.getOrNull(state.currentLineIndex + 1)?.text.orEmpty(),
                    mode = state.playbackMode,
                    offsetMs = state.perTrackOffsetMs,
                    connection = state.listenerConnection,
                    detail = settings.notificationDetail,
                    privacyMode = settings.privacyMode,
                    artworkUri = if (settings.showArtwork) state.track?.artworkUri else null
                )
            }.distinctUntilChanged().collect(::postNotificationSafely)
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
            ACTION_RECONNECT -> MediaSessionRepository.reconnect()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cachedArtwork?.recycle()
        cachedArtwork = null
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
        val connected = model.connection == ListenerConnection.CONNECTED
        val lyric = when (model.detail) {
            "song_only" -> model.artist
            else -> model.currentLyric.ifBlank { if (connected) "Play a song and HEARTLINE will find the words." else "Music connection needs attention." }
        }
        val modeText = model.mode.name.lowercase().replaceFirstChar(Char::uppercase)
        val offsetText = "%+.1fs".format(model.offsetMs / 1000.0)
        val expandedText = when (model.detail) {
            "song_only" -> model.artist
            "current" -> lyric
            else -> buildString {
                append(lyric)
                if (model.nextLyric.isNotBlank()) append("\n\n${model.nextLyric}")
            }
        }
        val visibility = if (model.privacyMode == "hide_lyrics_locked") NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE
        val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heartline_notification)
            .setContentTitle(model.title)
            .setContentText(if (model.privacyMode == "show_lyrics") lyric else model.artist)
            .build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heartline_notification)
            .setContentTitle(model.title)
            .setContentText(lyric)
            .setSubText(if (connected) "${model.artist} · $modeText · $offsetText" else "HEARTLINE · Reconnecting")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setBigContentTitle(model.title)
                    .setSummaryText(if (connected) "${model.artist} · Lyrics $modeText · Offset $offsetText" else "Music connection interrupted")
            )
            .setContentIntent(open)
            .setDeleteIntent(serviceAction(ACTION_STOP, 99))
            .setColor(ContextCompat.getColor(this, R.color.ic_launcher_background))
            .setColorized(false)
            .setVisibility(visibility)
            .setPublicVersion(publicVersion)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (connected) {
            builder
                .addAction(R.drawable.ic_sync_earlier, "Earlier", serviceAction(ACTION_MINUS, 2))
                .addAction(R.drawable.ic_sync_reset, "Reset", serviceAction(ACTION_RESET, 3))
                .addAction(R.drawable.ic_sync_later, "Later", serviceAction(ACTION_PLUS, 4))
        } else {
            builder.addAction(R.drawable.ic_sync_reset, "Reconnect", serviceAction(ACTION_RECONNECT, 5))
        }
        loadArtwork(model.artworkUri)?.let(builder::setLargeIcon)
        return builder.build()
    }

    private fun loadArtwork(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        if (uriString == cachedArtworkUri && cachedArtwork != null && cachedArtwork?.isRecycled == false) return cachedArtwork
        val decoded = runCatching {
            contentResolver.openInputStream(Uri.parse(uriString))?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        val max = 256
        val scale = minOf(1f, max / maxOf(decoded.width, decoded.height).toFloat())
        val result = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        if (result !== decoded) decoded.recycle()
        cachedArtwork?.takeIf { it !== result }?.recycle()
        cachedArtworkUri = uriString
        cachedArtwork = result
        return result
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
        val offsetMs: Long = 0L,
        val connection: ListenerConnection = ListenerConnection.UNKNOWN,
        val detail: String = "current_next",
        val privacyMode: String = "hide_lyrics_locked",
        val artworkUri: String? = null
    )
}
