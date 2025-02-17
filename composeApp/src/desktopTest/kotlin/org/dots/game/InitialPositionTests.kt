package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.InitialPosition
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.createPlacedState
import kotlin.test.Test
import kotlin.test.assertFails

class InitialPositionTests {
    @Test
    fun crossOnMinimalField() {
        with (Field(Rules(2, 2, initialPosition = InitialPosition.Cross))) {
            checkCross(0, 0)
        }
    }

    @Test
    fun crossOnEvenField() {
        with (Field(Rules(8, 8, initialPosition = InitialPosition.Cross))) {
            checkCross(3, 3)
        }
    }

    @Test
    fun crossOnOddField() {
        with (Field(Rules(9, 9, initialPosition = InitialPosition.Cross))) {
            checkCross(3, 3)
        }
    }

    @Test
    fun crossDoesntFitField() {
        assertFails { Field(Rules(1, 1, initialPosition = InitialPosition.Cross)) }
    }

    private fun Field.checkCross(x: Int, y: Int) {
        val firstPlayerPlaced = Player.First.createPlacedState()
        val secondPlayerPlaced = Player.Second.createPlacedState()
        Position(x, y).normalize().getState().checkPlaced(firstPlayerPlaced)
        Position(x, y + 1).normalize().getState().checkPlaced(secondPlayerPlaced)
        Position(x + 1, x + 1).normalize().getState().checkPlaced(firstPlayerPlaced)
        Position(x + 1, y).normalize().getState().checkPlaced(secondPlayerPlaced)
    }
}