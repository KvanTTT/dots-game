package org.dots.game

import org.dots.game.core.AppInfo
import org.dots.game.core.GameInfo
import org.dots.game.sgf.DiagnosticSeverity
import org.dots.game.sgf.LineColumn
import org.dots.game.sgf.SgfConverter
import org.dots.game.sgf.SgfDiagnostic
import org.dots.game.sgf.SgfParser
import kotlin.test.Test
import kotlin.test.assertEquals

class SgfConverterTests {
    @Test
    fun gameInfo() {
        val sgf = SgfParser.parse("(;GM[40]FF[4]CA[UTF-8]SZ[17:21]RU[russian]GN[Test Game]PB[Player1]BR[256]BT[Player1's Team]PW[Player2]WR[512]WT[Player2's Team]KM[0.5]DT[2025-01-19]GC[A game for SGF parser testing]PC[Amsterdam, Netherlands]EV[Test event]ON[Empty]AN[Ivan Kochurkin]CP[@]SO[https://zagram.org/eidokropki/index.html]TL[300]AP[https\\://zagram.org/eidokropki/index.html:1])")
        val gameInfo = SgfConverter.convert(sgf) {
            error(it)
        }.single()
        with (gameInfo) {
            assertEquals(17, rules.width)
            assertEquals(21, rules.height)
            assertEquals("Test Game", gameName)
            assertEquals("Player1", player1Name)
            assertEquals(256.0, player1Rating)
            assertEquals("Player1's Team", player1Team)
            assertEquals("Player2", player2Name)
            assertEquals(512.0, player2Rating)
            assertEquals("Player2's Team", player2Team)
            assertEquals(0.5, komi)
            assertEquals("2025-01-19", date)
            assertEquals("A game for SGF parser testing", description)
            assertEquals("Amsterdam, Netherlands", place)
            assertEquals("Test event", event)
            assertEquals("Empty", opening)
            assertEquals("Ivan Kochurkin", annotator)
            assertEquals("@", copyright)
            assertEquals("https://zagram.org/eidokropki/index.html", source)
            assertEquals(300.0, timeLimit)
            assertEquals(AppInfo("https://zagram.org/eidokropki/index.html", "1"), appInfo)
        }
    }

    @Test
    fun criticalErrorsWithValidTypes() {
        val expectedDiagnostics = listOf(
            SgfDiagnostic("Property GM (GameMode) has unsupported value `1` (Go). The only `40` (Kropki) is supported.", LineColumn(1, 6), DiagnosticSeverity.Critical),
            SgfDiagnostic("Property FF (FileFormat) has unsupported value `3`. The only `4` is supported.", LineColumn(1, 11), DiagnosticSeverity.Critical),
            SgfDiagnostic("Property SZ (Size) has invalid width: `1234`. Expected: 0..254.", LineColumn(1, 16), DiagnosticSeverity.Critical),
            SgfDiagnostic("Property SZ (Size) has invalid height: `5678`. Expected: 0..254.", LineColumn(1, 21), DiagnosticSeverity.Critical),
        )
        checkDiagnostics("(;GM[1]FF[3]SZ[1234:5678])", expectedDiagnostics)
    }

    @Test
    fun notCriticalErrors() {
        // TODO
    }

    @Test
    fun multipleGames() {
        // TODO
    }

    @Test
    fun duplicatedProperties() {
        // TODO
    }

    private fun checkDiagnostics(input: String, expectedDiagnostics: List<SgfDiagnostic>): List<GameInfo> {
        val sgf = SgfParser.parse(input)
        val actualDiagnostics = mutableListOf<SgfDiagnostic>()
        val result = SgfConverter.convert(sgf) {
            actualDiagnostics.add(it)
        }
        assertEquals(expectedDiagnostics, actualDiagnostics)
        return result
    }
}