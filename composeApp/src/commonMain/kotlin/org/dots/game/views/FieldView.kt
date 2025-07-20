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
import org.dots.game.core.MoveMode
import org.dots.game.core.MoveResult
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.features.getOneMoveCapturingAndBasePositions
import org.dots.game.core.features.getPositionsAtDistance
import org.dots.game.core.features.squareDistances
import org.dots.game.core.getPositionsOfConnection
import org.dots.game.core.getSortedClosurePositions
import org.dots.game.core.getStrongConnectionLinePositions
import org.dots.game.core.squareDistanceTo
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
private val baseDrawMode: PolygonDrawMode = PolygonDrawMode.OutlineAndFill
private val connectionDrawMode: ConnectionDrawMode = ConnectionDrawMode.Polygon(baseDrawMode)
private val drawConnections: Boolean = true
private val minDistanceId = 2
private val maxDistanceId = 2
private val helperMovesMode: HelperMovesMode = HelperMovesMode.CapturingAndBase

enum class HelperMovesMode {
    None,
    Capturing,
    CapturingAndBase,
}

private val capturingMoveMarkerSize = cellSize * 0.35f
private val capturingBaseMoveMarkerSize = cellSize * 0.2f

sealed class ConnectionDrawMode {
    object None : ConnectionDrawMode()
    object Lines : ConnectionDrawMode()
    class Polygon(val polygonDrawMode: PolygonDrawMode) : ConnectionDrawMode()
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

@Composable
fun FieldView(currentMove: MoveResult?, moveMode: MoveMode, fieldViewData: FieldViewData, uiSettings: UiSettings, onMovePlaced: (MoveResult) -> Unit) {
    val field = fieldViewData.field
    val currentDensity = LocalDensity.current
    var pointerFieldPosition: Position? by remember { mutableStateOf(null) }

    Box(
        Modifier
            .size(fieldViewData.fieldSize)
            .pointerInput(moveMode, field) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val currentPlayer = moveMode.getMovePlayer() ?: field.getCurrentPlayer()
                        when (event.type) {
                            PointerEventType.Move -> {
                                pointerFieldPosition = event.toFieldPositionIfValid(field, currentPlayer, currentDensity)
                            }
                            PointerEventType.Press -> {
                                if (event.buttons.isPrimaryPressed) {
                                    val fieldPosition =
                                        event.toFieldPositionIfValid(field, currentPlayer, currentDensity)
                                    if (fieldPosition != null && field.makeMoveUnsafe(fieldPosition, currentPlayer) != null) {
                                        onMovePlaced(field.lastMove!!)
                                        pointerFieldPosition = event.toFieldPositionIfValid(field, currentPlayer, currentDensity)
                                    }
                                }
                            }
                            PointerEventType.Exit -> {
                                pointerFieldPosition = null
                            }
                        }
                    }
                }
            }
    ) {
        Grid(field)
        Moves(currentMove, field, uiSettings)
        if (!field.isGameOver()) {
            if (drawConnections) {
                AllConnections(currentMove, field, uiSettings)
            }
            ThreatsAndSurroundings(currentMove, field, uiSettings)
        }
        Pointer(pointerFieldPosition, moveMode, field, uiSettings)
    }
}

class FieldViewData(val field: Field) {
    val fieldSize = DpSize(
        cellSize * (field.width - 1) + fieldPadding * 2,
        cellSize * (field.height - 1) + fieldPadding * 2,
    )
}

