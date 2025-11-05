package org.dots.game

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
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

actual fun writeFileText(filePath: String, content: String) {
    val file = File(filePath)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

actual fun fileExists(filePath: String): Boolean = File(filePath).exists()

actual fun getAppDataDir(): String {
    val userHome = System.getProperty("user.home")
    val appDir = File(userHome, ".dots-game")
    if (!appDir.exists()) {
        appDir.mkdirs()
    }
    return appDir.absolutePath
}

actual fun getTempSgfPath(): String {
    return File(getAppDataDir(), "temp.sgf").absolutePath
}

actual suspend fun downloadFileText(fileUrl: String): String = URI.create(fileUrl).toURL().openStream().use {
    it.readBytes().decodeToString()
}

fun loadWindowsState(): WindowState {
    val windowStateClass = WindowSettings::class // TODO: inline after KT-80853
    context(preferencesSettings, windowStateClass, WindowSettings.DEFAULT) {
        val hasWindowPositionKey = preferencesSettings.hasKey(windowStateClass.getSettingName(WindowPosition::x))

        return WindowState(
            placement = getEnumSetting(WindowSettings::placement),

            position = if (hasWindowPositionKey) {
                WindowPosition.Absolute(
                    getSetting(WindowSettings::x),
                    getSetting(WindowSettings::y),
                )
            } else {
                WindowPosition.PlatformDefault
            },

            size = DpSize(
                getSetting(WindowSettings::width),
                getSetting(WindowSettings::height)
            )
        )
    }
}

fun saveWindowsState(windowState: WindowState) {
    val windowStateClass = WindowSettings::class // TODO: inline after KT-80853
    context(preferencesSettings, windowStateClass,
        WindowSettings(windowState.position.x, windowState.position.y, windowState.size.width, windowState.size.height, windowState.placement))
    {
        // Ignore `isMinimized` to prevent loading the Window in a minimized state

        setSetting(WindowSettings::placement)

        if (windowState.position is WindowPosition.Absolute) {
            setSetting(WindowSettings::x)
            setSetting(WindowSettings::y)
        }

        setSetting(WindowSettings::width)
        setSetting(WindowSettings::height)
    }
}

data class WindowSettings(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val placement: WindowPlacement,
) {
    companion object {
        val DEFAULT = WindowSettings(0.dp, 0.dp, 1280.dp, 1024.dp, WindowPlacement.Floating)
    }
}

@Composable
actual fun openFileDialog(
    title: String,
    allowedExtensions: List<String>,
    onFileSelected: (String?) -> Unit
) {
    val fileDialog = remember {
        FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            if (allowedExtensions.isNotEmpty()) {
                // FileDialog on different platforms handles this differently
                // On Windows, we can set file filter
                val extensionsString = allowedExtensions.joinToString(";") { "*.$it" }
                file = extensionsString
            }
            isVisible = true
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val selectedFile = if (fileDialog.file != null) {
                File(fileDialog.directory, fileDialog.file).absolutePath
            } else {
                null
            }
            onFileSelected(selectedFile)
        }
    }
}