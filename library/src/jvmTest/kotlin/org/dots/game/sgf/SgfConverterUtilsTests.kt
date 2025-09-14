package org.dots.game.sgf

import org.dots.game.core.AppInfo
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
    fun convertAppInfo() {
        assertEquals(AppInfo("AppName", null), "AppName".convertAppInfo())
        assertEquals(AppInfo("AppName", "2.3"), "AppName:2.3".convertAppInfo())
        assertEquals(AppInfo("App:Name", "5"), "App\\:Name:5".convertAppInfo())
        assertEquals(AppInfo("AppName", "5:10"), "AppName:5:10".convertAppInfo())
    }
}