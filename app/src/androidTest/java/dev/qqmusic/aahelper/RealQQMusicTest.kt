package dev.qqmusic.aahelper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Integration tests ONLY: no mocked source/session, all assertions observe installed QQ Music. */
class RealQQMusicTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val access = ComponentName(context, QQMusicSessionListener::class.java)
    private fun qq(): MediaController? = manager.getActiveSessions(access).firstOrNull { it.packageName == QQMusicController.PACKAGE }
    private fun artworkMatches(bridge: MediaControllerCompat): Boolean {
        val actual = qq()?.metadata
        val art = actual?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: actual?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: return false
        val mirror = bridge.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: bridge.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: bridge.metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: return false
        val ratio = minOf(1f, 256f / maxOf(art.width, art.height))
        val normalized = android.graphics.Bitmap.createScaledBitmap(art, maxOf(1, (art.width * ratio).toInt()), maxOf(1, (art.height * ratio).toInt()), true)
        if (normalized.width != mirror.width || normalized.height != mirror.height) return false
        val expected = IntArray(normalized.width * normalized.height); val observed = IntArray(expected.size)
        normalized.getPixels(expected, 0, normalized.width, 0, 0, normalized.width, normalized.height)
        mirror.getPixels(observed, 0, mirror.width, 0, 0, mirror.width, mirror.height)
        return expected.contentEquals(observed)
    }
    private fun waitFor(message: String, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 22000
        while (SystemClock.elapsedRealtime() < end) { if (predicate()) return; SystemClock.sleep(200) }
        fail(message)
    }
    private fun withBridge(run: (MediaControllerCompat) -> Unit) {
        val originalPage = MediaPageSettings.isEnabled(context)
        instrumentation.runOnMainSync { MediaPageSettings.setEnabled(context, true) }
        val latch = CountDownLatch(1)
        lateinit var browser: MediaBrowserCompat
        instrumentation.runOnMainSync {
            browser = MediaBrowserCompat(context, ComponentName(context, QQMusicMediaService::class.java), object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() { latch.countDown() }
            }, null)
            browser.connect()
        }
        try {
            assertTrue("Real bridge browser did not connect", latch.await(10, TimeUnit.SECONDS))
            val bridge = MediaControllerCompat(context, browser.sessionToken)
            try { run(bridge) }
            finally {
                bridge.transportControls.pause()
                if (qq() != null) {
                    waitFor("Cleanup: QQ reaches PAUSED before the next scenario", { qq()?.playbackState?.state == PlaybackState.STATE_PAUSED })
                    SystemClock.sleep(750)
                }
            }
        } finally { instrumentation.runOnMainSync { browser.disconnect(); MediaPageSettings.setEnabled(context, originalPage) } }
    }
    @Test fun pausedSessionAndControls() = withBridge { bridge ->
        waitFor("QQ_SESSION_FOUND", { qq() != null })
        bridge.transportControls.play()
        try {
            waitFor("QQ PLAY must reach STATE_PLAYING", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING })
            waitFor("TITLE / ARTIST mirror", {
                val actual = qq()?.metadata
                val mirror = bridge.metadata
                !actual?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() &&
                    actual?.getString(MediaMetadata.METADATA_KEY_TITLE) == mirror?.getString(MediaMetadata.METADATA_KEY_TITLE) &&
                    !actual?.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank() &&
                    actual?.getString(MediaMetadata.METADATA_KEY_ARTIST) == mirror?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            })
            bridge.transportControls.pause()
            waitFor("QQ PAUSE must reach STATE_PAUSED", { qq()?.playbackState?.state == PlaybackState.STATE_PAUSED })
        } finally { bridge.transportControls.pause() }
    }
    @Test fun nextPreviousAndArtwork() = withBridge { bridge ->
        waitFor("QQ_SESSION_FOUND", { qq() != null })
        val title = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        try {
            bridge.transportControls.skipToNext()
            waitFor("QQ NEXT changes metadata", {
                val next = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                !next.isNullOrBlank() && next != title
            })
            // QQ publishes intermediate metadata before loading the new track. Let the
            // official player settle before sending the opposite transport operation.
            waitFor("NEXT completes loading", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING })
            SystemClock.sleep(1500)
            val next = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            bridge.transportControls.skipToPrevious()
            waitFor("QQ PREVIOUS changes metadata", {
                val previous = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                !previous.isNullOrBlank() && previous != next
            })
            waitFor("ARTWORK pixels must match real QQ metadata", { artworkMatches(bridge) })
        } finally { bridge.transportControls.pause() }
    }
    @Test fun missingSessionRecovery() {
        assertNull("Precondition: QQ must have NO active session; never force-stop here", qq())
        withBridge { bridge ->
            bridge.transportControls.play()
            try {
                waitFor("QQ creates session and actually plays after recovery", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING })
                waitFor("Recovered metadata mirrors real title", {
                    val title = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    !title.isNullOrBlank() && title == bridge.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                })
            } finally { bridge.transportControls.pause() }
        }
    }
    @Test fun qqChangesTrackExternally() = withBridge { bridge ->
        waitFor("QQ_SESSION_FOUND", { qq() != null })
        try {
            qq()!!.transportControls.play()
            waitFor("QQ settles before external track command", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING })
            SystemClock.sleep(1500)
            val original = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            // Bypass the helper for the initiating action; its callback must observe QQ.
            qq()!!.transportControls.skipToNext()
            waitFor("QQ external track change mirrors automatically", {
                val title = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                !title.isNullOrBlank() && title != original && title == bridge.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            })
            waitFor("Externally selected QQ track finishes loading", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING })
            waitFor("External artwork update matches source pixels", { artworkMatches(bridge) })
        } finally { bridge.transportControls.pause() }
    }
    @Test fun sessionDestroyedAndRecreatedForceStop() = withBridge { bridge ->
        waitFor("QQ_SESSION_FOUND", { qq() != null })
        val oldToken = qq()!!.sessionToken
        DebugLogger.log("TEST T90 USER_FORCE_STOP; not NORMAL_PROCESS_DEATH")
        instrumentation.uiAutomation.executeShellCommand("am force-stop com.tencent.qqmusic").close()
        // Media3's legacy bridge may publish an empty metadata object rather than null.
        waitFor("Destroyed session clears stale mirror title", {
            qq() == null && bridge.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        })
        bridge.transportControls.play()
        try {
            waitFor("Recreated QQ token and PLAYING", {
                val actual = qq()
                actual != null && actual.sessionToken != oldToken && actual.playbackState?.state == PlaybackState.STATE_PLAYING
            })
            waitFor("Recreated session metadata mirrored", {
                val title = qq()?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                !title.isNullOrBlank() && title == bridge.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            })
        } finally { bridge.transportControls.pause() }
    }
    @Test fun reconnectAndRapidControls() {
        withBridge { bridge ->
            waitFor("QQ_SESSION_FOUND", { qq() != null })
            for (i in 0..2) {
                bridge.transportControls.play()
                waitFor("Rapid PLAY $i", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING })
                bridge.transportControls.pause()
                waitFor("Rapid PAUSE $i", { qq()?.playbackState?.state == PlaybackState.STATE_PAUSED })
            }
        }
        withBridge { bridge ->
            bridge.transportControls.play()
            try { waitFor("Reconnected PLAY", { qq()?.playbackState?.state == PlaybackState.STATE_PLAYING }) }
            finally { bridge.transportControls.pause() }
        }
    }
}
