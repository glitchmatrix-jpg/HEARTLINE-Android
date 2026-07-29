package com.heartline.app.media

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.set(true)
        MediaSessionRepository.refreshSessions()
    }

    override fun onListenerDisconnected() {
        connected.set(false)
        MediaSessionRepository.reconnect()
        requestReconnect(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        connected.set(false)
        super.onDestroy()
    }
}
