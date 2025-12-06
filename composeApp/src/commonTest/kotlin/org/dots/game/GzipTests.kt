package org.dots.game

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GzipTests {
    val testString = "A".repeat(1024)
    val compressedTestString = byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1, 115, 116, 28, 5, -93, 96, 20, -116, 84, 0, 0, 26, -5, 55, -73, 0, 4, 0, 0)

    @Test
    fun test() {
        val original = testString.encodeToByteArray()

        val compressed = Gzip.compress(original)
        println("Compression ratio: ${compressed.size} / ${original.size} = ${compressed.size.toDouble() / original.size}")
        assertContentEquals(compressedTestString, compressed)

        val decompressed = Gzip.decompress(compressed)
        assertEquals(original.decodeToString(), decompressed.decodeToString())
    }
}