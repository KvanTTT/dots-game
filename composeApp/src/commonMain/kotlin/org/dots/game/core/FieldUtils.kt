package org.dots.game.core

fun Field.getStrongConnectionLinePositions(position: Position): List<Position> {
    val state = position.getState()
    if (!state.checkPlaced() || state.checkTerritory()) return emptyList()

    val player = state.getPlacedPlayer()
    val playerPlaced = player.createPlacedState()
    val (x, y) = position

    return buildList {
        fun Position.addIfActive() {
            if (getState().checkActive(playerPlaced)) add(this)
        }

        Position(x, y - 1).addIfActive()
        Position(x + 1, y).addIfActive()
        Position(x, y + 1).addIfActive()
        Position(x - 1, y).addIfActive()
    }
}

/**
 * Used for graphical representation for last-placed dot
 */
fun Field.getPositionsOfConnection(position: Position, diagonalConnections: Boolean = false): List<Position> {
    val state = position.getState()
    if (!state.checkPlaced() || state.checkTerritory()) return emptyList()

    val player = state.getPlacedPlayer()
    val playerPlaced = player.createPlacedState()
    val (x, y) = position

    val activePositions = buildList {
        position.clockwiseWalk(Position(x, y - 1)) {
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
fun Base.getSortedClosurePositions(field: Field): ExtendedClosureInfo {
    if (field.rules.baseMode != BaseMode.AllOpponentDots) {
        // Closures are always correctly sorted for surrounding-based formats, and inner closures are never used for them
        return ExtendedClosureInfo(closurePositions, emptyList())
    } else {
        val closureSet = closurePositions.toMutableSet()
        var outerClosure: List<Position> = emptyList()
        val innerClosures = mutableListOf<List<Position>>()

        var firstClosure = true
        while (closureSet.isNotEmpty()) {
            val positionClosestToHorizontalBorder = closureSet.minBy { it.y }
            // The outer closure should be minimal, the inner closure should be maximal
            val newClosure = closureSet.extractClosure(positionClosestToHorizontalBorder, inner = !firstClosure)
            if (firstClosure) {
                outerClosure = newClosure
            } else {
                innerClosures.add(newClosure)
            }
            firstClosure = false
        }

        return ExtendedClosureInfo(outerClosure, innerClosures)
    }
}

data class ExtendedClosureInfo(val outerClosure: List<Position>, val innerClosures: List<List<Position>>)

private fun MutableSet<Position>.extractClosure(initialPosition: Position, inner: Boolean): List<Position> {
    val closurePositions = mutableListOf(initialPosition)
    var square = 0
    var currentPosition: Position = initialPosition
    // The next position should always be inner for outer closure and outer for inner closure
    var nextPosition = Position(currentPosition.x, currentPosition.y + (if (!inner) +1 else -1))

    loop@ do {
        val walkCompleted = currentPosition.clockwiseWalk(nextPosition) {
            return@clockwiseWalk if (contains(it)) {
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

    require(if (!inner) square >= 0 else square <= 0)

    removeAll(closurePositions)

    return closurePositions
}