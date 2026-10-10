package dev.qqmusic.aahelper

import android.os.Bundle

internal object MediaDiagnostics {
    @Suppress("DEPRECATION")
    fun bundle(bundle: Bundle?): String = if (bundle == null) "null" else try {
        bundle.keySet().filterNotNull().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            val value = bundle.get(key)
            val text = when {
                Regex("token|password|secret|authorization", RegexOption.IGNORE_CASE).containsMatchIn(key) -> "[redacted]"
                value == null -> "null"
                value is String || value is Number || value is Boolean -> value.toString().take(200)
                else -> value.javaClass.simpleName
            }
            "$key=$text"
        }
    } catch (e: RuntimeException) { "unreadable:${e.javaClass.simpleName}" }
}
