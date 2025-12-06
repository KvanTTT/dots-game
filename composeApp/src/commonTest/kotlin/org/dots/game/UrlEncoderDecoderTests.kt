package org.dots.game

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlEncoderDecoderTests {
    val expectedEncoded = "%26example%3D.sgf"

    @Test
    fun test() {
        val encoded = UrlEncoderDecoder.encode(ExampleTestData.EXAMPLE_PATH)
        println("Encoded: $encoded")

        assertEquals(expectedEncoded, encoded)

        val decoded = UrlEncoderDecoder.decode(encoded)
        assertEquals(ExampleTestData.EXAMPLE_PATH, decoded)
    }
}
