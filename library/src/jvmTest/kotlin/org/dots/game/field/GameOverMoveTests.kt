package org.dots.game.field

import org.dots.game.core.EndGameKind
import org.dots.game.core.GameResult
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.getSortedClosurePositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            val moveResult = it.makeMove(Position.GROUND, Player.First)!!
            val base = moveResult.bases!!.single()

            val sortedPositions = base.getSortedClosurePositions(it, considerTerritoryPositions = true)
            assertEquals(2, sortedPositions.outerClosure.size)
            assertTrue(sortedPositions.innerClosures.isEmpty())

            assertTrue(base.closurePositions.isEmpty())
            assertEquals(0, it.player1Score)
            assertEquals(2, it.player2Score)
            assertEquals(GameResult.ScoreWin(2.0, EndGameKind.Grounding, Player.Second), it.gameResult)
            it.unmakeMove()

            it.makeMove(Position.GROUND, Player.Second)!!
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
            val moveResult = it.makeMove(Position.GROUND, Player.First)!!
            assertNull(moveResult.bases)
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.Draw(EndGameKind.Grounding), it.gameResult)
            it.unmakeMove()

            it.makeMove(Position.GROUND, Player.Second)
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
            val moveResult = it.makeMove(Position.GROUND, Player.First)!!
            val base = moveResult.bases!!.single()
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
            val moveResult = it.makeMove(Position.GROUND, Player.First)!!
            assertEquals(2, moveResult.bases!!.size)

            val firstBase = moveResult.bases[0]
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
            val moveResult = it.makeMove(Position.GROUND, Player.First)!!
            assertEquals(4, moveResult.bases!!.size)

            with (it) {
                assertEquals(Player.None, Position(3, 3).getState().getEmptyTerritoryPlayer())
                assertEquals(Player.None, Position(3, 4).getState().getEmptyTerritoryPlayer())
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
            val moveResult = it.makeMove(Position.GROUND, Player.First)!!
            assertNull(moveResult.bases)

            with (it) {
                assertTrue(Position(2, 2).getState().checkWithinEmptyTerritory(Player.First))
            }
        }
    }

    @Test
    fun resign() {
        testFieldWithRollback("""
            . . * . . .
            . . * * . .
            . * + . * .
            . . * * . .
            . . . . . .
        """) {
            val moveResult = it.makeMove(Position.RESIGN, Player.First)!!
            val base = moveResult.bases!!.single()
            // Grounded bases captured anyway in case of resigning
            val sortedPositions = base.getSortedClosurePositions(it, considerTerritoryPositions = true)
            assertEquals(7, sortedPositions.outerClosure.size)
            assertTrue(sortedPositions.innerClosures.isEmpty())
            assertEquals(GameResult.ResignWin(Player.Second), it.gameResult)
            // Resigning doesn't affect the resulting score
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
            it.unmakeMove()

            it.makeMove(Position.RESIGN, Player.Second)!!
            assertEquals(GameResult.ResignWin(Player.First), it.gameResult)
            // Resigning doesn't affect the resulting score
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
            it.unmakeMove()
        }
    }
}