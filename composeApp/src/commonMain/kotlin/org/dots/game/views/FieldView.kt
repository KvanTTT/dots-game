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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dots.game.MoveAnalysis
import org.dots.game.UiSettings
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.LegalMove
import org.dots.game.core.MoveMode
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.PositionXY
import org.dots.game.core.features.getOneMoveCapturingAndBasePositions
import org.dots.game.core.features.getPositionsAtDistance
import org.dots.game.core.features.squareDistances
import org.dots.game.core.getPositionsOfConnection
import org.dots.game.core.getSortedClosurePositions
import org.dots.game.core.getStrongConnectionLinePositions
import org.dots.game.core.squareDistanceTo
import org.dots.game.maxFieldDimension
import org.dots.game.platform
import kotlin.math.abs
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

private val analyzedMoveRadius = cellSize * 0.44f
private val bestAnalyzedMoveThickness = 2.dp

/**
 * The candidate moves are reported for the whole field, but only the most promising ones are highlighted,
 * otherwise a large field turns into an unreadable mess of numbers.
 */
private const val maxAnalyzedMovesToHighlight = 30

private const val maxOwnershipAlpha = 0.5f

/** Below this the ownership is indistinguishable from the model noise, so it's not worth shading. */
private const val minRenderedOwnership = 0.01

/**
 * Above this the ownership of a position is settled, so it's not shaded on top of the filling of the base
 * that already reports the very same owner.
 */
private const val minSettledOwnership = 0.95

private const val hintFractionDigits = 0

/**
 * The values are named the way the engine itself reports them, because the hint exists to check the engine
 * against the field, and a bare percentage tells neither which value it is nor where it comes from.
 */
private const val winRateHintName = "winrate"
private const val ownershipHintName = "ownership"
private val hintTextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
internal val hintDarkBackgroundColor = Color.hsv(0.0f, 0.0f, 0.15f, 0.85f)
internal val hintLightBackgroundColor = Color.hsv(0.0f, 0.0f, 0.97f, 0.85f)
private val hintPadding = 4.dp
private val hintCornerRadius = 3.dp

/** Every value keeps a background of its own, because a single one can't suit any pair of value colors. */
private val hintLineGap = 2.dp

