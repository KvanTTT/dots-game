package org.dots.game

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.russhwolf.settings.Settings

expect var appSettings: Settings?

@Composable
expect fun HorizontalScrollbar(scrollState: ScrollState, modifier: Modifier)

@Composable
expect fun VerticalScrollbar(scrollState: ScrollState, modifier: Modifier)

expect fun readFileText(filePath: String): String

expect fun writeFileText(filePath: String, content: String)

expect fun fileExists(filePath: String): Boolean

expect fun getAppDataDir(): String

expect fun getTempSgfPath(): String

expect suspend fun downloadFileText(fileUrl: String): String

@Composable
expect fun openFileDialog(
    title: String = "Open File",
    allowedExtensions: List<String> = emptyList(),
    onFileSelected: (String?) -> Unit
)