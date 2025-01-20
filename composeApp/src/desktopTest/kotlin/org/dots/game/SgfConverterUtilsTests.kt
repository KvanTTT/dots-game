package org.dots.game

import org.dots.game.sgf.convertSimpleText
import org.dots.game.sgf.convertText
import org.junit.Test
import kotlin.test.assertEquals

class SgfConverterUtilsTests {
    @Test
    fun simpleText() {
        assertEquals("simple", "simple".convertSimpleText())
        assertEquals("abc:def]", "abc\\:def\\]\\".convertSimpleText())
        assertEquals("abc def", "abc\rdef".convertSimpleText())
        assertEquals("abc def", "abc\ndef".convertSimpleText())
        assertEquals("abc def", "abc\r\ndef".convertSimpleText())
        assertEquals("abc def", "abc\n\rdef".convertSimpleText())
        assertEquals("abc ", "abc\r".convertSimpleText())
        assertEquals("abc ", "abc\n".convertSimpleText())
        assertEquals("abc ", "abc\r\n".convertSimpleText())
        assertEquals("abc ", "abc\n\r".convertSimpleText())
        assertEquals("nospace", "no\\\rspace".convertSimpleText())
        assertEquals("nospace", "no\\\nspace".convertSimpleText())
        assertEquals("nospace", "no\\\r\nspace".convertSimpleText())
        assertEquals("nospace", "no\\\n\rspace".convertSimpleText())
    }

    @Test
    fun text() {
        assertEquals("simple", "simple".convertText())
        assertEquals("abc:def]", "abc\\:def\\]\\".convertText())
        assertEquals("abc\rdef", "abc\rdef".convertText())
        assertEquals("abc\ndef", "abc\ndef".convertText())
        assertEquals("abc\r\ndef", "abc\r\ndef".convertText())
        assertEquals("abc\n\rdef", "abc\n\rdef".convertText())
        assertEquals("abc\r", "abc\r".convertText())
        assertEquals("abc\n", "abc\n".convertText())
        assertEquals("abc\r\n", "abc\r\n".convertText())
        assertEquals("abc\n\r", "abc\n\r".convertText())
        assertEquals("nospace", "no\\\rspace".convertText())
        assertEquals("nospace", "no\\\nspace".convertText())
        assertEquals("nospace", "no\\\r\nspace".convertText())
        assertEquals("nospace", "no\\\n\rspace".convertText())
    }
}