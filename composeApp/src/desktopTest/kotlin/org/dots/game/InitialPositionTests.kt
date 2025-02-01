package org.dots.game

import org.dots.game.core.InitialPositionType
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.generateDefaultInitialPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InitialPositionTests {
    @Test
    fun crossOnMinimalField() {
        val initialPositions = InitialPositionType.Cross.generateDefaultInitialPositions(2, 2)!!
        initialPositions.checkCross(1, 1)
    }

    @Test
    fun crossOnEvenField() {
        val initialPositions = InitialPositionType.Cross.generateDefaultInitialPositions(8, 8)!!
        initialPositions.checkCross(4, 4)
    }

    @Test
    fun crossOnOddField() {
        val initialPositions = InitialPositionType.Cross.generateDefaultInitialPositions(9, 9)!!
        initialPositions.checkCross(4, 4)
    }

    @Test
    fun crossDoesntFitField() {
        assertNull(InitialPositionType.Cross.generateDefaultInitialPositions(1, 1))
    }

    private fun List<MoveInfo>.checkCross(x: Int, y: Int) {
        assertEquals(4, this.size)
        assertEquals(MoveInfo(Position(x, y), Player.First), this[0])
        assertEquals(MoveInfo(Position(x + 1, x + 1), Player.First), this[1])
        assertEquals(MoveInfo(Position(x + 1, y), Player.Second), this[2])
        assertEquals(MoveInfo(Position(x, y + 1), Player.Second), this[3])
    }
}