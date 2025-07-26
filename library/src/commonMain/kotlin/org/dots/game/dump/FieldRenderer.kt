import org.dots.game.core.EMPTY_POSITION_MARKER
import org.dots.game.core.EMPTY_TERRITORY_MARKER
import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.SURROUNDING_MARKER
import org.dots.game.core.TERRITORY_EMPTY_MARKER
import org.dots.game.core.playerMarker

data class DumpParameters(
    val printNumbers: Boolean = true,
    val padding: Int = Int.MAX_VALUE,
    val printCoordinates: Boolean = true,
    val debugInfo: Boolean = false,
) {
    companion object {
        val DEFAULT = DumpParameters()
    }
}

fun Field.render(dumpParameters: DumpParameters = DumpParameters.DEFAULT): String {
    val (printNumbers, padding, printCoordinates, debugInfo) = dumpParameters

    var minX = realWidth - 1
    var maxX = 0
    var minY = realHeight - 1
    var maxY = 0

    var maxMarkerLength = 0
    val positionToNumber = buildMap {
        moveSequence.forEachIndexed { index, move ->
            this[move.position] = index
        }
    }

    val dotsRepresentation = Array(realWidth) { x ->
        Array(realHeight) { y ->
            val position = Position(x, y)
            val state = position.getState()
            val activePlayer = state.getActivePlayer()
            val placedPlayer = state.getPlacedPlayer()
            val emptyTerritoryPlayer = state.getEmptyTerritoryPlayer()
            val isTerritory = state.isTerritory()
            val isSurrounding = state.isSurrounding()

            buildString {
                if (emptyTerritoryPlayer != Player.None) {
                    require(activePlayer == Player.None && placedPlayer == Player.None && !isSurrounding && !isTerritory)
                    if (debugInfo) {
                        append(EMPTY_TERRITORY_MARKER)
                        append(playerMarker.getValue(emptyTerritoryPlayer))
                    }
                } else if (checkPositionWithinBounds(position)) {
                    if (isTerritory) {
                        require(!isSurrounding)
                    } else if (isSurrounding) {
                        require(!isTerritory)
                    }

                    if (debugInfo && isTerritory) {
                        append(playerMarker.getValue(activePlayer))
                        append(
                            if (placedPlayer == Player.None)
                                TERRITORY_EMPTY_MARKER
                            else
                                playerMarker.getValue(placedPlayer)
                        )
                    } else {
                        append(playerMarker.getValue(placedPlayer))
                    }

                    if (debugInfo && isSurrounding) {
                        append(SURROUNDING_MARKER)
                    }
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
                                    else -> EMPTY_POSITION_MARKER
                                }
                            }
                        }
                    )
                } else {
                    if (printNumbers) {
                        positionToNumber[position]?.let { append(it) }
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

    if (moveSequence.isEmpty()) {
        minX = realWidth / 2
        minY = realHeight / 2
        maxX = minX
        maxY = minY
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