package org.dots.game.core.features

import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position
import kotlin.math.sqrt

/**
 *
 * | N | Positions | Square Distance | Count |
 * |---|-------|---|---|
 * | 0 | `. * .` | 0 | 1 |
 * |   |         |   |   |
 * | 1 | `. x .` | 1 | 4 |
 * |   | `x * x` |   |   |
 * |   | `. x .` |   |   |
 * |   |         |   |   |
 * | 2 | `x . x` | 2 | 4 |
 * |   | `. * .` |   |   |
 * |   | `x . x` |   |   |
 * |   |         |   |   |
 * | 3 | `. . x . .`| 4 | 4 |
 * |   | `. . . . .`|   |   |
 * |   | `x . * . x`|   |   |
 * |   | `. . . . .`|   |   |
 * |   | `. . x . .`|   |   |
 * |   |            |   |   |
 * | 4 | `. x . x .`| 5 | 8 |
 * |   | `x . . . x`|   |   |
 * |   | `. . * . .`|   |   |
 * |   | `x . . . x`|   |   |
 * |   | `. x . x .`|   |   |
 * |   |            |   |   |
 * | 5 | `x . . . x`| 8 | 4 |
 * |   | `. . . . .`|   |   |
 * |   | `. . * . .`|   |   |
 * |   | `. . . . .`|   |   |
 * |   | `x . . . x`|   |   |
 * |   |            |   |   |
 * | 6 | `. . . x . . .`| 9  | 4 |
 * |   | `. . . . . . .`|    |   |
 * |   | `. . . . . . .`|    |   |
 * |   | `x . . * . . x`|    |   |
 * |   | `. . . . . . .`|    |   |
 * |   | `. . . . . . .`|    |   |
 * |   | `. . . x . . .`|    |   |
 * |   |                |    |   |
 * | 7 | `. . x . x . .`| 10 | 8 |
 * |   | `. . . . . . .`|    |   |
 * |   | `x . . . . . x`|    |   |
 * |   | `. . . * . . .`|    |   |
 * |   | `x . . . . . x`|    |   |
 * |   | `. . . . . . .`|    |   |
 * |   | `. . x . x . .`|    |   |
 * |   |                |    |   |
 *
 */

const val maxDistanceIdentifierNumber: Int = 7

val squareDistances: List<Int> = buildSet {
    val limit = Field.MAX_SIZE * Field.MAX_SIZE
    val limitSquare = sqrt(limit.toDouble()).toInt()
    for (i in 0..limitSquare) {
        for (j in i..limitSquare) {
            add(i * i + j * j)
        }
    }
}.sorted()

fun Field.getPositionsAtDistance(distanceId: Int): Set<Position> {
    return HashSet<Position>().apply {
        for (move in moveSequence) {
            val position = move.position
            with(this) {
                val state = position.getState()
                val player = state.getActivePlayer()
                if (player == Player.None) continue

                if (contains(position)) continue

                var addPosition = false
                fun checkAndAdd(x: Int, y: Int) {
                    val position = getPositionIfWithinBounds(x, y) ?: return

                    if (position.getState().isActive(player)) {
                        add(position)
                        addPosition = true
                    }
                }

                val (x, y) = position.toXY(realWidth)
                when (distanceId) {
                    0 -> { // just a position on the field
                        if (position.getState().isActive(player)) {
                            addPosition = true
                        }
                    }
                    1 -> { // sq distance: 1, vertical/horizontal connections
                        checkAndAdd(x, y - 1)
                        checkAndAdd(x + 1, y)
                        checkAndAdd(x, y + 1)
                        checkAndAdd(x - 1, y)
                    }
                    2 -> { // sq distance: 2, diagonal connections
                        checkAndAdd(x - 1, y - 1)
                        checkAndAdd(x + 1, y - 1)
                        checkAndAdd(x + 1, y + 1)
                        checkAndAdd(x - 1, y + 1)
                    }
                    3 -> { // sq distance: 4, vertical/horizontal by 2
                        checkAndAdd(x, y - 2)
                        checkAndAdd(x + 2, y)
                        checkAndAdd(x, y + 2)
                        checkAndAdd(x - 2, y)
                    }
                    4 -> { // sq distance: 5, vertical/horizontal by 2 + diagonal by 1
                        checkAndAdd(x + 1, y - 2)
                        checkAndAdd(x + 2, y - 1)
                        checkAndAdd(x + 2, y + 1)
                        checkAndAdd(x + 1, y + 2)
                        checkAndAdd(x - 1, y + 2)
                        checkAndAdd(x - 2, y + 1)
                        checkAndAdd(x - 2, y - 1)
                        checkAndAdd(x - 1, y - 2)
                    }
                    5 -> { // sq distance: 8, diagonal connections by 2
                        checkAndAdd(x + 2, y - 2)
                        checkAndAdd(x + 2, y + 2)
                        checkAndAdd(x - 2, y + 2)
                        checkAndAdd(x - 2, y - 2)
                    }
                    6 -> { // sq distance: 9, vertical/horizontal by 3
                        checkAndAdd(x, y - 3)
                        checkAndAdd(x + 3, y)
                        checkAndAdd(x, y + 3)
                        checkAndAdd(x - 3, y)
                    }
                    maxDistanceIdentifierNumber -> { // sq distance: 10, vertical/horizontal by 3 + diagonal by 1
                        checkAndAdd(x + 1, y - 3)
                        checkAndAdd(x + 3, y - 1)
                        checkAndAdd(x + 3, y + 1)
                        checkAndAdd(x + 1, y + 3)
                        checkAndAdd(x - 1, y + 3)
                        checkAndAdd(x - 3, y + 1)
                        checkAndAdd(x - 3, y - 1)
                        checkAndAdd(x - 1, y - 3)
                    }
                    else -> {
                        error("The maximal reasonable value of squared distance is ${squareDistances[maxDistanceIdentifierNumber]} ($maxDistanceIdentifierNumber)")
                    }
                }
                if (addPosition) add(position)
            }
        }
    }
}
