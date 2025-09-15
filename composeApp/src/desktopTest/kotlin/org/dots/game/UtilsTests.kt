package org.dots.game

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTests {
    @Test
    fun testSplitByUppercase() {
        assertEquals("Multi Word String", splitByUppercase("MultiWordString"))
        assertEquals("multi Word String", splitByUppercase("multiWordString"))
        assertEquals("XYZoo UVW", splitByUppercase("XYZooUVW"))
    }
}