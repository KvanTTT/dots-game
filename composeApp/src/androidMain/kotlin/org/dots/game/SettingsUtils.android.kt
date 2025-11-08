package org.dots.game

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.dots.game.core.ClassSettings

actual var appSettings: Settings? = null

actual class SettingsWrapper<T : ClassSettings<T>>(actual val obj: T) {
    actual companion object {
        lateinit var androidSettings: SharedPreferencesSettings

        actual fun <T : ClassSettings<T>> initialize(obj: T, directory: String?, loading: Boolean): SettingsWrapper<T> {
            return SettingsWrapper(obj)
        }
    }

    actual val settings: Settings? = androidSettings

    actual fun save() {
    }
}