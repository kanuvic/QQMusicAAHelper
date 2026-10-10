package dev.qqmusic.aahelper

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object MediaPageSettings {
    private const val KEY = "media_page_enabled"
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("startup", Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("startup", Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).commit()
        if (!enabled) QQMusicMediaService.closeForSettings()
        syncComponent(context)
        DebugLogger.log("Helper media page enabled=$enabled")
    }

    fun syncComponent(context: Context) {
        val desired = if (isEnabled(context)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = ComponentName(context, QQMusicMediaService::class.java)
        if (context.packageManager.getComponentEnabledSetting(component) != desired)
            context.packageManager.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
    }
}
