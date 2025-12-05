package org.dots.game.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.dots.game.UiSettings
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.LegalMove
import org.dots.game.core.MoveMode
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.features.getOneMoveCapturingAndBasePositions
import org.dots.game.core.features.getPositionsAtDistance
import org.dots.game.core.features.squareDistances
import org.dots.game.core.getPositionsOfConnection
import org.dots.game.core.getSortedClosurePositions
import org.dots.game.core.getStrongConnectionLinePositions
import org.dots.game.core.squareDistanceTo
import org.dots.game.maxFieldDimension
import org.dots.game.platform
import kotlin.math.round

private val borderPaddingRatio = 2.0f
private val textPaddingRatio = 1.0f
private val cellSize = 22.dp
private val linesColor = Color.hsv(0.0f, 0.0f, 0.25f)

private val fieldColor = Color.hsv(0.0f, 0.0f, 1.0f)
private val dotRadiusRatio = 0.28f
private val lastMoveRadiusRatio = 0.14f
private val baseAlpha = 0.5f

private val lastMoveColor = Color.White

private val fieldPadding = cellSize * borderPaddingRatio
private val textPadding = cellSize * textPaddingRatio
private val dotRadius = cellSize * dotRadiusRatio
private val lastMoveRadius = cellSize * lastMoveRadiusRatio

private val connectionThickness = 2.dp
private val outOfBoundDrawRatio = dotRadiusRatio
private val minDistanceId = 2
private val maxDistanceId = 2

private val capturingMoveMarkerSize = cellSize * 0.35f
private val capturingBaseMoveMarkerSize = cellSize * 0.2f

enum class ConnectionDrawMode {
    None,
    Lines,
    PolygonOutline,
    PolygonFill,
    PolygonOutlineAndFill;

    val polygonDrawMode: PolygonDrawMode?
        get() {
            return when (this) {
                PolygonOutline -> PolygonDrawMode.Outline
                PolygonFill -> PolygonDrawMode.Fill
                PolygonOutlineAndFill -> PolygonDrawMode.OutlineAndFill
                else -> null
            }
        }
}

sealed class ConnectionDrawModeKind {
    object None : ConnectionDrawModeKind()
    object Lines : ConnectionDrawModeKind()
    class Polygon(val polygonDrawMode: PolygonDrawMode) : ConnectionDrawModeKind()
}

enum class PolygonDrawMode {
    Outline,
    Fill,
    OutlineAndFill;

    val drawOutline: Boolean
        get() = this == Outline || this == OutlineAndFill
    val drawFill: Boolean
        get() = this == Fill || this == OutlineAndFill
}

private val linesThickness = 0.75.dp

fun getFieldSizeSize(width: Int, height: Int): DpSize {
    return DpSize(
        cellSize * (width - 1) + fieldPadding * 2,
        cellSize * (height - 1) + fieldPadding * 2
    )
}

val maxFieldSize = getFieldSizeSize(maxFieldDimension, maxFieldDimension)

