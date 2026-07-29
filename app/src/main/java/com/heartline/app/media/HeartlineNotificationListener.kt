package com.heartline.app.media

import android.service.notification.NotificationListenerService

class HeartlineNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { MediaSessionRepository.refreshSessions() }
    override fun onListenerDisconnected() { MediaSessionRepository.markPermissionMissing() }
}
