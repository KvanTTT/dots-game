package org.dots.game.core

import org.dots.game.core.InitialPositionType.Cross
import org.dots.game.core.InitialPositionType.Empty

class Rules(
    val width: Int = 39,
    val height: Int = 32,
    val captureByBorder: Boolean = false,
    val captureEmptyBase: Boolean = false,
    val player1InitialPositions: List<Position> = emptyList(),
    val player2InitialPositions: List<Position> = emptyList(),
) {
    companion object {
        val Standard = Rules()
    }

    val initialPositionType: InitialPositionType by lazy {
        InitialPositionType.Custom // TODO: implement detection
    }
}

enum class InitialPositionType {
    Empty,
    Cross,
    Custom;
}

fun InitialPositionType.generateDefaultInitialPosition(width: Int, height: Int): Pair<List<Position>, List<Position>>? {
    when {
        this == Empty -> {
            return Pair(emptyList(), emptyList())
        }
        this == Cross -> {
            if (width < 2 || height < 2) return null

            val startPosition = Position(width / 2, height / 2)
            val player1InitialPositions = listOf(
                startPosition,
                Position(startPosition.x + 1, startPosition.y + 1),
            )
            val player2InitialPositions = listOf(
                Position(startPosition.x + 1, startPosition.y),
                Position(startPosition.x, startPosition.y + 1),
            )
            return player1InitialPositions to player2InitialPositions
        }
        else -> {
            return Pair(emptyList(), emptyList())
        }
    }
}