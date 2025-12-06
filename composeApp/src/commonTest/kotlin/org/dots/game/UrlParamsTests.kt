package org.dots.game

import org.dots.game.ExampleTestData.EXAMPLE_PATH
import org.dots.game.ExampleTestData.exampleSgf
import org.dots.game.ExampleTestData.exampleSgfParams
import org.dots.game.sgf.TextSpan
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.todo

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
            assertContentEquals(expectedDiagnostics.toList(), diagnostics)
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

        val urlDecodingErrorSuffix = when {
            platform.isJvmBased -> {
                "URLDecoder: Illegal hex characters in escape (%) pattern - negative value"
            }
            platform is Platform.Web -> {
                "URI malformed"
            }
            else -> {
                todo { error("$platform is not implemented") }
            }
        }

        parseAndCheckDiagnostics("path=%-42&sgf=invalid_base64&game=999999999999999999&node=-1234",
            Diagnostic("Parameter `path` has invalid value `%-42` ($urlDecodingErrorSuffix)", TextSpan(6, 4)),
            Diagnostic("Parameter `sgf` has invalid value `invalid_base64` (The pad bits must be zeros)", TextSpan(15, 14)),
            Diagnostic("Parameter `game` has invalid value `999999999999999999` (Invalid number format)", TextSpan(35, 18)),
            Diagnostic("Parameter `node` has invalid value `-1234` (Expected a non-negative integer)", TextSpan(59, 5))
        )
    }
}