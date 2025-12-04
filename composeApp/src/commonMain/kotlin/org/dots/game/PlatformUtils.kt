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

expect object UrlEncoderDecoder {
    fun encode(value: String): String
    fun decode(value: String): String
}

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

@Composable
expect fun Tooltip(
    text: String,
    content: @Composable () -> Unit
)

enum class OS {
    Windows,
    MacOS,
    Linux,
    Android,
    Native,
    Unknown,
}

abstract class Platform(val os: OS) {
    val isMobile get() = os == OS.Android || os == OS.Native
    val supportsPrimaryButton: Boolean get() = !isMobile && os != OS.Unknown

    override fun toString(): String {
        return "${this::class.simpleName} ($os)"
    }
}

expect val platform: Platform