@Composable
fun FieldView(
    updateFieldObject: Any?,
    moveMode: MoveMode,
    field: Field,
    uiSettings: UiSettings,
    onMovePlaced: (Position, Player) -> Unit = { pos, player -> require(field.makeMoveUnsafe(pos, player) is LegalMove) }
) {
    val currentDensity = LocalDensity.current
    var pointerFieldPosition: Position? by remember { mutableStateOf(null) }

    Box(
        Modifier
            .size(getFieldSizeSize(field.width, field.height))
            .pointerInput(moveMode, field) {
                awaitPointerEventScope {
                    var isPrimaryPressed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val currentPlayer = moveMode.getMovePlayer(field)
                        when (event.type) {
                            PointerEventType.Move -> {
                                val newPointerFieldPosition = event.toFieldPositionIfValid(field, currentPlayer, currentDensity)
                                if (newPointerFieldPosition != pointerFieldPosition) {
                                    pointerFieldPosition = newPointerFieldPosition
                                    isPrimaryPressed = false
                                }
                            }
                            PointerEventType.Press -> {
                                // Handle mouse left button click (if supported)
                                isPrimaryPressed = event.buttons.isPrimaryPressed
                            }
                            PointerEventType.Release -> {
                                if (isPrimaryPressed || !platform.supportsPrimaryButton) {
                                    val fieldPosition =
                                        event.toFieldPositionIfValid(field, currentPlayer, currentDensity)
                                    if (fieldPosition != null) {
                                        onMovePlaced(fieldPosition, currentPlayer)
                                        pointerFieldPosition =
                                            event.toFieldPositionIfValid(field, currentPlayer, currentDensity)
                                    }
                                }
                                isPrimaryPressed = false
                            }
                            PointerEventType.Exit -> {
                                pointerFieldPosition = null
                                isPrimaryPressed = false
                            }
                        }
                    }
                }
            }
    ) {
        Grid(field, uiSettings)
        Moves(updateFieldObject, field, uiSettings)
        if (!field.isGameOver()) {
            if (uiSettings.showDiagonalConnections) {
                AllConnections(updateFieldObject, field, uiSettings)
            }

            if (uiSettings.showThreats || uiSettings.showSurroundings) {
                ThreatsAndSurroundings(updateFieldObject, field, uiSettings)
            }
        }
        Pointer(pointerFieldPosition, moveMode, field, uiSettings)
    }
}

@Composable
private fun Grid(field: Field, uiSettings: UiSettings) {
    val textMeasurer = rememberTextMeasurer()
    with (LocalDensity.current) {
        val fieldPaddingPx = fieldPadding.toPx()
        val verticalLinesEndPx = fieldPaddingPx + (cellSize * (field.height - 1)).toPx()
        val horizontalLinesEndPx = fieldPaddingPx + (cellSize * (field.width - 1)).toPx()
        val linesThicknessPx = linesThickness.toPx()
        val textPaddingPx = (fieldPadding - textPadding).toPx()

        Canvas(Modifier.fillMaxSize().graphicsLayer().background(fieldColor)) {
            val sizeWidth = size.width
            val sizeHeight = size.height

            for (x in Field.OFFSET until field.width + Field.OFFSET) {
                val xPx = x.coordinateToPx(this)

                val xCoordinateText = (x + (if (uiSettings.developerMode) -1 else 0)) .toString()
                val textLayoutResult = textMeasurer.measure(xCoordinateText)

                val textX = xPx - textLayoutResult.size.width / 2
                val textY = textPaddingPx - textLayoutResult.size.height

                if (textX < sizeWidth && textY < sizeHeight) {
                    drawText(textMeasurer, xCoordinateText, Offset(textX, textY))
                }

                drawLine(linesColor,
                    Offset(xPx, fieldPaddingPx),
                    Offset(xPx, verticalLinesEndPx),
                    linesThicknessPx,
                )
            }

            for (y in Field.OFFSET until field.height + Field.OFFSET) {
                val yPx = y.coordinateToPx(this)

                val yCoordinateText = (if (uiSettings.developerMode) y - 1 else field.height + Field.OFFSET - y).toString()
                val textLayoutResult = textMeasurer.measure(yCoordinateText)

                val textX = textPaddingPx - textLayoutResult.size.width
                val textY = yPx - textLayoutResult.size.height / 2

               if (textX < sizeWidth && textY < sizeHeight) {
                    drawText(textMeasurer, yCoordinateText, Offset(textX, textY))
                }

                drawLine(
                    linesColor,
                    Offset(fieldPaddingPx, yPx),
                    Offset(horizontalLinesEndPx, yPx),
                    linesThicknessPx,
                )
            }
        }
    }
}

