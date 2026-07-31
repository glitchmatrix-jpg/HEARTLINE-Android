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
    private val fastRefresh = Runnable { safelyRefreshSessions() }
    private val settledRefresh = Runnable { safelyRefreshSessions() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.set(true)
        runCatching { MediaSessionRepository.onListenerConnected() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (!sbn.isSafeMediaNotification()) return
        refreshMediaSessionFast()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (!sbn.isSafeMediaNotification()) return
        refreshMediaSessionFast()
    }

    private fun refreshMediaSessionFast() {
        handler.removeCallbacks(fastRefresh)
        handler.removeCallbacks(settledRefresh)
        safelyRefreshSessions()
        handler.postDelayed(fastRefresh, 80L)
        handler.postDelayed(settledRefresh, 220L)
    }

    private fun safelyRefreshSessions() {
        if (!connected.get()) return
        runCatching { MediaSessionRepository.refreshSessions() }
    }

    private fun StatusBarNotification?.isSafeMediaNotification(): Boolean {
        val notification = this?.notification ?: return false
        return runCatching {
            notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        }.getOrDefault(false)
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacksAndMessages(null)
        connected.set(false)
        runCatching { MediaSessionRepository.onListenerDisconnected() }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        connected.set(false)
        // Do not start reconnect work while Android is tearing the listener down.
        // onListenerDisconnected() and the repository watchdog handle recovery safely.
        super.onDestroy()
    }
}
