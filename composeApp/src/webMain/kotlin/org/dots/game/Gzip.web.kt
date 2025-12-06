package org.dots.game

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("pako")
external object Pako {
    fun gzip(input: Uint8Array): Uint8Array
    fun ungzip(input: Uint8Array): Uint8Array
}

actual object Gzip {
    const val OS_ID_INDEX: Int = 9
    const val UNKNOWN_OS_ID: Byte = -1

    actual fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return emptyByteArray
        return Pako.gzip(input.toUint8Array()).toByteArray(compression = true)
    }

    actual fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return emptyByteArray
        return Pako.ungzip(input.toUint8Array()).toByteArray(compression = false)
    }

    private fun ByteArray.toUint8Array(): Uint8Array {
        val result = Uint8Array(size)
        for (i in indices) {
            result[i] = this[i]
        }
        return result
    }

    private fun Uint8Array.toByteArray(compression: Boolean): ByteArray {
        val result = ByteArray(length)
        for (i in 0 until length) {
            // Use UNKNOWN_OS_ID to align the output with JVM/Android output
            result[i] = if (compression && i == OS_ID_INDEX) UNKNOWN_OS_ID else this[i]
        }
        return result
    }
}
