package org.dots.game

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import org.dots.game.core.ClassSettings

actual class SettingsWrapper<T : ClassSettings<T>>(actual val obj: T) {
    actual companion object {
        val webSettings = StorageSettings()

        actual fun <T : ClassSettings<T>> initialize(obj: T, directory: String?, loading: Boolean): SettingsWrapper<T> {
            return SettingsWrapper(obj)
        }
    }

    actual val settings: Settings? = webSettings

    actual fun save() {
    }
}