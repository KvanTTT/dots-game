import org.dots.game.core.ClassSettings
import org.dots.game.core.EMPTY_POSITION_MARKER
import org.dots.game.core.EMPTY_TERRITORY_MARKER
import org.dots.game.core.Field
import org.dots.game.core.Games
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.VISITED_MARKER
import org.dots.game.core.TERRITORY_EMPTY_MARKER
import org.dots.game.core.playerMarker
import org.dots.game.sgf.SgfWriter

data class DumpParameters(
    val printNumbers: Boolean = true,
    val padding: Int = Int.MAX_VALUE,
    val printCoordinates: Boolean = true,
    val printBorders: Boolean = false,
    val debugInfo: Boolean = false,
    val isSgf: Boolean = false,
) : ClassSettings<DumpParameters>() {
    override val default: DumpParameters
        get() = DEFAULT

    companion object {
        val DEFAULT = DumpParameters()
    }
}

fun Field.render(dumpParameters: DumpParameters = DumpParameters.DEFAULT): String {
    val (printNumbers, padding, printCoordinates, borders, debugInfo, isSgf) = dumpParameters

    if (isSgf) {
        return SgfWriter.write(Games.fromField(this))
    }

    var minPositionX = realWidth - 1
    var maxPositionX = 0
    var minPositionY = realHeight - 1
    var maxPositionY = 0

    var maxMarkerLength = 0
    val positionToNumber = buildMap {
        moveSequence.forEachIndexed { index, move ->
            this[move.position] = index
        }
    }

    val dotsRepresentation: Array<Array<String>> = Array(realHeight) {
        Array(realWidth + 1) { // The extra value used for normalization to a rectangle
            ""
        }
    }

    var x = 0
    var y = 0

    for (positionIndex in 0..<size) {
        val representation = if (x in 1..<realWidth && y in 1..<realHeight - 1) {
            val position = Position(positionIndex.toShort())
            buildString {
                val state = position.getState()
                val activePlayer = state.getActivePlayer()
                val placedPlayer = state.getPlacedPlayer()
                val emptyTerritoryPlayer = state.getEmptyTerritoryPlayer()
                val isTerritory = state.isTerritory()
                val isVisited = state.isVisited()
                if (emptyTerritoryPlayer != Player.None) {
                    require(activePlayer == Player.None && placedPlayer == Player.None && !isVisited && !isTerritory)
                    if (debugInfo) {
                        append(EMPTY_TERRITORY_MARKER)
                        append(playerMarker.getValue(emptyTerritoryPlayer))
                    } else {
                        append(EMPTY_POSITION_MARKER)
                    }
                } else {
                    if (isTerritory) {
                        require(!isVisited)
                    } else if (isVisited) {
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

                    if (debugInfo && isVisited) {
                        append(VISITED_MARKER)
                    }
                }
                if (printNumbers) {
                    positionToNumber[position]?.let { append(it) }
                }

                if (activePlayer != Player.None) {
                    if (x < minPositionX) minPositionX = x
                    if (x > maxPositionX) maxPositionX = x
                    if (y < minPositionY) minPositionY = y
                    if (y > maxPositionY) maxPositionY = y
                }
            }
        } else {
            when (x) {
                0 -> {
                    when (y) {
                        0 -> "┌"
                        realHeight - 1 -> "└"
                        else -> "│"
                    }
                }

                realWidth - 1 -> {
                    when (y) {
                        0 -> "┐"
                        realHeight - 1 -> "─"
                        else -> error("Incorrect coordinates ($x, $y)")
                    }
                }

                realWidth -> {
                    if (y == realHeight - 1) {
                        "┘"
                    } else {
                        ""
                    }
                }

                else -> {
                    if (y == 0 || y == realHeight - 1)
                        "─"
                    else
                        error("Incorrect coordinates ($x, $y)")
                }
            }
        }.also {
            if (it.length > maxMarkerLength) {
                maxMarkerLength = it.length
            }
        }

        dotsRepresentation[y][x] = representation

        if (x == realWidth - 1 && y < realHeight - 1) {
            x = 0
            y++
        } else {
            x++
        }
    }

    if (moveSequence.isEmpty()) {
        minPositionX = realWidth / 2
        minPositionY = realHeight / 2
        maxPositionX = minPositionX
        maxPositionY = minPositionY
    }

    val minCoordinate: Long
    val maxX: Long
    val maxY: Long
    if (borders) {
        minCoordinate = 0L
        maxX = realWidth.toLong()
        maxY = (realHeight - 1).toLong()
    } else {
        minCoordinate = 1L
        maxX = (realWidth - 1).toLong()
        maxY = (realHeight - 2).toLong()
    }

    val firstX = maxOf(minPositionX.toLong() - padding, minCoordinate).toInt()
    val firstY = maxOf(minPositionY.toLong() - padding, minCoordinate).toInt()
    val lastX = minOf(maxPositionX.toLong() + padding, maxX).toInt()
    val lastY = minOf(maxPositionY.toLong() + padding, maxY).toInt()

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
                appendWithPadding(dotsRepresentation[y][x], x)
            }
            if (y < lastY) {
                append('\n')
            }
        }
    }
}