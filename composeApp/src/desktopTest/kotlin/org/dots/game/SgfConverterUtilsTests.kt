package org.dots.game

import org.dots.game.core.AppInfo
import org.dots.game.sgf.LineColumn
import org.dots.game.sgf.buildLineOffsets
import org.dots.game.sgf.convertAppInfo
import org.dots.game.sgf.convertSimpleText
import org.dots.game.sgf.convertText
import org.dots.game.sgf.getLineColumn
import kotlin.test.Test
import kotlin.test.assertEquals

class SgfConverterUtilsTests {
    @Test
    fun convertSimpleText() {
        assertEquals("simple", "simple".convertSimpleText())
        assertEquals("abc:def]", "abc\\:def\\]\\".convertSimpleText())
        assertEquals("abc def", "abc\rdef".convertSimpleText())
        assertEquals("abc def", "abc\ndef".convertSimpleText())
        assertEquals("abc def", "abc\r\ndef".convertSimpleText())
        assertEquals("abc ", "abc\r".convertSimpleText())
        assertEquals("abc ", "abc\n".convertSimpleText())
        assertEquals("abc ", "abc\r\n".convertSimpleText())
        assertEquals("nospace", "no\\\rspace".convertSimpleText())
        assertEquals("nospace", "no\\\nspace".convertSimpleText())
        assertEquals("nospace", "no\\\r\nspace".convertSimpleText())
    }

    @Test
    fun convertText() {
        assertEquals("simple", "simple".convertText())
        assertEquals("abc:def]", "abc\\:def\\]\\".convertText())
        assertEquals("abc\rdef", "abc\rdef".convertText())
        assertEquals("abc\ndef", "abc\ndef".convertText())
        assertEquals("abc\r\ndef", "abc\r\ndef".convertText())
        assertEquals("abc\r", "abc\r".convertText())
        assertEquals("abc\n", "abc\n".convertText())
        assertEquals("abc\r\n", "abc\r\n".convertText())
        assertEquals("nospace", "no\\\rspace".convertText())
        assertEquals("nospace", "no\\\nspace".convertText())
        assertEquals("nospace", "no\\\r\nspace".convertText())
    }

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

    @Test
    fun convertAppInfo() {
        assertEquals(AppInfo("AppName", null), "AppName".convertAppInfo())
        assertEquals(AppInfo("AppName", "2.3"), "AppName:2.3".convertAppInfo())
        assertEquals(AppInfo("App:Name", "5"), "App\\:Name:5".convertAppInfo())
        assertEquals(AppInfo("AppName", "5:10"), "AppName:5:10".convertAppInfo())
    }
}