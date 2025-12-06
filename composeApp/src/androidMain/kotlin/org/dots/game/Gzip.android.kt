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
            val bufferSize = maxOf(input.size, 1024)
            GZIPInputStream(inputStream, bufferSize).use { gzipInputStream ->
                val buffer = ByteArray(bufferSize)
                val out = ByteArrayOutputStream()
                while (true) {
                    val read = gzipInputStream.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            }
        }
    }
}