@Composable
private fun Grid(field: Field) {
    val textMeasurer = rememberTextMeasurer()
    with (LocalDensity.current) {
        val fieldPaddingPx = fieldPadding.toPx()
        val verticalLinesEndPx = fieldPaddingPx + (cellSize * (field.height - 1)).toPx()
        val horizontalLinesEndPx = fieldPaddingPx + (cellSize * (field.width - 1)).toPx()
        val linesThicknessPx = linesThickness.toPx()
        val textPaddingPx = (fieldPadding - textPadding).toPx()

        Canvas(Modifier.fillMaxSize().graphicsLayer().background(fieldColor)) {
            for (x in Field.OFFSET until field.width + Field.OFFSET) {
                val xPx = x.coordinateToPx(this)

                val coordinateText = x.toString()
                val textLayoutResult = textMeasurer.measure(coordinateText)

                drawText(
                    textMeasurer,
                    coordinateText,
                    Offset(
                        xPx - textLayoutResult.size.width / 2,
                        textPaddingPx - textLayoutResult.size.height
                    )
                )

                drawLine(linesColor,
                    Offset(xPx, fieldPaddingPx),
                    Offset(xPx, verticalLinesEndPx),
                    linesThicknessPx,
                )
            }

            for (y in Field.OFFSET until field.height + Field.OFFSET) {
                val yPx = y.coordinateToPx(this)

                val coordinateText = y.toString()
                val textLayoutResult = textMeasurer.measure(coordinateText)

                drawText(
                    textMeasurer,
                    coordinateText,
                    Offset(
                        textPaddingPx - textLayoutResult.size.width,
                        yPx - textLayoutResult.size.height / 2
                    )
                )

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
private fun Moves(currentMove: MoveResult?, field: Field, uiSettings: UiSettings) {
    val fieldWithIncrementalUpdate = Field.create(field.rules) // TODO: rewrite without using temp field

    val gameOverMove = field.moveSequence.lastOrNull()?.takeIf { it.position.isGameOverMove() }

    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val dotRadiusPx = dotRadius.toPx()

        for (moveResult in field.moveSequence) {
            fieldWithIncrementalUpdate.makeMove(moveResult.position, moveResult.player)

            val moveResultPosition = moveResult.position.takeUnless { it.isGameOverMove() } ?: continue
            val color = uiSettings.toColor(moveResult.player)

            if (connectionDrawMode == ConnectionDrawMode.Lines) {
                drawStrongConnectionLines(fieldWithIncrementalUpdate, moveResultPosition, color)
            } else if (connectionDrawMode is ConnectionDrawMode.Polygon) {
                val connections = fieldWithIncrementalUpdate.getPositionsOfConnection(moveResultPosition)
                drawPolygon(
                    connections,
                    emptyList(),
                    moveResult.player,
                    connectionDrawMode.polygonDrawMode,
                    uiSettings,
                )
            }

            drawCircle(
                color,
                dotRadiusPx,
                moveResultPosition.toPxOffset(this)
            )

            if (moveResult.bases != null) {
                for (base in moveResult.bases) {
                    if (!base.isReal) continue

                    val (outerClosure, innerClosures) = base.getSortedClosurePositions(fieldWithIncrementalUpdate)
                    drawPolygon(
                        outerClosure,
                        innerClosures,
                        base.player,
                        baseDrawMode,
                        uiSettings,
                    )
                }
            }
        }

        currentMove?.let {
            drawCircle(
                lastMoveColor,
                lastMoveRadius.toPx(),
                it.position.toPxOffset(this)
            )
        }
    }

    if (gameOverMove != null) {
        Canvas(Modifier.fillMaxSize().graphicsLayer().alpha(baseAlpha)) {

            val dotRadiusPx = dotRadius.toPx()

            if (gameOverMove.bases != null) {
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
                            outerClosure.single().toPxOffset(this)
                        )
                    } else {
                        drawPolygon(
                            outerClosure,
                            innerClosures,
                            base.player,
                            baseDrawMode,
                            uiSettings,
                            isGrounding = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllConnections(currentMove: MoveResult?, field: Field, uiSettings: UiSettings) {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        with(field) {
            for (distanceId in minDistanceId..maxDistanceId) {
                val distantPositions = field.getPositionsAtDistance(distanceId).toList()
                val squaredDistance = squareDistances[distanceId]

                val lines = buildSet {
                    for (i in 0 until distantPositions.size) {
                        val startPosition = distantPositions[i]
                        val stateState = startPosition.getState()
                        if (stateState.checkTerritory()) continue

                        val player = stateState.getActivePlayer()
                        for (j in i + 1 until distantPositions.size) {
                            val endPosition = distantPositions[j]

                            if (endPosition.getState().let {
                                !it.checkTerritory() &&
                                it.getActivePlayer() == player
                            } &&
                                startPosition.squareDistanceTo(endPosition) == squaredDistance
                            ) {
                                add(startPosition to endPosition)
                            }
                        }
                    }
                }

                for (line in lines) {
                    val (start, end) = line
                    drawLine(
                        uiSettings.toColor(start.getState().getActivePlayer()),
                        start.toPxOffset(this@Canvas),
                        end.toPxOffset(this@Canvas),
                        strokeWidth = connectionThickness.toPx(),
                        alpha = 0.3f
                    )
                }
            }

            currentMove
        }
    }
}

@Composable
private fun ThreatsAndSurroundings(currentMove: MoveResult?, field: Field, uiSettings: UiSettings) {
    if (helperMovesMode == HelperMovesMode.None) return

    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val (oneMoveCapturingPositions, oneMoveBasePositions) = field.getOneMoveCapturingAndBasePositions()

        val capturingMarkerSize = capturingMoveMarkerSize.toPx()
        oneMoveCapturingPositions.forEach  {
            val (position, player) = it
            val (xPx, yPx) = position.toPxOffset(this)
            drawLine(
                uiSettings.toColor(player).copy(0.7f),
                Offset(xPx - capturingMarkerSize, yPx),
                Offset(xPx + capturingMarkerSize, yPx),
                strokeWidth = 3.dp.toPx(),
            )
            drawLine(
                uiSettings.toColor(if (player == Player.WallOrBoth) player.opposite() else player).copy(0.7f),
                Offset(xPx, yPx - capturingMarkerSize),
                Offset(xPx, yPx + capturingMarkerSize),
                strokeWidth = 3.dp.toPx(),
            )
        }

        if (helperMovesMode == HelperMovesMode.CapturingAndBase) {
            val baseMarkerSize = capturingBaseMoveMarkerSize.toPx()
            oneMoveBasePositions.forEach {
                val (position, player) = it
                val (xPx, yPx) = position.toPxOffset(this)
                drawLine(
                    uiSettings.toColor(player).copy(0.7f),
                    Offset(xPx - baseMarkerSize, yPx - baseMarkerSize),
                    Offset(xPx + baseMarkerSize, yPx + baseMarkerSize),
                    strokeWidth = 2.dp.toPx(),
                )
                drawLine(
                    uiSettings.toColor(if (player == Player.WallOrBoth) player.opposite() else player).copy(0.7f),
                    Offset(xPx + baseMarkerSize, yPx - baseMarkerSize),
                    Offset(xPx - baseMarkerSize, yPx + baseMarkerSize),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        currentMove
    }
}

private fun DrawScope.drawStrongConnectionLines(
    field: Field,
    moveResultPosition: Position,
    color: Color
) {
    val connections = field.getStrongConnectionLinePositions(moveResultPosition)
    if (connections.isEmpty()) return

    val dotOffsetPx = moveResultPosition.toPxOffset(this)
    for (connection in connections) {
        val connectionXEndPx = (connection.x + when (connection.x) {
            0 -> 1 - outOfBoundDrawRatio
            field.realWidth - 1 -> -(1 - outOfBoundDrawRatio)
            else -> 0f
        }).coordinateToPx(this)

        val connectionYEndPx = (connection.y + when (connection.y) {
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
    uiSettings: UiSettings,
    isGrounding: Boolean = false,
) {
    if (outerClosure.size <= 1) return

    fun createPath(positions: List<Position>): Path {
        return Path().apply {
            // TODO: implement clipping
            for ((index, position) in positions.withIndex()) {
                val (x, y) = position
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
            for ((index, innerClosure) in innerClosures.withIndex()) {
                op(if (index == 0) path else this, createPath(innerClosure), PathOperation.Difference)
            }
        }
    }

    val outlineColor = uiSettings.toColor(player)
    val fillColor = if (isGrounding) outlineColor else outlineColor.copy(alpha = baseAlpha)

    if (polygonDrawMode.drawFill) {
        drawPath(resultPath, fillColor)
    }
    if (polygonDrawMode.drawOutline) {
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
            uiSettings.toColor(moveMode.getMovePlayer() ?: field.getCurrentPlayer()).copy(alpha = 0.5f),
            dotRadius.toPx(),
            position.toPxOffset(this)
        )
    }
}

private fun PointerEvent.toFieldPositionIfValid(field: Field, currentPlayer: Player, currentDensity: Density): Position? {
    val offset = changes.first().position

    with (currentDensity) {
        val x = round((offset.x.toDp() - fieldPadding) / cellSize).toInt()
        val y = round((offset.y.toDp() - fieldPadding) / cellSize).toInt()

        return Position(x + Field.OFFSET, y + Field.OFFSET).takeIf {
            !field.isGameOver() && field.checkPositionWithinBounds(it) && field.checkValidMove(it, currentPlayer)
        }
    }
}

private fun Position.toPxOffset(density: Density): Offset {
    return Offset(x.coordinateToPx(density), y.coordinateToPx(density))
}

private fun Int.coordinateToPx(density: Density): Float = with (density) { (cellSize * (this@coordinateToPx - Field.OFFSET) + fieldPadding).toPx() }
private fun Float.coordinateToPx(density: Density): Float = with (density) { (cellSize * (this@coordinateToPx - Field.OFFSET) + fieldPadding).toPx() }