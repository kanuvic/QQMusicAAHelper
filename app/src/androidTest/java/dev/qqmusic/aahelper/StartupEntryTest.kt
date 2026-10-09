package dev.qqmusic.aahelper

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class StartupEntryTest {
    @Test fun personalRadioStartsFromPausedSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val qq = QQMusicController.get(instrumentation.targetContext)
        instrumentation.runOnMainSync { qq.start(); qq.command("pause") }
        val pauseEnd = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < pauseEnd && qq.playback?.state != PlaybackState.STATE_PAUSED) {
            SystemClock.sleep(200)
        }
        assertEquals("Precondition: source QQmusic must really be paused", PlaybackState.STATE_PAUSED, qq.playback?.state)
        instrumentation.runOnMainSync { qq.openPlaybackEntry(StartupMode.RADIO) }
        val end = SystemClock.elapsedRealtime() + 19000
        try {
            while (SystemClock.elapsedRealtime() < end && !qq.status.contains("已开始播放") && !qq.status.startsWith("Timeout")) {
                SystemClock.sleep(200)
            }
            assertTrue("Personal radio entry failed: ${qq.status}", qq.status.contains("已开始播放"))
            assertEquals(PlaybackState.STATE_PLAYING, qq.playback?.state)
            assertFalse("Source metadata must contain a real song", qq.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank())
        } finally { instrumentation.runOnMainSync { qq.command("pause") } }
    }
    @Test fun realQQEntryStartsPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = StartupMode.valueOf(InstrumentationRegistry.getArguments().getString("mode") ?: "RADIO")
        val qq = QQMusicController.get(instrumentation.targetContext)
        instrumentation.runOnMainSync { qq.openPlaybackEntry(mode) }
        val end = SystemClock.elapsedRealtime() + 19000
        while (SystemClock.elapsedRealtime() < end && !qq.status.contains("已开始播放") && !qq.status.startsWith("Timeout")) {
            SystemClock.sleep(200)
        }
        try {
            assertTrue("${mode.name}: ${qq.status}", qq.status.contains("已开始播放"))
            assertEquals(PlaybackState.STATE_PLAYING, qq.playback?.state)
        } finally { instrumentation.runOnMainSync { qq.command("pause") } }
    }
    @Test fun settingsPersistAcrossNewPreferenceReads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = StartupSettings.get(context)
        try {
            for (mode in StartupMode.entries.filter { it.isAvailable }) {
                StartupSettings.set(context, mode)
                assertEquals(mode, StartupSettings.get(context))
            }
        } finally {
            val restore = InstrumentationRegistry.getArguments().getString("restoreMode")
            StartupSettings.set(context, restore?.let { StartupMode.valueOf(it) } ?: previous)
        }
    }
}
