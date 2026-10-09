package dev.qqmusic.aahelper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.provider.Settings
import android.media.MediaMetadata
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.support.v4.media.MediaBrowserCompat

class MainActivity : Activity() {
    private lateinit var qq: QQMusicController
    private lateinit var status: TextView
    private lateinit var notificationAccessButton: Button
    private lateinit var browser: MediaBrowserCompat
    private var findingSession = false
    private val findSession = Runnable {
        try { qq.start() }
        finally {
            findingSession = false
            render() // Also refresh when the same existing session is found.
            status.minHeight = 0
        }
    }
    private val update: () -> Unit = { render() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); qq = QQMusicController.get(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 28) }
        setContentView(ScrollView(this).apply { addView(body) })
        body.addView(TextView(this).apply { text = "QQ音乐AA助手"; textSize = 26f })
        status = TextView(this).apply { textSize = 16f }; body.addView(status)
        fun button(label: String, run: () -> Unit) { body.addView(Button(this).apply { text = label; setOnClickListener { run() } }) }
        button("设置") { startActivity(Intent(this, SettingsActivity::class.java)) }
        notificationAccessButton = Button(this).apply {
            text = "开启通知使用权"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        body.addView(notificationAccessButton)
        button("启动 QQ音乐") { openQQMusic() }
        button("启动 QQ音乐并应用设置") { launchWithSettings() }
        button("查找 Session") {
            findingSession = true
            status.minHeight = status.height // Keep buttons still while text is blank.
            status.text = ""
            status.removeCallbacks(findSession)
            status.postDelayed(findSession, 350)
        }
        for (action in listOf("play", "pause", "previous", "next")) button(action) { qq.command(action) }
        button("日志") { android.app.AlertDialog.Builder(this).setTitle("QQMusicAA").setMessage(DebugLogger.text()).setPositiveButton("关闭", null).show() }
        browser = MediaBrowserCompat(this, ComponentName(this, QQMusicMediaService::class.java), object : MediaBrowserCompat.ConnectionCallback() {}, null)
    }
    private fun launchWithSettings() {
        if (!qq.access) {
            qq.start()
            Toast.makeText(this, "请先开启通知使用权", Toast.LENGTH_LONG).show()
            return
        }
        val mode = StartupSettings.get(this)
        // URL modes already open QQMusic at the selected entry. Other modes
        // explicitly show its launcher even when an existing session is attached.
        if (mode.uri == null && !openQQMusic()) return
        DebugLogger.log("Phone startup preview: ${mode.name}")
        qq.applyStartupMode(mode)
        val message = if (mode == StartupMode.INHERIT)
            "已应用：${mode.title}。手机测试只准备会话，不主动播放"
        else "已应用：${mode.title}"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    private fun openQQMusic(): Boolean {
        // A foreground phone button opens the UI even if a media session already
        // exists. Car-side prepare intentionally only ensures session readiness.
        val launch = packageManager.getLaunchIntentForPackage(QQMusicController.PACKAGE)
        if (launch == null) {
            Toast.makeText(this, "未找到 QQ音乐，请先安装 QQ音乐", Toast.LENGTH_LONG).show()
            DebugLogger.log("Phone launch: QQMusic launcher unavailable")
            return false
        }
        try {
            startActivity(launch)
            DebugLogger.log("Phone launch: QQMusic launcher submitted")
            return true
        } catch (e: RuntimeException) {
            Toast.makeText(this, "无法打开 QQ音乐，请检查安装状态", Toast.LENGTH_LONG).show()
            DebugLogger.log("Phone launch error: ${e.javaClass.simpleName}")
            return false
        }
    }
    override fun onStart() { super.onStart(); browser.connect(); qq.observe(update); qq.start() }
    override fun onResume() { super.onResume(); render() }
    override fun onStop() {
        status.removeCallbacks(findSession); findingSession = false; status.minHeight = 0
        qq.unobserve(update); browser.disconnect(); super.onStop()
    }
    private fun render() {
        notificationAccessButton.setTextColor(if (qq.access) Color.BLACK else Color.RED)
        if (findingSession) return
        val meta = qq.metadata
        val playbackLabel = when (qq.playback?.state) {
            android.media.session.PlaybackState.STATE_PLAYING -> "Playing"
            android.media.session.PlaybackState.STATE_PAUSED -> "Paused"
            null, android.media.session.PlaybackState.STATE_NONE -> "None"
            else -> "State ${qq.playback?.state}"
        }
        status.text = "\nQQ音乐：${if (qq.installed) "Installed" else "Not installed"}\nNotification Access：${if (qq.access) "Granted" else "Missing"}\nQQ Music Session：${if (qq.connected) "Connected" else "Missing"}\nPlayback：$playbackLabel\nTrack：${meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()}\nArtist：${meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()}\n${qq.status}\n"
    }
}
