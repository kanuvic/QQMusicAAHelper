package dev.qqmusic.aahelper

import android.util.Log
import java.util.ArrayDeque

object DebugLogger {
    private val lines = ArrayDeque<String>()
    fun log(message: String) {
        Log.i("QQMusicAA", message)
        synchronized(lines) { if (lines.size >= 160) lines.removeFirst(); lines.addLast(message) }
    }
    fun text(): String = synchronized(lines) { lines.joinToString("\n") }
}