@Composable
private fun Moves(updateObject: Any?, field: Field, uiSettings: UiSettings) {
    val fieldWithIncrementalUpdate = Field.create(field.rules) // TODO: rewrite without using temp field

    val gameOverMove = field.moveSequence.lastOrNull()?.takeIf { it.position.isGameOverMove }

    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val dotRadiusPx = dotRadius.toPx()

        for ((index, moveResult = value) in field.moveSequence.withIndex()) {
            if (index >= field.initialMovesCount) {
                require(
                    fieldWithIncrementalUpdate.makeMoveUnsafe(
                        moveResult.position,
                        moveResult.player,
                        (moveResult as? GameResult)?.toExternalFinishReason()
                    ) is LegalMove
                )
            }

            val moveResultPosition = moveResult.position.takeUnless { it.isGameOverMove } ?: continue
            val color = uiSettings.toColor(moveResult.player)

            val connectionDrawMode = uiSettings.connectionDrawMode
            if (connectionDrawMode == ConnectionDrawMode.Lines) {
                drawStrongConnectionLines(fieldWithIncrementalUpdate, moveResultPosition, color)
            } else {
                val connectionPolygonDrawMode = connectionDrawMode.polygonDrawMode
                if (connectionPolygonDrawMode != null) {
                    val connections = fieldWithIncrementalUpdate.getPositionsOfConnection(moveResultPosition)
                    drawPolygon(
                        connections,
                        emptyList(),
                        moveResult.player,
                        connectionPolygonDrawMode,
                        field.realWidth,
                        uiSettings,
                    )
                }
            }

            drawCircle(
                color,
                dotRadiusPx,
                moveResultPosition.toPxOffset(field, this)
            )

            for (base in moveResult.bases) {
                if (!base.isReal) continue

                val (outerClosure, innerClosures) = base.getSortedClosurePositions(fieldWithIncrementalUpdate)
                drawPolygon(
                    outerClosure,
                    innerClosures,
                    base.player,
                    uiSettings.baseDrawMode,
                    field.realWidth,
                    uiSettings,
                )
            }
        }

        field.lastMove?.let {
            drawCircle(
                lastMoveColor,
                lastMoveRadius.toPx(),
                it.position.toPxOffset(field,this)
            )
        }

        updateObject
    }

    if (gameOverMove != null) {
        Canvas(Modifier.fillMaxSize().graphicsLayer().alpha(baseAlpha)) {

            val dotRadiusPx = dotRadius.toPx()

            for (base in gameOverMove.bases) {
                if (!base.isReal) continue

                val (outerClosure, innerClosures) = base.getSortedClosurePositions(
                    fieldWithIncrementalUpdate,
                    considerTerritoryPositions = true,
                )

                if (outerClosure.size == 1) {
                    drawCircle(
                        uiSettings.toColor(base.player),
                        dotRadiusPx,
                        outerClosure.single().toPxOffset(field, this)
                    )
                } else {
                    drawPolygon(
                        outerClosure,
                        innerClosures,
                        base.player,
                        uiSettings.baseDrawMode,
                        field.realWidth,
                        uiSettings,
                        isGrounding = true
                    )
                }
            }
        }
    }
}

