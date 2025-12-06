package org.dots.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual object Clipboard {
    private val systemClipboard = Toolkit.getDefaultToolkit().systemClipboard

    actual fun copyTo(text: String) {
        systemClipboard.setContents(StringSelection(text), null)
    }

    actual suspend fun getFrom(): String? {
        val contents = systemClipboard.getContents(null) ?: return null
        return  withContext(Dispatchers.IO) {
            contents
                .takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
                ?.getTransferData(DataFlavor.stringFlavor) as? String
        }
    }
}
