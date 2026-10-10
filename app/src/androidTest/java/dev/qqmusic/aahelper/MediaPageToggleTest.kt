package dev.qqmusic.aahelper

import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaPageToggleTest {
    @Test fun disablingPageReleasesOnlyHelperSession() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, QQMusicSessionListener::class.java)
        val component = ComponentName(context, QQMusicMediaService::class.java)
        val original = MediaPageSettings.isEnabled(context)
        fun sessions() = manager.getActiveSessions(listener)
        val qqToken = sessions().firstOrNull { it.packageName == "com.tencent.qqmusic" }?.sessionToken
        assertNotNull("Prepare a real QQ media session first", qqToken)
        var browser: MediaBrowserCompat? = null
        val connected = CountDownLatch(1)
        try {
            i.runOnMainSync {
                MediaPageSettings.setEnabled(context, true)
                browser = MediaBrowserCompat(context, component, object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() { connected.countDown() }
                }, null)
                browser?.connect()
            }
            assertTrue("Enabled bridge connects", connected.await(10, TimeUnit.SECONDS))
            assertTrue(sessions().any { it.packageName == context.packageName })
            i.runOnMainSync { MediaPageSettings.setEnabled(context, false) }
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (sessions().any { it.packageName == context.packageName } && SystemClock.elapsedRealtime() < deadline)
                SystemClock.sleep(100)
            assertFalse("No helper MediaSession survives disabling", sessions().any { it.packageName == context.packageName })
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(component))
            assertEquals("Official QQ session remains intact", qqToken, sessions().firstOrNull { it.packageName == "com.tencent.qqmusic" }?.sessionToken)
        } finally {
            i.runOnMainSync {
                browser?.disconnect()
                MediaPageSettings.setEnabled(context, original)
            }
        }
    }
}
