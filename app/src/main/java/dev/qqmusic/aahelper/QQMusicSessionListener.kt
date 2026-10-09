package dev.qqmusic.aahelper

import android.service.notification.NotificationListenerService

class QQMusicSessionListener : NotificationListenerService() {
    override fun onListenerConnected() { QQMusicController.get(this).start() }
    override fun onListenerDisconnected() { QQMusicController.get(this).accessDisconnected() }
}