/** Keeps the hint clear of both the hovered dot and the mouse cursor. */
private val hintIndent = cellSize * 0.55f

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
    moveAnalysis: MoveAnalysis? = null,
    onMovePlaced: (Position, Player) -> Unit = { pos, player -> require(field.makeMoveUnsafe(pos, player) is LegalMove) }
) {
    val currentDensity = LocalDensity.current
    var pointerFieldPosition: Position? by remember { mutableStateOf(null) }
    // Tracked separately from [pointerFieldPosition], which only holds the positions a move may be placed on
    var hoveredPositionXY: PositionXY? by remember { mutableStateOf(null) }

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
                                hoveredPositionXY = event.toFieldPositionXY(field, currentDensity)

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
                                hoveredPositionXY = null
                                isPrimaryPressed = false
                            }
                        }
                    }
                }
            }
    ) {
        Grid(field, uiSettings)
        // Drawn before the dots and the bases so that they stay on top of the shading
        if (moveAnalysis != null && uiSettings.showOwnership) {
            AnalyzedOwnership(moveAnalysis, field, uiSettings)
        }
        Moves(updateFieldObject, field, uiSettings)
        if (!field.isGameOver()) {
            if (uiSettings.showDiagonalConnections) {
                AllConnections(updateFieldObject, field, uiSettings)
            }

            if (uiSettings.showThreats || uiSettings.showSurroundings) {
                ThreatsAndSurroundings(updateFieldObject, field, uiSettings)
            }

            if (moveAnalysis != null && uiSettings.showCandidateMoves) {
                AnalyzedMoves(moveAnalysis, uiSettings)
            }
        }
        Pointer(pointerFieldPosition, moveMode, field, uiSettings)

        // The topmost layer, so that the hint is never covered by the field content
        if (moveAnalysis != null) {
            hoveredPositionXY?.let { AnalysisHint(it, moveAnalysis, uiSettings) }
        }
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

                val xCoordinateText = xCoordinateToDisplayString(x, uiSettings.developerMode)
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

                val yCoordinateText = yCoordinateToDisplayString(y, field, uiSettings.developerMode)
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
    val gameOverMove = field.moveSequence.lastOrNull()?.takeIf { it.position.isGameOverMove }

    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val fieldWithIncrementalUpdate = Field.create(field.rules) // TODO: rewrite without using temp field

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
                moveResultPosition.toPxOffset(fieldWithIncrementalUpdate, this)
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
                it.position.toPxOffset(fieldWithIncrementalUpdate, this)
            )
        }

        val _ = updateObject
    }

    if (gameOverMove != null) {
        Canvas(Modifier.fillMaxSize().graphicsLayer().alpha(baseAlpha)) {

            val dotRadiusPx = dotRadius.toPx()

            for (base in gameOverMove.bases) {
                if (!base.isReal) continue

                val (outerClosure, innerClosures) = base.getSortedClosurePositions(
                    field,
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

                for ((start = first, end = second) in lines) {
                    drawLine(
                        uiSettings.toColor(start.getState().getActivePlayer()),
                        start.toPxOffset(field, this@Canvas),
                        end.toPxOffset(field,this@Canvas),
                        strokeWidth = connectionThickness.toPx(),
                        alpha = 0.3f
                    )
                }
            }

            val _ = updateObject
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

        val _ = updateObject
    }
}

/**
 * Shades every position with the color of the player who is expected to capture it, see
 * [MoveAnalysis.ownershipOf]. The stronger the expectation, the more opaque the shading is.
 *
 * The positions that are captured by the very player who is expected to own them are left to the filling of
 * their base, see [minSettledOwnership]. A base the ownership disagrees with is shaded as any other position.
 */
@Composable
private fun AnalyzedOwnership(moveAnalysis: MoveAnalysis, field: Field, uiSettings: UiSettings) {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val cellSizePx = cellSize.toPx()
        val cellOffsetPx = cellSizePx / 2
        val cellArea = Size(cellSizePx, cellSizePx)

        for (x in Field.OFFSET until field.width + Field.OFFSET) {
            val xPx = x.coordinateToPx(this)

            for (y in Field.OFFSET until field.height + Field.OFFSET) {
                val ownership = moveAnalysis.ownershipOf(PositionXY(x, y)) ?: continue
                val absOwnership = abs(ownership)
                if (absOwnership < minRenderedOwnership) continue

                // The ownership is reported for the analyzed player, so a negative value is the opponent's one
                val owner = if (ownership > 0) moveAnalysis.player else moveAnalysis.player.opposite()

                if (uiSettings.baseDrawMode.drawFill) {
                    // The base filling already conveys such a position, and the two colorings on top of each other
                    // are only harder to read; the hint of the position still reports the exact value.
                    // A base captured by the other player is shaded nevertheless, because it's a disagreement worth
                    // seeing rather than a duplicated coloring
                    if (absOwnership >= minSettledOwnership && field.capturedBy(x, y) == owner) continue
                }

                drawRect(
                    uiSettings.toColor(owner).copy(alpha = absOwnership.toFloat() * maxOwnershipAlpha),
                    topLeft = Offset(xPx - cellOffsetPx, y.coordinateToPx(this) - cellOffsetPx),
                    size = cellArea,
                )
            }
        }
    }
}

/**
 * @return the player whose base fills the position according to the [Field] logic,
 * or `null` if the position isn't captured.
 */
private fun Field.capturedBy(x: Int, y: Int): Player? =
    getPositionIfWithinBounds(x, y)?.getState()?.takeIf { it.isTerritory() }?.getActivePlayer()

/** A value of the hovered position, colored with whatever the value refers to. */
private class HintLine(val text: String, val color: Color)

/**
 * Shows the exact values of the position under the cursor, because the field conveys them roughly only:
 * [AnalyzedMoves] encodes how good a candidate move is by a color of a gradient, and the shading of
 * [AnalyzedOwnership] conveys the expected owner with a rough confidence.
 *
 * Unlike the shading, the ownership is reported on the captured positions as well, to tell a correctly
 * calculated ownership from a wrong one there; only the positions the shading skips as too uncertain
 * ([minRenderedOwnership]) get no ownership line either. The ownership is signed the same way
 * [MoveAnalysis.ownershipOf] reports it, and it's colored with the expected owner, so that a negative value
 * doesn't have to be mentally inverted.
 */
