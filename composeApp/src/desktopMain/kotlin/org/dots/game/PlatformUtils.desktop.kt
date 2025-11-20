package org.dots.game

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dots.game.core.ClassSettings
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.net.URI

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

actual suspend fun downloadFileText(fileUrl: String): String {
    return withContext(Dispatchers.IO) {
        URI.create(fileUrl).toURL().openStream().readBytes().decodeToString()
    }
}

fun loadWindowsState(directory: String? = null): WindowState {
    val settingsWrapper = SettingsWrapper.initialize(WindowSettings.DEFAULT, directory = directory, loading = true)
    val settings = settingsWrapper.settings ?: return WindowState()
    val windowStateClass = WindowSettings::class // TODO: inline after KT-80853
    context(settings, windowStateClass, WindowSettings.DEFAULT) {
        val hasWindowPositionKey = settings.hasKey(windowStateClass.getSettingName(WindowPosition::x))
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

fun saveWindowsState(windowState: WindowState, directory: String? = null): Boolean {
    val settingsWrapper = SettingsWrapper.initialize(WindowSettings.DEFAULT, directory = directory, loading = false)
    val settings = settingsWrapper.settings ?: return false
    val windowStateClass = WindowSettings::class // TODO: inline after KT-80853
    context(settings, windowStateClass,
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
    settingsWrapper.save()
    return true
}

data class WindowSettings(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val placement: WindowPlacement,
) : ClassSettings<WindowSettings>() {
    override val default: WindowSettings
        get() = DEFAULT

    companion object {
        val DEFAULT = WindowSettings(0.dp, 0.dp, 1600.dp, 900.dp, WindowPlacement.Floating)
    }
}

@Composable
actual fun SaveFileDialog(
    title: String?,
    selectedFile: String?,
    extension: String,
    onFileSelected: (String?) -> Unit,
    content: String,
) {
    FileDialog(title, selectedFile, listOf(extension), onFileSelected, content)
}

@Composable
actual fun OpenFileDialog(
    title: String?,
    selectedFile: String?,
    allowedExtensions: List<String>,
    onFileSelected: (String?) -> Unit,
) {
    FileDialog(title, selectedFile, allowedExtensions, onFileSelected, content = null)
}

@Composable
private fun FileDialog(
    title: String?,
    selectedFile: String?,
    allowedExtensions: List<String>,
    onFileSelected: (String?) -> Unit,
    content: String?,
) {
    // Save mode doesn't allow multiple extensions
    require(content == null || allowedExtensions.size <= 1)

    val fileDialog = remember {
        val mode = if (content != null) FileDialog.SAVE else FileDialog.LOAD
        FileDialog(null as Frame?, title, mode).apply {
            if (allowedExtensions.isNotEmpty()) {
                // TODO: figure out why it doesn't work on Windows and fix
                filenameFilter = FilenameFilter { _, name ->
                    allowedExtensions.any { ext ->
                        if (ext.isNotEmpty())
                            name.endsWith(ext)
                        else
                            !name.contains('.')
                    }
                }
            }
            if (selectedFile != null) {
                val fileObj = File(selectedFile)
                directory = fileObj.parent
                file = fileObj.name
            }
            isVisible = true
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val selectedFile = fileDialog.file?.let {
                File(fileDialog.directory, it)
            }
            if (selectedFile != null && content != null) {
                selectedFile.writeText(content)
            }
            onFileSelected(selectedFile?.absolutePath)
        }
    }
}

class Desktop(os: OS) : Platform(os)

actual val platform: Platform = run {
    val osName = System.getProperty("os.name").lowercase()
    val os = when {
        osName.contains("win") -> OS.Windows
        osName.contains("nux") -> OS.Linux
        osName.contains("mac") -> OS.MacOS
        else -> OS.Unknown
    }
    Desktop(os)
}