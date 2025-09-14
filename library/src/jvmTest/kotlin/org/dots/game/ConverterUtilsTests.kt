package org.dots.game

import kotlin.test.Test
import kotlin.test.assertEquals

class ConverterUtilsTests {
    @Test
    fun lineColumns() {
        val str = "ab\rbc\nde\r\nfg"
        val lineOffsets = str.buildLineOffsets()

        assertEquals(4, lineOffsets.size)

        assertEquals(0, lineOffsets[0])
        assertEquals(3, lineOffsets[1])
        assertEquals(6, lineOffsets[2])
        assertEquals(10, lineOffsets[3])

        assertEquals(LineColumn(1, 1), 0.getLineColumn(lineOffsets))
        assertEquals(LineColumn(1, 2), 1.getLineColumn(lineOffsets))
        assertEquals(LineColumn(1, 3), 2.getLineColumn(lineOffsets))

        assertEquals(LineColumn(2, 1), 3.getLineColumn(lineOffsets))
        assertEquals(LineColumn(2, 2), 4.getLineColumn(lineOffsets))

        assertEquals(LineColumn(4, 1), 10.getLineColumn(lineOffsets))
        assertEquals(LineColumn(4, 2), 11.getLineColumn(lineOffsets))
        assertEquals(LineColumn(4, 3), 12.getLineColumn(lineOffsets))
    }

    @Test
    fun lineColumnOnEmptyString() {
        val lineOffsets = "".buildLineOffsets()
        assertEquals(1, lineOffsets.size)
        assertEquals(0, lineOffsets[0])
        assertEquals(LineColumn(1, 1), 0.getLineColumn(lineOffsets))
    }

    @Test
    fun lineColumnOnNewLine() {
        val lineOffsets = "\n".buildLineOffsets()
        assertEquals(2, lineOffsets.size)
        assertEquals(0, lineOffsets[0])
        assertEquals(1, lineOffsets[1])
        assertEquals(LineColumn(1, 1), 0.getLineColumn(lineOffsets))
        assertEquals(LineColumn(2, 1), 1.getLineColumn(lineOffsets))
    }
}