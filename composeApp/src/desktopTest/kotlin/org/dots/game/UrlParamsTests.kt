package org.dots.game

import org.dots.game.ExampleTestData.EXAMPLE_PATH
import org.dots.game.ExampleTestData.exampleSgf
import org.dots.game.ExampleTestData.exampleSgfParams
import org.dots.game.sgf.TextSpan
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class UrlParamsTests {
    @Test
    fun correctGameSettingsUrlParsing() {
        val decodedGameSettings = GameSettings.parseUrlParams(exampleSgfParams, 0) {
            assertFails("The error isn't expected: $it") {}
        }

        assertEquals(EXAMPLE_PATH, decodedGameSettings.path)
        assertEquals(exampleSgf, decodedGameSettings.sgf)
        assertEquals(1, decodedGameSettings.game)
        assertEquals(2, decodedGameSettings.node)
    }

    @Test
    fun emptyGameSettingsUrlParsing() {
        val gameSettings = GameSettings(null, null, null, null)
        assertTrue(gameSettings.toUrlParams().isEmpty())
    }

    @Test
    fun incorrectGameSettingsUrlParsing() {
        fun parseAndCheckDiagnostics(str: String, vararg expectedDiagnostics: Diagnostic) {
            val diagnostics = buildList {
                val _ = GameSettings.parseUrlParams(str, 1) { add(it) }
            }
            assertEquals(expectedDiagnostics.toList(), diagnostics)
        }

        parseAndCheckDiagnostics("^%[)",
            Diagnostic("Parameter `^%[)` doesn't have a value", TextSpan(1, 4))
        )

        parseAndCheckDiagnostics("&=value&path&unknownkey=123&",
            Diagnostic("Empty parameter name", TextSpan(1, 0)),
            Diagnostic("Empty parameter name", TextSpan(2, 0)),
            Diagnostic("Parameter `path` doesn't have a value", TextSpan(9, 4)),
            Diagnostic("Parameter `unknownkey` is unknown", TextSpan(14, 10))
        )

        parseAndCheckDiagnostics("path=%-42&sgf=invalid_base64&game=999999999999999999&node=-1234",
            Diagnostic("Parameter `path` have invalid value `%-42` (URLDecoder: Illegal hex characters in escape (%) pattern - negative value)", TextSpan(6, 4)),
            Diagnostic("Parameter `sgf` have invalid value `invalid_base64` (The pad bits must be zeros)", TextSpan(15, 14)),
            Diagnostic("Parameter `game` have invalid value `999999999999999999` (Invalid number format: '999999999999999999')", TextSpan(35, 18)),
            Diagnostic("Parameter `node` have invalid value `-1234` (Invalid number format: '-1234')", TextSpan(59, 5))
        )
    }
}