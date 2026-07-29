package com.heartline.app.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean

class HeartlineNotificationListener : NotificationListenerService() {
    companion object {
        private val connected = AtomicBoolean(false)

        val isConnected: Boolean
            get() = connected.get()

        fun requestReconnect(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, HeartlineNotificationListener::class.java))
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val fastRefresh = Runnable { MediaSessionRepository.refreshSessions() }
    private val settledRefresh = Runnable { MediaSessionRepository.refreshSessions() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.set(true)
        MediaSessionRepository.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.notification?.isMediaNotification() != true) return
        refreshMediaSessionFast()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.notification?.isMediaNotification() != true) return
        refreshMediaSessionFast()
    }

    private fun refreshMediaSessionFast() {
        // Music apps can publish the notification a fraction before their media-session
        // metadata settles. Refresh now to stop stale lyrics, then twice more to pick up
        // the final title/artist with minimal perceived switching delay.
        handler.removeCallbacks(fastRefresh)
        handler.removeCallbacks(settledRefresh)
        MediaSessionRepository.refreshSessions()
        handler.postDelayed(fastRefresh, 80L)
        handler.postDelayed(settledRefresh, 220L)
    }

    private fun Notification.isMediaNotification(): Boolean =
        category == Notification.CATEGORY_TRANSPORT ||
            extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true

    override fun onListenerDisconnected() {
        handler.removeCallbacksAndMessages(null)
        connected.set(false)
        MediaSessionRepository.onListenerDisconnected()
        requestReconnect(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        connected.set(false)
        MediaSessionRepository.onListenerDisconnected()
        super.onDestroy()
    }
}
