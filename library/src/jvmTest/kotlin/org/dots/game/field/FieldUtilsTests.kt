package org.dots.game.field

import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.InitialPositionType.Cross
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.dots.game.core.generateDefaultInitialPositions
import org.dots.game.core.getPositionsOfConnection
import org.dots.game.core.unmakeAllMovesAndCheck
import org.dots.game.dump.FieldParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FieldUtilsTests : FieldTests() {
    val sampleField = FieldParser.parseAndConvertWithNoInitialMoves("""
            . . . . . . .
            . . * * * * *
            . * + . * . .
            . . * * * * .
            . * . . * . .
            . * . . . . .
            . . . * . * *
            . * * * . . * 
        """.trimIndent())

    @Test
    fun positionsOfStrongConnectionEmpty() {
        assertTrue(sampleField.getPositionsOfConnection(2, 2).isEmpty())
    }

    @Test
    fun positionsOfStrongConnectionInsideTerritory() {
        assertTrue(sampleField.getPositionsOfConnection(3, 3).isEmpty())
        assertTrue(sampleField.getPositionsOfConnection(4, 3).isEmpty())
    }

    @Test
    fun positionsOfStrongConnectionIgnoreTerritory() {
        assertEquals(
            listOf(
                PositionXY(6, 2),
                PositionXY(5, 3),
                PositionXY(4, 2),
                PositionXY(5, 2),
            ),
            sampleField.getPositionsOfConnection(5, 2)
        )
    }

    @Test
    fun positionsOfStrongConnection() {
        assertEquals(
            listOf(
                PositionXY(6, 4),
                PositionXY(5, 5),
                PositionXY(4, 4),
                PositionXY(5, 4),
            ),
            sampleField.getPositionsOfConnection(5, 5)
        )
    }

    @Test
    fun positionsOfStrongConnectionExcludeCenterDot() {
        assertEquals(
            listOf(
                PositionXY(6, 4),
                PositionXY(5, 5),
                PositionXY(4, 4),
                PositionXY(5, 3),
            ),
            sampleField.getPositionsOfConnection(5, 4)
        )
    }

    @Test
    fun positionsOfStrongConnectionIncludeCenterDot() {
        assertEquals(
            listOf(
                PositionXY(7, 2),
                PositionXY(6, 2),
                PositionXY(5, 3),
                PositionXY(5, 2),
                PositionXY(6, 2),
            ),
            sampleField.getPositionsOfConnection(6, 2)
        )
    }

    @Test
    fun positionsOfStrongConnectionIncludeCenterDot2() {
        assertEquals(
            listOf(
                PositionXY(4, 7),
                PositionXY(4, 8),
                PositionXY(3, 8),
                PositionXY(2, 8),
                PositionXY(3, 8),
            ),
            sampleField.getPositionsOfConnection(3, 8)
        )
    }

    @Test
    fun positionsOfStrongConnectionIncludeCenterDot3() {
        assertEquals(
            listOf(
                PositionXY(7, 8),
                PositionXY(6, 7),
                PositionXY(7, 7)
            ),
            sampleField.getPositionsOfConnection(7, 7)
        )
    }

    @Test
    fun positionsOfStrongConnectionIgnoreWeakGroup() {
        assertEquals(
            listOf(PositionXY(2, 6), PositionXY(2, 5)),
            sampleField.getPositionsOfConnection(2, 5)
        )
    }

    private fun Field.getPositionsOfConnection(x: Int, y: Int): List<PositionXY> {
        return getPositionsOfConnection(Position(x, y, realWidth)).map { it.toXY(realWidth) }
    }

    @Test
    fun clone() {
        val width = 4
        val height = 4
        val rules = Rules(width, height, initialMoves = Cross.generateDefaultInitialPositions(width, height)!!)
        val field = Field.create(rules)

        field.makeMove(2, 1, Player.First)
        field.makeMove(1, 2, Player.First)
        field.finishGame(ExternalFinishReason.Resign, Player.Second)

        val newField = field.clone()
        assertEquals(field.width, newField.width)
        assertEquals(field.height, newField.height)
        assertEquals(field.realWidth, newField.realWidth)
        assertEquals(field.realHeight, newField.realHeight)
        assertEquals(0, field.numberOfLegalMoves)
        assertEquals(field.numberOfLegalMoves, newField.numberOfLegalMoves)
        assertEquals(4, field.initialMovesCount)
        assertEquals(field.initialMovesCount, newField.initialMovesCount)
        assertEquals(7, field.moveSequence.size)
        assertEquals(field.moveSequence.size, newField.moveSequence.size)
        assertEquals(1, field.player1Score)
        assertEquals(field.player1Score, newField.player1Score)
        assertEquals(0, field.player2Score)
        assertEquals(field.player2Score, newField.player2Score)
        assertEquals(field.gameResult, GameResult.ResignWin(Player.First))
        assertEquals(field.gameResult, newField.gameResult)

        field.unmakeAllMovesAndCheck { fail(it) }
        newField.unmakeAllMovesAndCheck { fail(it) }
    }
}