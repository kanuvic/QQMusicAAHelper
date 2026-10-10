package dev.qqmusic.aahelper

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/** Runs while Android binds the granted notification listener; no helper MediaSession required. */
class CarStartupMonitor(context: Context) {
    private val context = context.applicationContext
    private val connection = CarConnection(this.context).type
    private var projected = false
    private var observing = false
    private val observer = Observer<Int> { type ->
        val next = type == CarConnection.CONNECTION_TYPE_PROJECTION
        if (next != projected) {
            projected = next
            DebugLogger.log("Car connection projection=$next")
            if (next) {
                val mode = StartupSettings.get(this.context)
                DebugLogger.log("Android Auto connected via CarConnection; startup=${mode.name}")
                QQMusicController.get(this.context).applyStartupMode(mode)
            }
        }
    }
    fun start() {
        if (observing) return
        observing = true
        connection.observeForever(observer)
    }
    fun stop() {
        if (!observing) return
        connection.removeObserver(observer)
        observing = false; projected = false
    }
}
