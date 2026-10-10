package dev.qqmusic.aahelper

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.CheckBox

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "设置"
        actionBar?.setDisplayHomeAsUpEnabled(true)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply { addView(body) })
        body.addView(TextView(this).apply { text = "连接车机时"; textSize = 24f })
        body.addView(TextView(this).apply {
            text = "选择连接车机后的播放方式，下次连接生效。默认跟随 Android Auto 设置，也可选择继续上次播放、播放刷歌或播放最近歌曲。"
            textSize = 15f; setPadding(0, 16, 0, 24)
        })
        val choices = RadioGroup(this)
        val selected = StartupSettings.get(this)
        for (mode in StartupMode.entries) {
            choices.addView(RadioButton(this).apply {
                id = mode.ordinal + 1
                text = "${mode.title}${if (mode.isAvailable) "" else "（暂时无效）"}\n${mode.description}"
                isEnabled = mode.isAvailable
                if (!mode.isAvailable) setTextColor(android.graphics.Color.GRAY)
                textSize = 17f; setPadding(0, 20, 0, 20)
            })
        }
        choices.check(selected.ordinal + 1)
        choices.setOnCheckedChangeListener { _, id ->
            StartupMode.entries.getOrNull(id - 1)?.takeIf { it.isAvailable }?.let { StartupSettings.set(this, it) }
        }
        body.addView(choices)
        body.addView(CheckBox(this).apply {
            text = "显示 AA助手播放页面"
            textSize = 17f; setPadding(0, 32, 0, 12)
            isChecked = MediaPageSettings.isEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked -> MediaPageSettings.setEnabled(this@SettingsActivity, checked) }
        })
        body.addView(TextView(this).apply {
            text = "默认开启，提供助手的媒体浏览和播放控制页面。如果出现重复卡片，可关闭此项；关闭后只准备 QQ音乐可能没有播放卡片，建议选择“继续上次播放”。修改后请断开并重新连接车机。"
            textSize = 15f
        })
    }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
