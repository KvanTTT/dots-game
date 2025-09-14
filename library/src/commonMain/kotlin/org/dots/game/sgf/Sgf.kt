package org.dots.game.sgf

import org.dots.game.Diagnostic
import org.dots.game.core.Games

object Sgf {
    fun parseAndConvert(sgf: String, onlySingleGameSupported: Boolean = false, diagnosticReporter: (Diagnostic) -> Unit): Games {
        val sgfParseTree = SgfParser.parse(sgf) { parseDiagnostic ->
            diagnosticReporter(parseDiagnostic)
        }
        return SgfConverter.convert(sgfParseTree, warnOnMultipleGames = onlySingleGameSupported) { convertDiagnostic ->
            diagnosticReporter(convertDiagnostic)
        }
    }
}