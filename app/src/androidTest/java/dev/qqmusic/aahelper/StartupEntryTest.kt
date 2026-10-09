package dev.qqmusic.aahelper

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class StartupEntryTest {
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
