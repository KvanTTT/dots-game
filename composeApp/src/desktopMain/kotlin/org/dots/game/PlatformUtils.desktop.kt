package org.dots.game

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.net.URI
import java.util.prefs.Preferences

val preferencesSettings = PreferencesSettings(Preferences.userRoot())
actual var appSettings: Settings? = preferencesSettings

@Composable
actual fun HorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) = HorizontalScrollbar(rememberScrollbarAdapter(scrollState), modifier)

@Composable
actual fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) = VerticalScrollbar(rememberScrollbarAdapter(scrollState), modifier)

actual fun readFileText(filePath: String): String = File(filePath).readText()

actual fun fileExists(filePath: String): Boolean = File(filePath).exists()

actual suspend fun downloadFileText(fileUrl: String): String = URI.create(fileUrl).toURL().openStream().use {
    it.readBytes().decodeToString()
}

fun loadWindowsState(): WindowState {
    val windowStateClass = WindowState::class // TODO: inline after KT-80853
    context(preferencesSettings, windowStateClass) {
        val hasWindowPositionKey = preferencesSettings.hasKey(windowStateClass.getSettingName(WindowPosition::x))

        return WindowState(
            placement = getEnumSetting(WindowState::placement, WindowPlacement.Floating),

            position = if (hasWindowPositionKey) {
                WindowPosition.Absolute(
                    getSetting(WindowPosition::x, 0.dp),
                    getSetting(WindowPosition::y, 0.dp),
                )
            } else {
                WindowPosition.PlatformDefault
            },

            size = DpSize(
                getSetting(DpSize::width, 1280.dp),
                getSetting(DpSize::height, 1024.dp)
            )
        )
    }
}

fun saveWindowsState(windowState: WindowState) {
    val windowStateClass = WindowState::class // TODO: inline after KT-80853
    context(preferencesSettings, windowStateClass) {
        // Ignore `isMinimized` to prevent loading the Window in a minimized state

        setEnumSetting(WindowState::placement, windowState.placement)

        if (windowState.position is WindowPosition.Absolute) {
            setSetting(WindowPosition::x, windowState.position.x)
            setSetting(WindowPosition::y, windowState.position.y)
        }

        setSetting(DpSize::width, windowState.size.width)
        setSetting(DpSize::height, windowState.size.height)
    }
}