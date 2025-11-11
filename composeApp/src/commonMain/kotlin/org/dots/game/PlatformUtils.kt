package org.dots.game

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun HorizontalScrollbar(scrollState: ScrollState, modifier: Modifier)

@Composable
expect fun VerticalScrollbar(scrollState: ScrollState, modifier: Modifier)

expect fun readFileText(filePath: String): String

expect fun fileExists(filePath: String): Boolean

expect suspend fun downloadFileText(fileUrl: String): String

@Composable
expect fun SaveFileDialog(
    title: String?,
    selectedFile: String?,
    extension: String,
    onFileSelected: (String?) -> Unit,
    content: String,
)

@Composable
expect fun OpenFileDialog(
    title: String?,
    selectedFile: String?,
    allowedExtensions: List<String> = emptyList(),
    onFileSelected: (String?) -> Unit,
)

enum class Platform {
    DESKTOP_WINDOWS,
    DESKTOP_MACOS,
    DESKTOP_LINUX,
    MOBILE_ANDROID,
    MOBILE_IOS,
    WEB;

    val isMobile: Boolean
        get() = this == MOBILE_ANDROID || this == MOBILE_IOS
}

expect val platform: Platform
