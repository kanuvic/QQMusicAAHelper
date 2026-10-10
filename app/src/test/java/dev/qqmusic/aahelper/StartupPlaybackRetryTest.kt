package dev.qqmusic.aahelper

import org.junit.Assert.*
import org.junit.Test

class StartupPlaybackRetryTest {
    private class Fixture {
        var now = 0L
        var playing = false
        var checkHook: (() -> Unit)? = null
        var queued: Pair<Long, () -> Unit>? = null
        val attempts = mutableListOf<Long>()
        val results = mutableListOf<Boolean>()
        val retry = StartupPlaybackRetry(
            schedule = { delay, task -> queued = (now + delay) to task },
            cancelScheduled = { queued = null },
            isPlaying = { checkHook?.invoke(); playing },
            finished = { results.add(it) }
        )
        fun start() = retry.start { attempts.add(now) }
        fun tick() {
            val task = queued ?: error("No scheduled check")
            queued = null; now = task.first; task.second()
        }
    }
    @Test fun sendsTenRetriesThreeSecondsApartThenWaitsForLastResponse() {
        val f = Fixture(); f.start()
        repeat(10) { f.tick() }
        assertEquals((0L..30000L step 3000L).toList(), f.attempts)
        assertTrue(f.results.isEmpty())
        f.tick()
        assertEquals(33000L, f.now)
        assertEquals(listOf(false), f.results)
        assertNull(f.queued)
    }
    @Test fun stopsOncePlaybackIsConfirmed() {
        val f = Fixture(); f.start(); f.tick()
        f.playing = true; f.tick()
        assertEquals(listOf(0L, 3000L), f.attempts)
        assertEquals(listOf(true), f.results)
        assertNull(f.queued)
    }
    @Test fun successfulLastRetryDoesNotReportTimeout() {
        val f = Fixture(); f.start(); repeat(10) { f.tick() }
        f.playing = true; f.tick()
        assertEquals(11, f.attempts.size)
        assertEquals(listOf(true), f.results)
    }
    @Test fun cancellationRejectsEvenAnAlreadyQueuedCallback() {
        val f = Fixture(); f.start()
        val stale = f.queued!!.second
        f.retry.cancel(); stale()
        assertEquals(listOf(0L), f.attempts)
        assertTrue(f.results.isEmpty()); assertNull(f.queued)
    }
    @Test fun newStartupInvalidatesPreviousRun() {
        val f = Fixture(); f.start()
        val stale = f.queued!!.second
        f.now = 1000; f.start(); stale(); f.tick()
        assertEquals(listOf(0L, 1000L, 4000L), f.attempts)
    }
    @Test fun accessLossDuringStateCheckStopsFurtherCommands() {
        val f = Fixture(); f.start()
        f.checkHook = { f.retry.cancel() }; f.tick()
        assertEquals(listOf(0L), f.attempts)
        assertTrue(f.results.isEmpty()); assertNull(f.queued)
    }
    @Test fun eachPlaybackEntryRetriesItsOwnRequestAndStopsOnSuccess() {
        for (mode in listOf("RESUME", "RADIO", "RECENT")) {
            val f = Fixture()
            val requests = mutableListOf<String>()
            f.retry.start { requests.add(mode) }
            repeat(10) { f.tick() }
            assertEquals(List(11) { mode }, requests)
            f.playing = true; f.tick()
            assertEquals(listOf(true), f.results)
            assertNull(f.queued)
        }
    }
    @Test fun switchingEntryNeverResendsTheOldRequest() {
        val f = Fixture()
        val requests = mutableListOf<String>()
        f.retry.start { requests.add("RADIO") }
        val stale = f.queued!!.second
        f.retry.start { requests.add("RECENT") }
        stale(); f.tick()
        assertEquals(listOf("RADIO", "RECENT", "RECENT"), requests)
    }
    @Test fun cancellingWhileSendingDoesNotScheduleAnotherAttempt() {
        val f = Fixture()
        f.retry.start { f.retry.cancel() }
        assertNull(f.queued)
        assertTrue(f.results.isEmpty())
    }
}
