package org.dots.game

import kotlinx.browser.window
import kotlinx.coroutines.await

@OptIn(ExperimentalWasmJsInterop::class)
actual object Clipboard {
    private val clipboard = window.navigator.clipboard

    actual fun copyTo(text: String) {
        clipboard.writeText(text)
    }

    actual suspend fun getFrom(): String? {
        return clipboard.readText().await<String?>().toString()
    }
}
