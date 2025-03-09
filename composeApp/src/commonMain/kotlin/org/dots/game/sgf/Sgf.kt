package org.dots.game.sgf

import org.dots.game.core.Game

object Sgf {
    fun parseAndConvert(sgf: String, onlySingleGameSupported: Boolean = false, diagnosticReporter: (SgfDiagnostic) -> Unit): List<Game> {
        val sgfParseTree = SgfParser.parse(sgf) { parseDiagnostic ->
            diagnosticReporter(parseDiagnostic)
        }
        return SgfConverter.convert(sgfParseTree, warnOnMultipleGames = onlySingleGameSupported) { convertDiagnostic ->
            diagnosticReporter(convertDiagnostic)
        }
    }
}