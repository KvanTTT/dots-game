package org.dots.game

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import org.dots.game.core.ClassSettings
import org.dots.game.core.ThisAppName
import java.io.File
import java.nio.file.Paths
import java.util.Properties
import java.util.prefs.Preferences

actual var appSettings: Settings? = PreferencesSettings(Preferences.userRoot())

actual class SettingsWrapper<T : ClassSettings<T>> private constructor(
    actual val obj: T,
    actual val settings: Settings?,
    private val propertiesFile: File,
    private val properties: Properties,
) {
    actual companion object {
        actual fun <T : ClassSettings<T>> initialize(obj: T, directory: String?, loading: Boolean): SettingsWrapper<T> {
            val normalizedDirectory = directory ?: System.getProperty("user.home")
            val appDirectory = Paths.get(normalizedDirectory, ThisAppName)

            val propertiesFile = File(appDirectory.toString(), obj::class.simpleName + ".properties")
            if (!loading) {
                // Don't create a settings file without necessity
                appDirectory.toFile().mkdirs()
                propertiesFile.createNewFile()
            }

            val properties = Properties().apply {
                if (propertiesFile.exists()) {
                    propertiesFile.inputStream().use { load(it) }
                }
            }
            return SettingsWrapper(obj, PropertiesSettings(properties), propertiesFile, properties)
        }
    }

    actual fun save() {
        propertiesFile.outputStream().use {
            properties.store(it, null)
        }
    }
}