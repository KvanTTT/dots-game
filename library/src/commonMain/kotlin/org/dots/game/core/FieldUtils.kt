package org.dots.game.core

import kotlin.reflect.KProperty

fun Field.getStrongConnectionLinePositions(position: Position): List<Position> {
    val state = position.getState()
    if (!state.checkActive()) return emptyList()

    val player = state.getPlacedPlayer()
    val playerPlaced = player.createPlacedState()

    return buildList {
        position.forEachAdjacent {
            if (it.getState().checkActive(playerPlaced)) {
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
    if (!state.checkActive()) return emptyList()

    val player = state.getPlacedPlayer()
    val playerPlaced = player.createPlacedState()
    val (x, y) = position

    val activePositions = buildList {
        position.clockwiseBigJumpWalk(Position(x - 1, y - 1)) {
            if (it.getState().checkActive(playerPlaced)) {
                add(it)
            }
            true
        }
    }

    return buildList {
        fun addCurrentAndOriginalIfNeeded(currentPosition: Position) {
            if (isNotEmpty() && currentPosition.squareDistanceTo(last()) > 2) {
                add(position)
            }
            add(currentPosition)
        }

        for ((index, activePosition) in activePositions.withIndex()) {
            if (activePosition.squareDistanceTo(position) > 1) { // weak connection
                val prevActivePosition = activePositions[(index - 1 + activePositions.size) % activePositions.size]
                val nextActivePosition = activePositions[(index + 1) % activePositions.size]
                val distanceToPrev = activePosition.squareDistanceTo(prevActivePosition)
                val distanceToNext = activePosition.squareDistanceTo(nextActivePosition)
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

        if (isNotEmpty() && (last().squareDistanceTo(first()) > 2 || size <= 2)) {
            add(position)
        }
    }
}

/**
 * Returns outer/inner closures that have sorted positions (square distance between adjacent positions <= 2).
 * It's useful for surrounding drawing.
 */
fun Base.getSortedClosurePositions(field: Field, considerTerritoryPositions: Boolean = false): ExtendedClosureInfo {
    val baseMode = field.rules.baseMode
    if (baseMode != BaseMode.AllOpponentDots && !considerTerritoryPositions) {
        return ExtendedClosureInfo(closurePositions, emptyList())
    } else {
        val closureSet = (if (considerTerritoryPositions) previousPositionStates.map { it.position } else closurePositions).toHashSet()
        var outerClosure: List<Position> = emptyList()
        val innerClosures = mutableListOf<List<Position>>()

        var firstClosure = true
        while (closureSet.isNotEmpty()) {
            val positionClosestToHorizontalBorder = closureSet.minBy { it.y }
            // The outer closure should be minimal, the inner closure should be maximal
            val newClosure = closureSet.extractClosure(
                positionClosestToHorizontalBorder,
                // The next position should be inner for outer closure and outer for inner closure for AllOpponentDots
                // However, in case of territory positions there is only a single outer closure that should be outer-walked
                innerWalk = !considerTerritoryPositions && firstClosure,
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

private fun HashSet<Position>.extractClosure(initialPosition: Position, innerWalk: Boolean): List<Position> {
    val closurePositions = mutableListOf(initialPosition)
    var square = 0
    var currentPosition: Position = initialPosition
    // The next position should always be inner for outer closure and outer for inner closure
    var nextPosition = Position(currentPosition.x + (if (innerWalk) +1 else -1), currentPosition.y + (if (innerWalk) +1 else -1))

    loop@ do {
        val walkCompleted = currentPosition.clockwiseBigJumpWalk(nextPosition) {
            return@clockwiseBigJumpWalk if (contains(it)) {
                square += currentPosition.getSquare(it)

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

    check(moveSequence.size == initialMovesCount, ::moveSequence)
    check(0 == player1Score, ::player1Score)
    check(0 == player2Score, ::player2Score)
    check(gameResult == null, ::gameResult)
    check(width * height - initialMovesCount == numberOfLegalMoves, ::numberOfLegalMoves)
    var actualInitialMovesCount = 0
    for (x in 0 until realWidth) {
        for (y in 0 until realHeight) {
            val position = Position(x, y)
            val borderOrEmptyState = if (rules.captureByBorder && position.isBorder()) DotState.Border else DotState.Empty
            if (borderOrEmptyState != Position(x, y).getState()) {
                actualInitialMovesCount++
            }
        }
    }

    check(initialMovesCount == actualInitialMovesCount, ::initialMovesCount)
}

fun Position.transform(type: TransformType, width: Int, height: Int): Position {
    if (isGameOverMove()) return this
    val (x, y) = this
    return when (type) {
        TransformType.RotateCw90 -> Position(height - 1 - y, x)
        TransformType.Rotate180 -> Position(width - 1 - x, height - y - 1)
        TransformType.RotateCw270 -> Position(y, width - 1 - x)
        TransformType.FlipHorizontal -> Position(width - 1 - x, y)
        TransformType.FlipVertical -> Position(x, height - 1 - y)
    }
}
