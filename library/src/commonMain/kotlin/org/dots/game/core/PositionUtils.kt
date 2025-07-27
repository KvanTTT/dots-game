package org.dots.game.core

private const val LEFT_TOP_INDEX = 0
private const val TOP_INDEX = 1
private const val RIGHT_TOP_INDEX = 2
private const val RIGHT_INDEX = 3
private const val RIGHT_BOTTOM_INDEX = 4
private const val BOTTOM_INDEX = 5
private const val LEFT_BOTTOM_INDEX = 6
private const val LEFT_INDEX = 7

fun Position.squareDistanceTo(other: Position, fieldStride: Int): Int {
    val diffX = getX(fieldStride) - other.getX(fieldStride)
    val diffY = getY(fieldStride) - other.getY(fieldStride)
    return diffX * diffX + diffY * diffY
}

fun Position.squareDistanceToZero(fieldStride: Int): Int {
    val x = getX(fieldStride)
    val y = getY(fieldStride)
    return x * x + y * y
}

fun Position.getSquare(other: Position, fieldStride: Int): Int {
    return (value / fieldStride) * (other.value % fieldStride) - (other.value / fieldStride) * (value % fieldStride)
}

inline fun Position.forEachAdjacent(fieldStride: Int, action: (Position) -> Boolean): Boolean {
    if (!action(xym1(fieldStride))) return false
    if (!action(xp1y())) return false
    if (!action(xyp1(fieldStride))) return false
    if (!action(xm1y())) return false
    return true
}

/**
 * Call an @param[action] on each position during walking over positions that surrounds @param[Position].
 * The first position to walk relative the passed @param[other] is calculated in the following way:
 *
 * ```
 *   x . *   . x .   . . x   . . .   . . .   * . .   * . .   . . *
 *   . o .   . o .   . o .   . o x   . o .   . o .   . o .   x o .
 *   . . .   . . *   . . *   * . .   * . x   . x .   x . .   . . .
 * ```
 *  - o @param[Position]
 *  - x @param[other]
 *  - * first position to consider.
 *
 *  @return `true` if no predicate matches (typically, it's a single dot not linked to any other).
 */
internal inline fun Position.clockwiseBigJumpWalk(other: Position, fieldStride: Int, action: (Position) -> Boolean): Boolean {
    val initialIndex = getBigJumpInitialIndex(this, other, fieldStride)
    var index = initialIndex

    do {
        when (index) {
            LEFT_TOP_INDEX -> {
                if (!action(xm1ym1(fieldStride))) return false
                index++
            }
            TOP_INDEX -> {
                if (!action(xym1(fieldStride))) return false
                index++
            }
            RIGHT_TOP_INDEX -> {
                if (!action(xp1ym1(fieldStride))) return false
                index++
            }
            RIGHT_INDEX -> {
                if (!action(xp1y())) return false
                index++
            }
            RIGHT_BOTTOM_INDEX -> {
                if (!action(xp1yp1(fieldStride))) return false
                index++
            }
            BOTTOM_INDEX -> {
                if (!action(xyp1(fieldStride))) return false
                index++
            }
            LEFT_BOTTOM_INDEX -> {
                if (!action(xm1yp1(fieldStride))) return false
                index++
            }
            LEFT_INDEX -> {
                if (!action(xm1y())) return false
                index = 0
            }
            else -> error("Shouldn't be here")
        }
    } while (index != initialIndex)

    return true
}

private fun getBigJumpInitialIndex(position: Position, otherPosition: Position, fieldStride: Int): Int {
    return when (otherPosition.value - position.value) {
        -1,
        -1 - fieldStride -> RIGHT_TOP_INDEX
        -fieldStride,
        +1 - fieldStride -> RIGHT_BOTTOM_INDEX
        +1,
        +1 + fieldStride -> LEFT_BOTTOM_INDEX
        +fieldStride,
        -1 + fieldStride -> LEFT_TOP_INDEX
        else -> error("Shouldn't be here")
    }
}

internal fun getNextPosition(position: Position, otherPosition: Position, fieldStride: Int): Position {
    return when (otherPosition.value - position.value) {
        -1 -> position.xm1ym1(fieldStride)
        -1 - fieldStride -> position.xym1(fieldStride)
        -fieldStride -> position.xp1ym1(fieldStride)
        +1 - fieldStride -> position.xp1y()
        +1 -> position.xp1yp1(fieldStride)
        +1 + fieldStride -> position.xyp1(fieldStride)
        +fieldStride -> position.xm1yp1(fieldStride)
        -1 + fieldStride -> position.xm1y()
        else -> error("Shouldn't be here")
    }
}