@Composable
private fun AnalysisHint(positionXY: PositionXY, moveAnalysis: MoveAnalysis, uiSettings: UiSettings) {
    val lines = buildList {
        if (uiSettings.showCandidateMoves) {
            moveAnalysis.moveAt(positionXY)?.let { move ->
                // The transparency of the highlighting encodes the confidence,
                // but a transparent text is just unreadable
                val color = moveAnalysis.colorOf(move).copy(alpha = 1.0f)
                add(HintLine(winRateHintName + ": " + (move.winRate * 100).toFixed(hintFractionDigits) + "%", color))
            }
        }

        if (uiSettings.showOwnership) {
            val ownership = moveAnalysis.ownershipOf(positionXY)
            // Such a low value conveys no expected owner at all, the same way the shading skips it
            if (ownership != null && abs(ownership) >= minRenderedOwnership) {
                val owner = if (ownership > 0) moveAnalysis.player else moveAnalysis.player.opposite()
                val text = ownershipHintName + ": " + (ownership * 100).toFixed(hintFractionDigits) + "%"
                add(HintLine(text, uiSettings.toColor(owner)))
            }
        }
    }
    if (lines.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val paddingPx = hintPadding.toPx()
        val gapPx = hintLineGap.toPx()
        val cornerRadius = CornerRadius(hintCornerRadius.toPx())

        val textLayouts = lines.map { textMeasurer.measure(it.text, hintTextStyle) }
        val lineHeights = textLayouts.map { it.size.height + paddingPx * 2 }
        val hintWidth = textLayouts.maxOf { it.size.width } + paddingPx * 2
        val hintHeight = lineHeights.sum() + gapPx * (lines.size - 1)

        // Above and to the right of the position, but never past the field bounds, otherwise it gets clipped
        val indentPx = hintIndent.toPx()
        val left = (positionXY.x.coordinateToPx(this) + indentPx).coerceIn(0f, (size.width - hintWidth).coerceAtLeast(0f))
        var top = (positionXY.y.coordinateToPx(this) - indentPx - hintHeight).coerceAtLeast(0f)

        for (index in lines.indices) {
            val line = lines[index]

            drawRoundRect(
                mostContrastingHintBackground(line.color),
                topLeft = Offset(left, top),
                size = Size(hintWidth, lineHeights[index]),
                cornerRadius = cornerRadius,
            )

            drawText(
                textLayouts[index],
                color = line.color,
                topLeft = Offset(left + paddingPx, top + paddingPx),
            )

            top += lineHeights[index] + gapPx
        }
    }
}

/**
 * The player colors are configurable, and no single background suits every one of them: the default
 * blue of the first player is barely readable on a dark background, while the default red of
 * the second one is more readable on a dark background than on a light one.
 *
 * @return the background that gives [textColor] the higher [contrastRatio].
 */
internal fun mostContrastingHintBackground(textColor: Color): Color {
    return if (contrastRatio(textColor, hintDarkBackgroundColor) >=
        contrastRatio(textColor, hintLightBackgroundColor)
    ) {
        hintDarkBackgroundColor
    } else {
        hintLightBackgroundColor
    }
}

/**
 * The [WCAG contrast ratio](https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio) of two colors,
 * from `1.0` (indistinguishable) to `21.0` (black against white).
 */
internal fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()

    return (maxOf(firstLuminance, secondLuminance) + 0.05f) / (minOf(firstLuminance, secondLuminance) + 0.05f)
}

/**
 * Highlights the candidate moves of [moveAnalysis]: the color encodes how good a move is and the transparency
 * encodes how reliable its evaluation is, see [colorOf]. The exact win rate is reported by [AnalysisHint],
 * because the numbers of the neighboring moves are unreadable on a dense field.
 *
 * The best move is circled with the color of the player it's analyzed for: it stands out from both the
 * highlighting of the move and the [fieldColor] background, and it tells whose move is being suggested.
 */
@Composable
private fun AnalyzedMoves(moveAnalysis: MoveAnalysis, uiSettings: UiSettings) {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val radiusPx = analyzedMoveRadius.toPx()
        val bestMoveColor = uiSettings.toColor(moveAnalysis.player)

        for (move in moveAnalysis.moves.take(maxAnalyzedMovesToHighlight)) {
            val center = Offset(
                move.positionXY.x.coordinateToPx(this),
                move.positionXY.y.coordinateToPx(this),
            )

            drawCircle(moveAnalysis.colorOf(move), radiusPx, center)

            if (move === moveAnalysis.best) {
                drawCircle(
                    bestMoveColor,
                    radiusPx,
                    center,
                    style = Stroke(width = bestAnalyzedMoveThickness.toPx()),
                )
            }
        }
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

/**
 * Unlike [toFieldPositionIfValid], every position of the field is returned, an occupied one included,
 * because the ownership is reported for all of them, and the captured dots are the most interesting ones.
 */
private fun PointerEvent.toFieldPositionXY(field: Field, currentDensity: Density): PositionXY? {
    val offset = changes.first().position

    with (currentDensity) {
        val x = round((offset.x.toDp() - fieldPadding) / cellSize).toInt() + Field.OFFSET
        val y = round((offset.y.toDp() - fieldPadding) / cellSize).toInt() + Field.OFFSET

        return if (x in Field.OFFSET until field.width + Field.OFFSET && y in Field.OFFSET until field.height + Field.OFFSET) {
            PositionXY(x, y)
        } else {
            null
        }
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