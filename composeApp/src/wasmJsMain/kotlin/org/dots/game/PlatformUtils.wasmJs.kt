package org.dots.game

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withRunningRecomposer
import androidx.compose.ui.Modifier
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Response
import org.w3c.files.File

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

actual fun readFileText(filePath: String): String = error("File loading is not supported")

actual fun fileExists(filePath: String): Boolean = false

actual suspend fun downloadFileText(fileUrl: String): String {
    error("File downloading by url is not supported") // TODO: return window.fetch(fileUrl).await<Response>().text().await()
}
