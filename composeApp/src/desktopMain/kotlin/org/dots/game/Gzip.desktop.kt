package org.dots.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

actual object Gzip {
    actual fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return emptyByteArray
        ByteArrayOutputStream().use { outStream ->
            GZIPOutputStream(outStream).use { it.write(input) }
            return outStream.toByteArray()
        }
    }

    actual fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return emptyByteArray
        ByteArrayInputStream(input).use { inputStream ->
            GZIPInputStream(inputStream, input.size).use { gzipInputStream ->
                return gzipInputStream.readAllBytes()
            }
        }
    }
}