package org.dots.game.core

import DumpParameters
import render
import kotlin.reflect.KProperty

fun Field.getStrongConnectionLinePositions(position: Position): List<Position> {
    val state = position.getState()
    if (state.isTerritory()) return emptyList()

    val player = state.getActivePlayer()
    if (player == Player.None) return emptyList()

    return buildList {
        position.forEachAdjacent(realWidth) {
            if (it.getState().isActiveAndNotTerritory(player)) {
                add(it)
            }
            true
        }
    }
}

/**
 * Used for graphical representation for last-placed dot
 */
fun Field.getPositionsOfConnection(position: Position, diagonalConnections: Boolean = false): List<Position> {
    val state = position.getState()
    if (state.isTerritory()) return emptyList()

    val player = state.getActivePlayer()
    if (player == Player.None) return emptyList()

    val activePositions = buildList {
        position.clockwiseBigJumpWalk(position.xm1ym1(realWidth), realWidth) {
            if (it.getState().isActiveAndNotTerritory(player)) {
                add(it)
            }
            true
        }
    }

    return buildList {
        fun addCurrentAndOriginalIfNeeded(currentPosition: Position) {
            if (isNotEmpty() && currentPosition.squareDistanceTo(last(), realWidth) > 2) {
                add(position)
            }
            add(currentPosition)
        }

        for ((index, activePosition) in activePositions.withIndex()) {
            if (activePosition.squareDistanceTo(position, realWidth) > 1) { // weak connection
                val prevActivePosition = activePositions[(index - 1 + activePositions.size) % activePositions.size]
                val nextActivePosition = activePositions[(index + 1) % activePositions.size]
                val distanceToPrev = activePosition.squareDistanceTo(prevActivePosition, realWidth)
                val distanceToNext = activePosition.squareDistanceTo(nextActivePosition, realWidth)
                if ((prevActivePosition == nextActivePosition && distanceToPrev == 1) ||
                    (distanceToPrev == 1 && distanceToNext > 1 || distanceToPrev > 1 && distanceToNext == 1) ||
                    diagonalConnections && (distanceToPrev > 1 || distanceToNext > 1)
                ) {
                    addCurrentAndOriginalIfNeeded(activePosition)
                }
            } else { // strong connection
                addCurrentAndOriginalIfNeeded(activePosition)
            }
        }

        if (isNotEmpty() && (last().squareDistanceTo(first(), realWidth) > 2 || size <= 2)) {
            add(position)
        }
    }
}

/**
 * Returns outer/inner closures that have sorted positions (square distance between adjacent positions <= 2).
 * It's useful for surrounding rendering.
 */
fun Base.getSortedClosurePositions(field: Field, considerTerritoryPositions: Boolean = false): ExtendedClosureInfo {
    val baseMode = field.rules.baseMode
    if (baseMode != BaseMode.AllOpponentDots && !considerTerritoryPositions) {
        return ExtendedClosureInfo(closurePositions.toList(), emptyList())
    } else {
        val closureSet = (if (considerTerritoryPositions) rollbackPositions else closurePositions).toHashSet()
        var outerClosure: List<Position> = emptyList()
        val innerClosures = mutableListOf<List<Position>>()

        var firstClosure = true
        while (closureSet.isNotEmpty()) {
            val minY = closureSet.minOf { it.getY(field.realWidth) }
            val topLeftMostPosition = closureSet.filter { it.getY(field.realWidth) == minY }.minBy { it.getX(field.realWidth) }

            // The outer closure should be minimal, the inner closure should be maximal
            val newClosure = closureSet.extractClosure(
                topLeftMostPosition,
                // The next position should be inner for outer closure and outer for inner closure for AllOpponentDots
                // However, in case of territory positions there is only a single outer closure that should be outer-walked
                innerWalk = !considerTerritoryPositions && firstClosure,
                field.realWidth,
            )
            if (firstClosure) {
                outerClosure = newClosure
                if (considerTerritoryPositions) {
                    break
                }
            } else {
                innerClosures.add(newClosure)
            }
            firstClosure = false
        }

        return ExtendedClosureInfo(outerClosure, innerClosures)
    }
}

