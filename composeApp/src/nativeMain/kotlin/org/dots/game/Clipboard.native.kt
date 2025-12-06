package org.dots.game

import platform.UIKit.UIPasteboard

actual object Clipboard {
    private val generalPasteboard = UIPasteboard.generalPasteboard()

    actual fun copyTo(text: String) {
        generalPasteboard.string = text
    }

    actual suspend fun getFrom(): String? {
        return generalPasteboard.string
    }
}
