package dev.qqmusic.aahelper

import android.service.notification.NotificationListenerService

class QQMusicSessionListener : NotificationListenerService() {
    private var car: CarStartupMonitor? = null
    override fun onListenerConnected() {
        MediaPageSettings.syncComponent(this)
        QQMusicController.get(this).start()
        if (car == null) car = CarStartupMonitor(this)
        car?.start()
    }
    override fun onListenerDisconnected() {
        car?.stop()
        QQMusicController.get(this).accessDisconnected()
    }
    override fun onDestroy() { car?.stop(); super.onDestroy() }
}
