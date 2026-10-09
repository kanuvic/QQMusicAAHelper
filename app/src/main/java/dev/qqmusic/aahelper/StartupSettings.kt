package dev.qqmusic.aahelper

import android.content.Context

enum class StartupMode(val title: String, val description: String, val uri: String? = null) {
    RESUME("继续上次播放", "恢复 QQ音乐上次的歌曲、进度和播放队列（默认）"),
    RADIO("播放刷歌", "开始播放 QQ音乐的个性化推荐（原猜你喜欢／雷达）",
        "qqmusic://qq.com/media/playPersonalRadio?p=%7B%7D"),
    RECENT("播放最近歌曲", "播放 QQ音乐最近听过的歌曲；部分歌曲需要会员权限",
        "qqmusic://qq.com/ui/myTab?p=%7B%22tab%22%3A%22history%22%2C%22direct_play%22%3Atrue%7D"),
    INHERIT("跟随 Android Auto 设置", "由 Android Auto 决定是否自动开始播放；需要播放时继续上次的队列");

    val isAvailable: Boolean get() = true
}

object StartupSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("startup", Context.MODE_PRIVATE)
    fun get(context: Context): StartupMode {
        val saved = prefs(context).getString("mode", null)
        // Migrate the short-lived pause option to its user-requested replacement.
        if (saved == "PAUSED") return StartupMode.INHERIT
        return StartupMode.entries.firstOrNull { it.name == saved && it.isAvailable } ?: StartupMode.RESUME
    }
    fun set(context: Context, mode: StartupMode) {
        if (mode.isAvailable) prefs(context).edit().putString("mode", mode.name).commit()
    }
}
