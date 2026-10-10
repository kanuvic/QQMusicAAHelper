package dev.qqmusic.aahelper

/** One initial play request followed by at most ten retries, three seconds apart. */
internal class StartupResumeRetry(
    private val schedule: (Long, () -> Unit) -> Unit,
    private val cancelScheduled: () -> Unit,
    private val isPlaying: () -> Boolean,
    private val sendPlay: () -> Unit,
    private val finished: (Boolean) -> Unit
) {
    private var generation = 0
    private var active = false
    private var retries = 0

    fun cancel() {
        generation++; active = false; cancelScheduled()
    }

    fun start() {
        cancel()
        active = true; retries = 0
        val current = generation
        sendPlay()
        if (active && current == generation) next(current)
    }

    private fun next(current: Int) {
        schedule(3000) {
            if (active && current == generation) {
                val playing = isPlaying()
                // Access loss or another setting may cancel during the state check.
                if (active && current == generation) {
                    if (playing || retries == 10) {
                        active = false; cancelScheduled(); finished(playing)
                    } else {
                        retries++; sendPlay()
                        if (active && current == generation) next(current)
                    }
                }
            }
        }
    }
}
