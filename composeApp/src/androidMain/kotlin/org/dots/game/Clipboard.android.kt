package org.dots.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

actual object Clipboard {
    private val clipboardManager = AndroidContextHolder.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    actual fun copyTo(text: String) {
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    actual suspend fun getFrom(): String? {
        val clip = clipboardManager?.primaryClip ?: return null
        return clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(AndroidContextHolder.appContext)?.toString()
    }
}
