package org.dots.game.core

import org.dots.game.core.InitialPositionType.Cross
import org.dots.game.core.InitialPositionType.Empty

class Rules(
    val width: Int = 39,
    val height: Int = 32,
    val captureByBorder: Boolean = false,
    val captureEmptyBase: Boolean = false,
    val initialMoves: List<MoveInfo> = emptyList(),
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

fun InitialPositionType.generateDefaultInitialPositions(width: Int, height: Int): List<MoveInfo>? {
    when {
        this == Empty -> {
            return emptyList()
        }
        this == Cross -> {
            if (width < 2 || height < 2) return null

            val startPosition = Position(width / 2, height / 2)
            return listOf(
                MoveInfo(startPosition, Player.First),
                MoveInfo(Position(startPosition.x + 1, startPosition.y + 1), Player.First),
                MoveInfo(Position(startPosition.x + 1, startPosition.y), Player.Second),
                MoveInfo(Position(startPosition.x, startPosition.y + 1), Player.Second),
            )
        }
        else -> {
            return emptyList()
        }
    }
}