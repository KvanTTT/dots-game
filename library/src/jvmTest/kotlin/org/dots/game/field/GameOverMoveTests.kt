@file:Suppress("RETURN_VALUE_NOT_USED") // TODO: remove after switching to a newer Kotlin version (KT-82363)

package org.dots.game.field

import org.dots.game.core.EndGameKind
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.GameResult
import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.getSortedClosurePositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameOverMoveTests : FieldTests() {
    @Test
    fun groundingSimple() {
        testFieldWithRollback("""
             . . . . .
             . * * + .
             . . . . .
        """) {
            assertIs<GameResult.WinGameResult>(it.makeMove(positionXY = null, Player.First, ExternalFinishReason.Grounding))
            val base = it.lastMove!!.bases.single()

            val sortedPositions = base.getSortedClosurePositions(it, considerTerritoryPositions = true)
            assertEquals(2, sortedPositions.outerClosure.size)
            assertTrue(sortedPositions.innerClosures.isEmpty())

            assertEquals(base.closurePositions.size, 0)
            assertEquals(0, it.player1Score)
            assertEquals(2, it.player2Score)
            assertEquals(GameResult.ScoreWin(2.0, EndGameKind.Grounding, Player.Second, Player.First), it.gameResult)
            assertIs<GameResult.WinGameResult>(it.unmakeMove())

            assertIs<GameResult.WinGameResult>(it.makeMove(positionXY = null, Player.Second, ExternalFinishReason.Grounding))
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.ScoreWin(1.0, EndGameKind.Grounding, Player.First, Player.Second), it.gameResult)
            assertIs<GameResult.WinGameResult>(it.unmakeMove())
        }
    }

    @Test
    fun groundingDraw() {
        testFieldWithRollback("""
             * + .
        """) {
            assertIs<LegalMove>(it.makeMove(positionXY = null, player = Player.First, externalFinishReason = ExternalFinishReason.Grounding))
            assertTrue(it.lastMove!!.bases.isEmpty())
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.Draw(EndGameKind.Grounding, Player.First), it.gameResult)
            assertIs<LegalMove>(it.unmakeMove())

            assertIs<LegalMove>(it.makeMove(positionXY = null, player = Player.Second, ExternalFinishReason.Grounding))
            assertEquals(0, it.player1Score)
            assertEquals(0, it.player2Score)
            assertEquals(GameResult.Draw(EndGameKind.Grounding, Player.Second), it.gameResult)
            assertIs<LegalMove>(it.unmakeMove())
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
            assertIs<GameResult.WinGameResult>(it.makeMove(positionXY = null, Player.First, ExternalFinishReason.Grounding))
            val base = it.lastMove!!.bases.single()
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
            assertIs<GameResult.WinGameResult>(it.makeMove(positionXY = null, Player.First, ExternalFinishReason.Grounding))
            val lastMove = it.lastMove!!
            assertEquals(2, lastMove.bases.size)

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
            assertIs<GameResult.WinGameResult>(it.makeMove(positionXY = null, Player.First, ExternalFinishReason.Grounding))
            assertEquals(4, it.lastMove!!.bases.size)

            with (it) {
                assertEquals(Player.None, Position(3, 3, it.realWidth).getState().getEmptyTerritoryPlayer())
                assertEquals(Player.None, Position(3, 4, it.realWidth).getState().getEmptyTerritoryPlayer())
            }
        }
    }

    @Test
    fun groundingDoesntInvalidateEmptyTerritoryForStrongConnection() {
        testFieldWithRollback(
            """
            * * *
            * . *
            * * *
        """
        ) {
            assertIs<GameResult.Draw>(it.makeMove(positionXY = null, Player.First, ExternalFinishReason.Grounding))
            assertTrue(it.lastMove!!.bases.isEmpty())

            with (it) {
                assertTrue(Position(2, 2, it.realWidth).getState().isWithinEmptyTerritory(Player.First))
            }
        }
    }
}