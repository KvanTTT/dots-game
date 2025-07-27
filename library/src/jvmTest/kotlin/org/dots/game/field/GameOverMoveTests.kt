package org.dots.game.field

import org.dots.game.core.EndGameKind
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.GameResult
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.getSortedClosurePositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameOverMoveTests : FieldTests() {
    @Test
    fun groundingSimple() {
        testFieldWithRollback("""
             . . . . .
             . * * + .
             . . . . .
        """) {
            it.finishGame(ExternalFinishReason.Grounding, Player.First)!!
            val base = it.lastMove!!.bases!!.single()

            val sortedPositions = base.getSortedClosurePositions(it, considerTerritoryPositions = true)
            assertEquals(2, sortedPositions.outerClosure.size)
            assertTrue(sortedPositions.innerClosures.isEmpty())

            assertTrue(base.closurePositions.isEmpty())
            assertEquals(0, it.player1Score)
            assertEquals(2, it.player2Score)
            assertEquals(GameResult.ScoreWin(2.0, EndGameKind.Grounding, Player.Second), it.gameResult)
            it.unmakeMove()

            it.finishGame(ExternalFinishReason.Grounding, Player.Second)!!
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.ScoreWin(1.0, EndGameKind.Grounding, Player.First), it.gameResult)
            it.unmakeMove()
        }
    }

    @Test
    fun groundingDraw() {
        testFieldWithRollback("""
             * + .
        """) {
            it.finishGame(ExternalFinishReason.Grounding, Player.First)!!
            assertNull(it.lastMove!!.bases)
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.Draw(EndGameKind.Grounding), it.gameResult)
            it.unmakeMove()

            it.finishGame(ExternalFinishReason.Grounding, Player.Second)
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.Draw(EndGameKind.Grounding), it.gameResult)
            it.unmakeMove()
        }
    }

    @Test
    fun groundingCaptureBase() {
        testFieldWithRollback("""
            . . . . . .
            . . * * . .
            . * + . * .
            . . * * . .
            . . . . . .
        """) {
            it.finishGame(ExternalFinishReason.Grounding, Player.First)!!
            val base = it.lastMove!!.bases!!.single()
            val sortedPositions = base.getSortedClosurePositions(it, considerTerritoryPositions = true)
            assertEquals(6, sortedPositions.outerClosure.size)
            assertTrue(sortedPositions.innerClosures.isEmpty())

            assertEquals(0, it.player1Score)
            assertEquals(6, it.player2Score)
        }
    }

    @Test
    fun groundingMultipleGroups() {
        testFieldWithRollback(
            """
            . . . .
            . * + .
            . + * .
            . . . .
        """
        ) {
            it.finishGame(ExternalFinishReason.Grounding, Player.First)!!
            val lastMove = it.lastMove!!
            assertEquals(2, lastMove.bases!!.size)

            val firstBase = lastMove.bases[0]
            val sortedPositions = firstBase.getSortedClosurePositions(it, considerTerritoryPositions = true)
            assertEquals(1, sortedPositions.outerClosure.size)
            assertTrue(sortedPositions.innerClosures.isEmpty())

            assertEquals(0, it.player1Score)
            assertEquals(2, it.player2Score)
        }
    }

    @Test
    fun groundingInvalidateEmptyTerritory() {
        testFieldWithRollback(
            """
            . . . . . .
            . . * * . .
            . * . . * .
            . . * * . .
            . . . . . .
        """
        ) {
            it.finishGame(ExternalFinishReason.Grounding, Player.First)!!
            assertEquals(4, it.lastMove!!.bases!!.size)

            with (it) {
                assertEquals(Player.None, Position(3, 3, it.realWidth).getState().getEmptyTerritoryPlayer())
                assertEquals(Player.None, Position(3, 4, it.realWidth).getState().getEmptyTerritoryPlayer())
            }
        }
    }

    @Test
    fun groundingDontInvalidateEmptyTerritoryForStrongConnection() {
        testFieldWithRollback(
            """
            * * *
            * . *
            * * *
        """
        ) {
            it.finishGame(ExternalFinishReason.Grounding, Player.First)!!
            assertNull(it.lastMove!!.bases)

            with (it) {
                assertTrue(Position(2, 2, it.realWidth).getState().isWithinEmptyTerritory(Player.First))
            }
        }
    }
}