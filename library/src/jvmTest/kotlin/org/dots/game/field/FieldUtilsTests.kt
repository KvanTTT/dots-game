package org.dots.game.field

import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.InitialPositionType.Cross
import org.dots.game.core.Player
import org.dots.game.core.Position
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
        assertTrue(sampleField.getPositionsOfConnection(Position(2, 2)).isEmpty())
    }

    @Test
    fun positionsOfStrongConnectionInsideTerritory() {
        assertTrue(sampleField.getPositionsOfConnection(Position(3, 3)).isEmpty())
        assertTrue(sampleField.getPositionsOfConnection(Position(4, 3)).isEmpty())
    }

    @Test
    fun positionsOfStrongConnectionIgnoreTerritory() {
        assertEquals(
            listOf(
                Position(6, 2),
                Position(5, 3),
                Position(4, 2),
                Position(5, 2),
            ),
            sampleField.getPositionsOfConnection(Position(5, 2))
        )
    }

    @Test
    fun positionsOfStrongConnection() {
        assertEquals(
            listOf(
                Position(6, 4),
                Position(5, 5),
                Position(4, 4),
                Position(5, 4),
            ),
            sampleField.getPositionsOfConnection(Position(5, 5))
        )
    }

    @Test
    fun positionsOfStrongConnectionExcludeCenterDot() {
        assertEquals(
            listOf(
                Position(6, 4),
                Position(5, 5),
                Position(4, 4),
                Position(5, 3),
            ),
            sampleField.getPositionsOfConnection(Position(5, 4))
        )
    }

    @Test
    fun positionsOfStrongConnectionIncludeCenterDot() {
        assertEquals(
            listOf(
                Position(7, 2),
                Position(6, 2),
                Position(5, 3),
                Position(5, 2),
                Position(6, 2),
            ),
            sampleField.getPositionsOfConnection(Position(6, 2))
        )
    }

    @Test
    fun positionsOfStrongConnectionIncludeCenterDot2() {
        assertEquals(
            listOf(
                Position(4, 7),
                Position(4, 8),
                Position(3, 8),
                Position(2, 8),
                Position(3, 8),
            ),
            sampleField.getPositionsOfConnection(Position(3, 8))
        )
    }

    @Test
    fun positionsOfStrongConnectionIncludeCenterDot3() {
        assertEquals(
            listOf(Position(7, 8), Position(6, 7), Position(7, 7)),
            sampleField.getPositionsOfConnection(Position(7, 7))
        )
    }

    @Test
    fun positionsOfStrongConnectionIgnoreWeakGroup() {
        assertEquals(
            listOf(Position(2, 6), Position(2, 5)),
            sampleField.getPositionsOfConnection(Position(2, 5))
        )
    }

    @Test
    fun clone() {
        val width = 4
        val height = 4
        val rules = Rules(width, height, initialMoves = Cross.generateDefaultInitialPositions(width, height)!!)
        val field = Field.create(rules)

        field.makeMove(Position(2, 1), Player.First)
        field.makeMove(Position(1, 2), Player.First)
        field.makeMove(Position.RESIGN, Player.Second)

        val newField = field.clone()
        assertEquals(field.width, newField.width)
        assertEquals(field.height, newField.height)
        assertEquals(field.realWidth, newField.realWidth)
        assertEquals(field.realHeight, newField.realHeight)
        assertEquals(10, field.numberOfLegalMoves)
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