@Composable
private fun AllConnections(updateObject: Any?, field: Field, uiSettings: UiSettings) {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        with(field) {
            for (distanceId in minDistanceId..maxDistanceId) {
                val distantPositions = field.getPositionsAtDistance(distanceId).toList()
                val squaredDistance = squareDistances[distanceId]

                val lines = buildSet {
                    for (i in 0 until distantPositions.size) {
                        val startPosition = distantPositions[i]
                        val stateState = startPosition.getState()
                        if (stateState.isTerritory()) continue

                        val player = stateState.getActivePlayer()
                        for (j in i + 1 until distantPositions.size) {
                            val endPosition = distantPositions[j]

                            if (endPosition.getState().let { it.isTerritory() || it.getActivePlayer() != player }) {
                                continue
                            }

                            if (startPosition.squareDistanceTo(endPosition, realWidth) != squaredDistance) {
                                continue
                            }

                            // Filter out overlapping lines
                            if (squaredDistance == 2 && uiSettings.connectionDrawMode.polygonDrawMode != null) {
                                val startPosXY = startPosition.toXY(field.realWidth)
                                val endPosXY = endPosition.toXY(field.realWidth)
                                val (diffX = first, diffY = second) = endPosXY - startPosXY
                                val adjPos1 = when (diffX) {
                                    1 -> startPosition.xp1y()
                                    -1 -> startPosition.xm1y()
                                    else -> error("Shouldn't be here")
                                }
                                val adjPos2 =  when (diffY) {
                                    1 ->  startPosition.xyp1(field.realWidth)
                                    -1 -> startPosition.xym1(field.realWidth)
                                    else -> error("Shouldn't be here")
                                }

                                if (adjPos1.getState().getActivePlayer() == player || adjPos2.getState().getActivePlayer() == player) {
                                    continue
                                }
                            }

                            add(startPosition to endPosition)
                        }
                    }
                }

                for (line in lines) {
                    val (start = first, end = second) = line
                    drawLine(
                        uiSettings.toColor(start.getState().getActivePlayer()),
                        start.toPxOffset(field, this@Canvas),
                        end.toPxOffset(field,this@Canvas),
                        strokeWidth = connectionThickness.toPx(),
                        alpha = 0.3f
                    )
                }
            }

            updateObject
        }
    }
}

