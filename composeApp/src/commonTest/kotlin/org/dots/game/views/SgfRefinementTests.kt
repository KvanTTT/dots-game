package org.dots.game.views

import org.dots.game.DiagnosticSeverity
import org.dots.game.ExampleTestData.exampleSgf
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.sgf.Sgf
import org.dots.game.sgf.SgfWriter
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SgfRefinementTests {
    @Test
    fun refinedSgfIsValid() {
        val refinement = refineSgfContent(exampleSgf)

        val refinedSgf = assertNotNull(refinement.sgf)
        assertTrue(refinement.diagnostics.none { it.severity >= DiagnosticSeverity.Error }, "${refinement.diagnostics}")

        // The refined content should be loadable back without errors
        val diagnostics = buildList {
            val games = Sgf.parseAndConvert(refinedSgf) { add(it) }
            assertTrue(games.isNotEmpty())
        }
        assertTrue(diagnostics.none { it.severity >= DiagnosticSeverity.Error }, "$diagnostics")
    }

    @Test
    fun gameWithoutMovesIsNotRefinable() {
        val emptyGameSgf = SgfWriter.write(Games.fromRules(Rules.Standard))

        // Empty games are dropped by the refiner, thus there is nothing to save
        assertNull(refineSgfContent(emptyGameSgf).sgf)
    }
}
