package org.dots.game.core

import org.dots.game.core.Field.Companion.OFFSET

fun Field.dump(printNumbers: Boolean = true, padding: Int = Int.MAX_VALUE, printCoordinates: Boolean = true, debugInfo: Boolean = false): String {
    var minX = width
    var maxX = OFFSET
    var minY = height
    var maxY = OFFSET

    var maxMarkerLength = 0
    val positionToMoveResult = moveSequence.associateBy { it.position }

    val dotsRepresentation = Array(realWidth) { x ->
        Array(realHeight) { y ->
            val position = Position(x, y)
            val state = position.getState()
            val isTerritory = debugInfo && state.checkTerritory()
            val isPlaced = state.checkPlaced()

            buildString {
                if (isTerritory) {
                    append(playerMarker.getValue(state.getTerritoryPlayer()))
                }
                if (state.checkPlaced()) {
                    append(playerMarker.getValue(state.getPlacedPlayer()))
                } else if (isTerritory) {
                    append(TERRITORY_EMPTY_MARKER)
                }
                val emptyBaseAtPosition = emptyBasePositionsSequence[position]
                if (debugInfo && emptyBaseAtPosition != null) {
                    require(!isTerritory && !isPlaced)
                    append(EMPTY_TERRITORY_MARKER)
                    append(playerMarker.getValue(emptyBaseAtPosition.player))
                }
                if (isEmpty()) {
                    append(
                        when (x) {
                            0 -> {
                                when (y) {
                                    0 -> '┌'
                                    realHeight - 1 -> '└'
                                    else -> '│'
                                }
                            }

                            realWidth - 1  -> {
                                when (y) {
                                    0 -> '┐'
                                    realHeight - 1 -> '┘'
                                    else -> '│'
                                }
                            }

                            else -> {
                                when (y) {
                                    0, realHeight - 1 -> '─'
                                    else -> EMPTY_POSITION
                                }
                            }
                        }
                    )
                } else {
                    if (printNumbers) {
                        positionToMoveResult[position]?.number?.let { append(it) }
                    }

                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }.also {
                if (it.length > maxMarkerLength) {
                    maxMarkerLength = it.length
                }
            }
        }
    }

    val firstX = maxOf(minX.toLong() - padding, 0).toInt()
    val firstY = maxOf(minY.toLong() - padding, 0).toInt()
    val lastX = minOf(maxX.toLong() + padding, (realWidth - 1).toLong()).toInt()
    val lastY = minOf(maxY.toLong() + padding, (realHeight - 1).toLong()).toInt()

    if (printCoordinates) {
        lastX.toString().length.let { if (it > maxMarkerLength) maxMarkerLength = it }
    }

    return buildString {
        fun StringBuilder.appendWithPadding(str: String, x: Int) {
            append(str)
            if (x < lastX) {
                (0 until maxMarkerLength - str.length + 1).forEach { _ -> append(' ') }
            }
        }

        for (y in firstY..lastY) {
            if (printCoordinates) {
                if (y == firstY) {
                    appendWithPadding("\\", 0)
                    for (x in firstX..lastX) {
                        appendWithPadding(x.toString(), x)
                    }
                    append('\n')
                }
                appendWithPadding(y.toString(), 0)
            }
            for (x in firstX..lastX) {
                appendWithPadding(dotsRepresentation[x][y], x)
            }
            if (y < lastY) {
                append('\n')
            }
        }
    }
}

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