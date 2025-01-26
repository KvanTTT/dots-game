package org.dots.game

import org.dots.game.core.InitialPositionType
import org.dots.game.core.Position
import org.dots.game.core.generateDefaultInitialPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InitialPositionTests {
    @Test
    fun crossOnMinimalField() {
        val initialPositions = InitialPositionType.Cross.generateDefaultInitialPosition(2, 2)!!
        initialPositions.checkCross(1, 1)
    }

    @Test
    fun crossOnEvenField() {
        val initialPositions = InitialPositionType.Cross.generateDefaultInitialPosition(8, 8)!!
        initialPositions.checkCross(4, 4)
    }

    @Test
    fun crossOnOddField() {
        val initialPositions = InitialPositionType.Cross.generateDefaultInitialPosition(9, 9)!!
        initialPositions.checkCross(4, 4)
    }

    @Test
    fun crossDoesntFitField() {
        assertNull(InitialPositionType.Cross.generateDefaultInitialPosition(1, 1))
    }

    private fun Pair<List<Position>, List<Position>>.checkCross(x: Int, y: Int) {
        val (player1InitialPositions, player2InitialPositions) = this

        assertEquals(2, player1InitialPositions.size)
        assertEquals(Position(x, y), player1InitialPositions[0])
        assertEquals(Position(x + 1, x + 1), player1InitialPositions[1])

        assertEquals(2, player2InitialPositions.size)
        assertEquals(Position(x + 1, y), player2InitialPositions[0])
        assertEquals(Position(x, y + 1), player2InitialPositions[1])
    }
}