@Composable
private fun ThreatsAndSurroundings(updateObject: Any?, field: Field, uiSettings: UiSettings) {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val (capturingPositions, basePositions) = field.getOneMoveCapturingAndBasePositions()

        if (uiSettings.showThreats) {
            val capturingMarkerSize = capturingMoveMarkerSize.toPx()
            capturingPositions.forEach {
                val (position = key, player = value) = it
                val (xPx = x, yPx = y) = position.toPxOffset(field, this)
                drawLine(
                    uiSettings.toColor(if (player == Player.WallOrBoth) Player.First else player).copy(0.7f),
                    Offset(xPx - capturingMarkerSize, yPx),
                    Offset(xPx + capturingMarkerSize, yPx),
                    strokeWidth = 3.dp.toPx(),
                )
                drawLine(
                    uiSettings.toColor(if (player == Player.WallOrBoth) Player.Second else player).copy(0.7f),
                    Offset(xPx, yPx - capturingMarkerSize),
                    Offset(xPx, yPx + capturingMarkerSize),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }

        if (uiSettings.showSurroundings) {
            val baseMarkerSize = capturingBaseMoveMarkerSize.toPx()
            basePositions.forEach {
                val (position = key, player = value) = it
                val (xPx = x, yPx = y) = position.toPxOffset(field,this)
                drawLine(
                    uiSettings.toColor(if (player == Player.WallOrBoth) Player.First else player).copy(0.7f),
                    Offset(xPx - baseMarkerSize, yPx - baseMarkerSize),
                    Offset(xPx + baseMarkerSize, yPx + baseMarkerSize),
                    strokeWidth = 2.dp.toPx(),
                )
                drawLine(
                    uiSettings.toColor(if (player == Player.WallOrBoth) Player.Second else player).copy(0.7f),
                    Offset(xPx + baseMarkerSize, yPx - baseMarkerSize),
                    Offset(xPx - baseMarkerSize, yPx + baseMarkerSize),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        updateObject
    }
}

private fun DrawScope.drawStrongConnectionLines(
    field: Field,
    moveResultPosition: Position,
    color: Color
) {
    val connections = field.getStrongConnectionLinePositions(moveResultPosition)
    if (connections.isEmpty()) return

    val dotOffsetPx = with(field) {
         moveResultPosition.toPxOffset(field,this@drawStrongConnectionLines)
    }
    for (connection in connections) {
        val (x, y) = connection.toXY(field.realWidth)
        val connectionXEndPx = (x + when (x) {
            0 -> 1 - outOfBoundDrawRatio
            field.realWidth - 1 -> -(1 - outOfBoundDrawRatio)
            else -> 0f
        }).coordinateToPx(this)

        val connectionYEndPx = (y + when (y) {
            0 -> 1 - outOfBoundDrawRatio
            field.realHeight - 1 -> -(1 - outOfBoundDrawRatio)
            else -> 0f
        }).coordinateToPx(this)

        drawLine(
            color,
            dotOffsetPx,
            Offset(connectionXEndPx, connectionYEndPx),
            connectionThickness.toPx()
        )
    }
}

private fun DrawScope.drawPolygon(
    outerClosure: List<Position>,
    innerClosures: List<List<Position>>,
    player: Player,
    polygonDrawMode: PolygonDrawMode,
    fieldStride: Int,
    uiSettings: UiSettings,
    isGrounding: Boolean = false,
) {
    if (outerClosure.size <= 1) return

    fun createPath(positions: List<Position>): Path {
        return Path().apply {
            // TODO: implement clipping
            for ((index, position = value) in positions.withIndex()) {
                val (x, y) = position.toXY(fieldStride)
                val xCoordinate = x.toFloat().coordinateToPx(this@drawPolygon)
                val yCoordinate = y.toFloat().coordinateToPx(this@drawPolygon)

                if (index == 0) {
                    moveTo(xCoordinate, yCoordinate)
                } else {
                    lineTo(xCoordinate, yCoordinate)
                    if (index == outerClosure.size - 1) {
                        close()
                    }
                }
            }
        }
    }

    val path = createPath(outerClosure)

    val resultPath = if (innerClosures.isEmpty()) {
        path
    } else {
        Path().apply {
            for ((index, innerClosure = value) in innerClosures.withIndex()) {
                op(if (index == 0) path else this, createPath(innerClosure), PathOperation.Difference)
            }
        }
    }

    val outlineColor = uiSettings.toColor(player)
    val fillColor = if (isGrounding) outlineColor else outlineColor.copy(alpha = baseAlpha)

    if (polygonDrawMode.drawFill) {
        drawPath(resultPath, fillColor)
    }
    if (polygonDrawMode.drawOutline || isGrounding) {
        drawPath(
            resultPath,
            outlineColor,
            style = Stroke(width = if (isGrounding) dotRadius.toPx() * 2 else connectionThickness.toPx(),
            join = if (isGrounding) StrokeJoin.Round else StrokeJoin.Miter)
        )
    }
}

@Composable
private fun Pointer(position: Position?, moveMode: MoveMode, field: Field, uiSettings: UiSettings) {
    if (position == null) return

    Canvas(Modifier) {
        drawCircle(
            uiSettings.toColor(moveMode.getMovePlayer(field)).copy(alpha = 0.5f),
            dotRadius.toPx(),
            position.toPxOffset(field,this)
        )
    }
}

private fun PointerEvent.toFieldPositionIfValid(field: Field, currentPlayer: Player, currentDensity: Density): Position? {
    if (field.disabled) return null

    val offset = changes.first().position

    with (currentDensity) {
        val x = round((offset.x.toDp() - fieldPadding) / cellSize).toInt() + Field.OFFSET
        val y = round((offset.y.toDp() - fieldPadding) / cellSize).toInt() + Field.OFFSET

        return field.getPositionIfValid(x, y, currentPlayer)
    }
}

private fun Position.toPxOffset(field: Field, density: Density): Offset {
    val (x, y) = toXY(field.realWidth)
    return Offset(x.coordinateToPx(density), y.coordinateToPx(density))
}

private fun Int.coordinateToPx(density: Density): Float = with (density) { (cellSize * (this@coordinateToPx - Field.OFFSET) + fieldPadding).toPx() }
private fun Float.coordinateToPx(density: Density): Float = with (density) { (cellSize * (this@coordinateToPx - Field.OFFSET) + fieldPadding).toPx() }