data class ExtendedClosureInfo(val outerClosure: List<Position>, val innerClosures: List<List<Position>>)

private fun HashSet<Position>.extractClosure(initialPosition: Position, innerWalk: Boolean, fieldStride: Int): List<Position> {
    val closurePositions = mutableListOf(initialPosition)
    var square = 0
    var currentPosition: Position = initialPosition
    // The next position should always be inner for outer closure and outer for inner closure
    var nextPosition = if (innerWalk) {
        currentPosition.xp1yp1(fieldStride)
    } else {
        currentPosition.xm1ym1(fieldStride)
    }

    loop@ do {
        val walkCompleted = currentPosition.clockwiseBigJumpWalk(nextPosition, fieldStride) {
            return@clockwiseBigJumpWalk if (contains(it)) {
                square += currentPosition.getSquare(it, fieldStride)

                if (it == initialPosition) {
                    break@loop
                }

                closurePositions.add(it)
                nextPosition = currentPosition
                currentPosition = it

                false
            } else {
                true
            }
        }
        if (walkCompleted) {
            // Exit if `initialPosition` is single
            break
        }
    } while (true)

    require(if (innerWalk) square >= 0 else square <= 0)

    removeAll(closurePositions)

    return closurePositions
}

fun Field.unmakeAllMovesAndCheck(failFunc: (String) -> Unit) {
    fun check(assertion: Boolean, property: KProperty<*>) {
        if (!assertion) {
            failFunc("❗Failed check `${property.name}`")
        }
    }

    unmakeAllMoves()

    val emptyField = Field.create(rules)

    check(emptyField.initialMovesCount == initialMovesCount, ::moveSequence)
    check(emptyField.player1Score == player1Score, ::player1Score)
    check(emptyField.player2Score == player2Score, ::player2Score)
    check(emptyField.gameResult == gameResult, ::gameResult)
    check(emptyField.numberOfLegalMoves == numberOfLegalMoves, ::numberOfLegalMoves)

    for (x in 0 until realWidth) {
        for (y in 0 until realHeight) {
            val position = Position(x, y, realWidth)
            val actualState = position.getState()
            val expectedState = with(emptyField) { position.getState() }

            if (expectedState != actualState) {
                failFunc("❗Failed dots state at ($x, $y) [${position.value}]}. Expected: $expectedState; Actual: $actualState\n" +
                        render(DumpParameters(debugInfo = true))
                )
            }
        }
    }

    check(positionHash == emptyField.positionHash, ::positionHash)
}

fun Position.transform(type: TransformType, realWidth: Int, realHeight: Int, newFieldStride: Int): Position {
    if (isGameOverMove) return this
    val (x, y) = toXY(realWidth)
    return when (type) {
        TransformType.RotateCw90 -> Position(realHeight - 1 - y, x, newFieldStride)
        TransformType.Rotate180 -> Position(realWidth - x, realHeight - y - 1, newFieldStride)
        TransformType.RotateCw270 -> Position(y, realWidth - x, newFieldStride)
        TransformType.FlipHorizontal -> Position(realWidth - x, y, newFieldStride)
        TransformType.FlipVertical -> Position(x, realHeight - 1 - y, newFieldStride)
    }
}

fun PositionXY.transform(type: TransformType, width: Int, height: Int): PositionXY {
    if (isGameOverMove) return this
    val (x, y) = this
    return when (type) {
        TransformType.RotateCw90 -> PositionXY(height - y + 1, x)
        TransformType.Rotate180 -> PositionXY(width - x + 1, height - y + 1)
        TransformType.RotateCw270 -> PositionXY(y, width - x + 1)
        TransformType.FlipHorizontal -> PositionXY(width - x + 1, y)
        TransformType.FlipVertical -> PositionXY(x, height - y + 1)
    }
}
