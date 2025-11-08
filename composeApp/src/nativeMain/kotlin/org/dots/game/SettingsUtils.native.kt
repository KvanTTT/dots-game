package org.dots.game

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.dots.game.core.ClassSettings
import org.dots.game.core.ThisAppName
import platform.Foundation.NSUserDefaults

actual class SettingsWrapper<T : ClassSettings<T>>(actual val obj: T) {
    actual companion object {
        val nativeSettings = NSUserDefaultsSettings(NSUserDefaults(suiteName = ThisAppName))

        actual fun <T : ClassSettings<T>> initialize(obj: T, directory: String?, loading: Boolean): SettingsWrapper<T> {
            return SettingsWrapper(obj)
        }
    }

    actual val settings: Settings? = nativeSettings

    actual fun save() {
    }
}