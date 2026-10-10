package dev.qqmusic.aahelper

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** Requires a real DHU disconnect/reconnect by the test operator; never sends a fake car broadcast. */
class HiddenCarStartupTest {
    @Test fun resumeWithoutHelperSessionOnRealReconnect() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, QQMusicSessionListener::class.java)
        fun sessions() = manager.getActiveSessions(listener)
        fun qq() = sessions().firstOrNull { it.packageName == "com.tencent.qqmusic" }
        val originalMode = StartupSettings.get(context)
        val originalPage = MediaPageSettings.isEnabled(context)
        lateinit var connection: androidx.lifecycle.LiveData<Int>
        i.runOnMainSync { connection = CarConnection(context).type }
        val disconnected = AtomicBoolean(false)
        val reconnected = AtomicBoolean(false)
        val observer = Observer<Int> { type ->
            if (type == CarConnection.CONNECTION_TYPE_NOT_CONNECTED) disconnected.set(true)
            if (type == CarConnection.CONNECTION_TYPE_PROJECTION && disconnected.get()) reconnected.set(true)
        }
        try {
            i.runOnMainSync {
                StartupSettings.set(context, StartupMode.RESUME)
                MediaPageSettings.setEnabled(context, false)
                connection.observeForever(observer)
            }
            i.uiAutomation.executeShellCommand("am force-stop com.tencent.qqmusic").use {
                java.io.FileInputStream(it.fileDescriptor).readBytes()
            }
            val missingDeadline = SystemClock.elapsedRealtime() + 5000
            while (qq() != null && SystemClock.elapsedRealtime() < missingDeadline) SystemClock.sleep(100)
            assertNull("QQ source must be absent before reconnect", qq())
            DebugLogger.log("HIDDEN_RECONNECT_TEST_READY")
            val deadline = SystemClock.elapsedRealtime() + 45000
            while ((!reconnected.get() || qq()?.playbackState?.state != PlaybackState.STATE_PLAYING) &&
                SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
            assertTrue("A real car disconnect/reconnect must occur", reconnected.get())
            assertEquals("Hidden helper restores real QQ playback", PlaybackState.STATE_PLAYING, qq()?.playbackState?.state)
            assertFalse("Hidden helper has no media session", sessions().any { it.packageName == context.packageName })
        } finally {
            i.runOnMainSync {
                connection.removeObserver(observer)
                StartupSettings.set(context, originalMode)
                MediaPageSettings.setEnabled(context, originalPage)
                QQMusicController.get(context).command("pause")
            }
        }
    }
}
