package dev.qqmusic.aahelper

import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class StartupEntryTest {
    @Test fun manualPauseCancelsStartupRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val qq = QQMusicController.get(instrumentation.targetContext)
        instrumentation.runOnMainSync { qq.start() }
        assertTrue("Precondition: a real QQmusic session", qq.connected)
        val before = DebugLogger.text().split("Startup resume play attempt").size
        try {
            instrumentation.runOnMainSync { qq.applyStartupMode(StartupMode.RESUME); qq.command("pause") }
            SystemClock.sleep(3500)
            assertEquals(PlaybackState.STATE_PAUSED, qq.playback?.state)
            assertEquals("Explicit pause must prevent the scheduled retry", 1,
                DebugLogger.text().split("Startup resume play attempt").size - before)
        } finally { instrumentation.runOnMainSync { qq.command("pause") } }
    }
    @Test fun resumeRetriesWhenQQIsPausedBeforeFirstCheck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val qq = QQMusicController.get(context)
        instrumentation.runOnMainSync { qq.start(); qq.command("pause") }
        val pauseEnd = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < pauseEnd && qq.playback?.state != PlaybackState.STATE_PAUSED) SystemClock.sleep(100)
        assertEquals(PlaybackState.STATE_PAUSED, qq.playback?.state)
        val before = DebugLogger.text().split("Startup resume play attempt").size
        try {
            instrumentation.runOnMainSync { qq.applyStartupMode(StartupMode.RESUME) }
            SystemClock.sleep(1100)
            // Pause at the source, without the helper's explicit pause/cancel path.
            val manager = context.getSystemService(android.media.session.MediaSessionManager::class.java)
            val listener = android.content.ComponentName(context, QQMusicSessionListener::class.java)
            manager.getActiveSessions(listener).first { it.packageName == QQMusicController.PACKAGE }.transportControls.pause()
            SystemClock.sleep(200)
            assertEquals("Simulated source pause must take effect", PlaybackState.STATE_PAUSED, qq.playback?.state)
            val end = SystemClock.elapsedRealtime() + 10000
            while (SystemClock.elapsedRealtime() < end && !qq.status.contains("继续上次播放：QQ音乐已开始播放")) SystemClock.sleep(100)
            assertEquals(PlaybackState.STATE_PLAYING, qq.playback?.state)
            assertTrue(qq.status, qq.status.contains("继续上次播放：QQ音乐已开始播放"))
            assertTrue("A retry must actually be sent", DebugLogger.text().split("Startup resume play attempt").size - before >= 2)
        } finally { instrumentation.runOnMainSync { qq.command("pause") } }
    }

    @Test fun newDefaultAndStoredModesRemainStable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("startup", android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString("mode", null)
        try {
            prefs.edit().remove("mode").commit()
            assertEquals(StartupMode.INHERIT, StartupSettings.get(context))
            assertEquals(StartupMode.INHERIT, StartupMode.entries.first())
            prefs.edit().putString("mode", "RESUME").commit()
            assertEquals(StartupMode.RESUME, StartupSettings.get(context))
            prefs.edit().putString("mode", "unknown").commit()
            assertEquals(StartupMode.INHERIT, StartupSettings.get(context))
        } finally { prefs.edit().putString("mode", saved).commit() }
    }
    @Test fun personalRadioStartsFromPausedSession() = entryStartsFromPausedSession(StartupMode.RADIO)
    @Test fun recentSongsStartFromPausedSession() = entryStartsFromPausedSession(StartupMode.RECENT)

    private fun entryStartsFromPausedSession(mode: StartupMode) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val qq = QQMusicController.get(instrumentation.targetContext)
        instrumentation.runOnMainSync { qq.start(); qq.command("pause") }
        val pauseEnd = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < pauseEnd && qq.playback?.state != PlaybackState.STATE_PAUSED) {
            SystemClock.sleep(200)
        }
        assertEquals("Precondition: source QQmusic must really be paused", PlaybackState.STATE_PAUSED, qq.playback?.state)
        instrumentation.runOnMainSync { qq.applyStartupMode(mode) }
        val end = SystemClock.elapsedRealtime() + 19000
        try {
            while (SystemClock.elapsedRealtime() < end && !qq.status.contains("已开始播放") && !qq.status.startsWith("Timeout")) {
                SystemClock.sleep(200)
            }
            assertTrue("${mode.name} entry failed: ${qq.status}", qq.status.contains("已开始播放"))
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
