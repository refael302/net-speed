package app.netstrip.statusbar.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

enum class IconMode {
    DOWNLOAD,
    UPLOAD,
    BOTH,
}

class Prefs(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val enabled = MutableStateFlow(storage.getBoolean(KEY_ENABLED, false))
    val iconMode = MutableStateFlow(readMode())
    val startOnBoot = MutableStateFlow(storage.getBoolean(KEY_BOOT, true))

    fun setEnabled(value: Boolean) {
        storage.edit().putBoolean(KEY_ENABLED, value).apply()
        enabled.value = value
    }

    fun setIconMode(value: IconMode) {
        storage.edit().putString(KEY_ICON, value.name).apply()
        iconMode.value = value
    }

    fun setStartOnBoot(value: Boolean) {
        storage.edit().putBoolean(KEY_BOOT, value).apply()
        startOnBoot.value = value
    }

    private fun readMode(): IconMode {
        val stored = storage.getString(KEY_ICON, IconMode.BOTH.name)
        return IconMode.entries.firstOrNull { it.name == stored } ?: IconMode.BOTH
    }

    private companion object {
        const val FILE = "netstrip"
        const val KEY_ENABLED = "enabled"
        const val KEY_ICON = "icon_mode"
        const val KEY_BOOT = "start_on_boot"
    }
}
