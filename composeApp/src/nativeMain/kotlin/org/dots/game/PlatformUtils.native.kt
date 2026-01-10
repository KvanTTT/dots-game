package org.dots.game

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringByRemovingPercentEncoding
import platform.Foundation.NSTemporaryDirectory

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

actual suspend fun downloadFileText(fileUrl: String): String = error("File downloading by url is not supported")

@Composable
actual fun SaveFileDialog(
    title: String?,
    selectedFile: String?,
    extension: String,
    onFileSelected: (String?) -> Unit,
    content: String
) {
}

@Composable
actual fun OpenFileDialog(
    title: String?,
    selectedFile: String?,
    allowedExtensions: List<String>,
    onFileSelected: (String?) -> Unit
) {
    // File dialog is not implemented for native platforms (iOS)
    onFileSelected(null)
}

actual val platform: Platform = Native

@Composable
actual fun Tooltip(text: String?, content: @Composable () -> Unit) {
    content()
}

actual object UrlEncoderDecoder {
    actual fun encode(value: String): String {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return (value as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
            NSCharacterSet.URLQueryAllowedCharacterSet
        )!!
    }

    actual fun decode(value: String): String {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return (value as NSString).stringByRemovingPercentEncoding()!!
    }
}

actual val tempDirectory: String = NSTemporaryDirectory()