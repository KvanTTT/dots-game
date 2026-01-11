package org.dots.game.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.AppType
import org.dots.game.core.GameTreeNode
import org.dots.game.localization.Strings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.reflect.KProperty1

private val barColor = Color(0, 0, 0, 150)
private val valueFontSize = 14.sp

private sealed class GraphProperties<T : Number>(
    val color: Color,
    val graphPointProperty: KProperty1<GraphPointData, T?>,
    val titleProperty: KProperty1<Strings, String>,
    val descriptionProperty: KProperty1<Strings, String>?,
    val uiSettingsProperty: KProperty1<UiSettings, Boolean>,
    val renderValue: (T) -> String = { it.toString() },
) {
    fun getLabel(strings: Strings): String {
        return titleProperty.get(strings)
    }

    fun getDescription(strings: Strings): String? {
        return descriptionProperty?.get(strings)
    }

    fun getEnabled(uiSettings: UiSettings): Boolean {
        return uiSettingsProperty.get(uiSettings)
    }

    fun getValue(graphPointData: GraphPointData): T? {
        return graphPointProperty.get(graphPointData)
    }

    object WinRate : GraphProperties<Float>(
        Color(27, 204, 27, 255),
        GraphPointData::winRate,
        Strings::winRate,
        Strings::winRateDescription,
        UiSettings::showWinRateGraph,
        renderValue = { "${round(it * 100).toInt()}%" }
    )

    object Score : GraphProperties<Float>(
        Color(57, 141, 179, 255),
        GraphPointData::score,
        Strings::score,
        Strings::scoreDescription,
        UiSettings::showScoreGraph,
        renderValue = { "${if (it > 0) "+" else ""}$it" },
    )

    object Weight : GraphProperties<Float>(
        Color(191, 98, 0, 255),
        GraphPointData::weight,
        Strings::weight,
        Strings::weightDescription,
        UiSettings::showWeightGraph,
    )

    object Visits : GraphProperties<Int>(
        Color(128, 128, 128, 255),
        GraphPointData::visits,
        Strings::visits,
        null,
        UiSettings::showVisitsGraph,
    )
}

private val allGraphProperties = listOf(
    GraphProperties.WinRate,
    GraphProperties.Score,
    GraphProperties.Weight,
    GraphProperties.Visits,
)

@Composable
fun GameTreeGraphsView(
    currentNode: GameTreeNode?,
    gameTreeViewData: GameTreeViewData,
    uiSettings: UiSettings,
    onUiSettingsChange: (UiSettings) -> Unit,
    onChangeCurrentNode: () -> Unit,
) {
    val gameTree = gameTreeViewData.gameTree
    if (gameTree.game?.appInfo?.appType != AppType.Katago) return

    val strings = uiSettings.language.getStrings()
    val textMeasurer = rememberTextMeasurer()

    val graphData = remember(gameTreeViewData) {
        val graphPointData = mutableListOf<GraphPointData>()

        var node: GameTreeNode? = gameTree.rootNode
        while (node != null) {
            node.getKataGoGraphPointData()?.let { graphPointData.add(it) }
            node = node.children.firstOrNull { it.mainBranch }
        }
        GraphData(graphPointData)
    }

    // Exit if there are no points to prevent division by zero and because there is nothing useful to draw.
    if (!graphData.hasAnyComment || graphData.points.isEmpty()) return
    val coef = 1.0f / (graphData.points.size - 1).let { if (it == 0) 1f else it.toFloat() }

    val density = LocalDensity.current
    val height = 200.dp
    val heightPx = with(density) { height.toPx() }
    val valueTextIncrementPx = with(density) { 15.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Composable
            fun <T : Number> DataCheckbox(graphProperties: GraphProperties<T>) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tooltip(graphProperties.getDescription(strings)) {
                        Text(
                            text = graphProperties.getLabel(strings),
                            color = graphProperties.color,
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Checkbox(
                        checked = graphProperties.getEnabled(uiSettings),
                        onCheckedChange = {
                            onUiSettingsChange(
                                when (graphProperties) {
                                    is GraphProperties.WinRate -> uiSettings.copy(showWinRateGraph = it)
                                    is GraphProperties.Score -> uiSettings.copy(showScoreGraph = it)
                                    is GraphProperties.Weight -> uiSettings.copy(showWeightGraph = it)
                                    is GraphProperties.Visits -> uiSettings.copy(showVisitsGraph = it)
                                }
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.weight(1f))
            }

            allGraphProperties.forEach { DataCheckbox(it) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(height)
                .pointerInput(graphData) {
                    detectTapGestures { offset ->
                        val index = round(offset.x / size.width * (graphData.points.size - 1))
                            .toInt().coerceIn(0, graphData.points.size - 1)
                        gameTree.switch(graphData.points[index].node)
                        onChangeCurrentNode()
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width

                val winRatePath = Path()
                val scorePath = Path()

                val maxAbsScoreCoef by lazy(LazyThreadSafetyMode.NONE) {
                    1.0f / graphData.maxAbsScore.let { if (it == 0.0f) 1.0f else it } * 0.5f
                }
                val maxWeightCoef by lazy(LazyThreadSafetyMode.NONE) {
                    1.0f / graphData.maxWeight.let { if (it == 0.0f) 1.0f else it }
                }
                val maxVisitsCoef by lazy(LazyThreadSafetyMode.NONE) {
                    1.0f / graphData.maxVisits.let { if (it == 0) 1.0f else it.toFloat() }
                }

                val widthCoef = width * coef
                graphData.points.forEachIndexed { index, graphPointData ->
                    val (_ = node, winRate, score, visits, weight) = graphPointData
                    val x = index * widthCoef

                    if (uiSettings.showWinRateGraph) {
                        val yWinScore =
                            heightPx * (1f - (winRate ?: 0.5f)) // 1.0 is top (second wins), 0.0 is bottom (first wins)
                        if (index == 0) {
                            winRatePath.moveTo(x, yWinScore)
                        } else {
                            winRatePath.lineTo(x, yWinScore)
                        }
                    }

                    if (uiSettings.showScoreGraph) {
                        val yScore = heightPx * (1.0f - ((score ?: 0.0f) * maxAbsScoreCoef + 0.5f))

                        if (index == 0) {
                            scorePath.moveTo(x, yScore)
                        } else {
                            scorePath.lineTo(x, yScore)
                        }
                    }

                    if (uiSettings.showWeightGraph) {
                        val yWeight = weight?.let { heightPx * (1.0f - it * maxWeightCoef) }
                        yWeight?.let {
                            drawCircle(GraphProperties.Weight.color, radius = 2.0f, center = Offset(x, it))
                        }
                    }

                    if (uiSettings.showVisitsGraph) {
                        val yVisits = visits?.let { heightPx * (1.0f - it.toFloat() * maxVisitsCoef) }
                        yVisits?.let {
                            drawCircle(GraphProperties.Visits.color, radius = 2.0f, center = Offset(x, it))
                        }
                    }
                }

                if (uiSettings.showWinRateGraph) {
                    drawPath(winRatePath, GraphProperties.WinRate.color, style = Stroke(width = 2f))
                }
                if (uiSettings.showScoreGraph) {
                    drawPath(scorePath, GraphProperties.Score.color, style = Stroke(width = 2f))
                }

                val zeroY = heightPx * 0.5f
                drawLine(
                    Color.Gray,
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1f
                )
            }

            if (uiSettings.showWinRateGraph) {
                Text(
                    GraphProperties.WinRate.renderValue(1.0f),
                    color = GraphProperties.WinRate.color,
                    fontSize = valueFontSize,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    GraphProperties.WinRate.renderValue(0.0f),
                    color = GraphProperties.WinRate.color,
                    fontSize = valueFontSize,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            if (uiSettings.showScoreGraph) {
                Text(
                    GraphProperties.Score.renderValue(graphData.maxScore),
                    color = GraphProperties.Score.color,
                    fontSize = valueFontSize,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
                Text(
                    GraphProperties.Score.renderValue(graphData.minScore),
                    color = GraphProperties.Score.color,
                    fontSize = valueFontSize,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            // Vertical bar synchronized with current node
            val currentIndex = graphData.points.indexOfFirst { it.node == currentNode }

            if (currentIndex != -1 && currentIndex < graphData.points.size) {
                val ratio = currentIndex.toFloat() * coef
                Canvas(modifier = Modifier.matchParentSize().graphicsLayer {
                    translationX = round(ratio * size.width)
                }) {
                    drawLine(
                        barColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, heightPx),
                        strokeWidth = 1f,
                    )

                    val graphPointData = graphData.points[currentIndex]
                    val invert = when {
                        graphPointData.winRate != null -> graphPointData.winRate > 0.5f
                        graphPointData.score != null -> graphPointData.score > 0.0f
                        else -> false
                    }

                    val numberOfTextsToDraw = allGraphProperties.count { it.getEnabled(uiSettings) }

                    val xOffset = if (ratio < 0.85f) 5.0f else -5.0f - 50.0f
                    var yOffset = if (!invert) {
                        0.0f
                    } else {
                        heightPx - numberOfTextsToDraw * valueTextIncrementPx - 5.0f
                    }

                    fun <T : Number> drawValueIfNeeded(graphProperties: GraphProperties<T>) {
                        if (!graphProperties.getEnabled(uiSettings)) return

                        graphProperties.getValue(graphPointData)?.let {
                            drawText(
                                textMeasurer,
                                graphProperties.renderValue(it),
                                topLeft = Offset(xOffset, yOffset),
                                style = TextStyle(fontSize = valueFontSize, color = graphProperties.color)
                            )
                            yOffset += valueTextIncrementPx
                        }
                    }

                    allGraphProperties.forEach { drawValueIfNeeded(it) }
                }
            }
        }
    }
}

/**
 * Extracts KataGo self-play graph point data from a given GameTreeNode if available.
 * If the node or required data is null, the function returns null.
 *
 * @return A GraphPointData object containing the node, win rate, score, visit count,
 * and weight if all values are successfully parsed; otherwise, null.
 */
private fun GameTreeNode?.getKataGoGraphPointData(): GraphPointData? {
    if (this == null) return null

    var winRate: Float? = null
    var score: Float? = null
    var visits: Int? = null
    var weight: Float? = null

    val comment = this.comment
    if (comment != null) {
        val parts = comment.split(" ")
        val visitsValueIndex = parts.indexOfFirst { it.startsWith("v=") }

        val winValueIndex: Int
        val scoreValueIndex: Int
        when (visitsValueIndex) {
            4 -> {
                winValueIndex = 0
                scoreValueIndex = 3
            }
            2 -> {
                // Support for https://github.com/KvanTTT/KataGoDots/issues/30 (dropped `loss` and `noResult` in a compact format)
                winValueIndex = 0
                scoreValueIndex = 1
            }
            else -> {
                winValueIndex = -1
                scoreValueIndex = -1
            }
        }

        winRate = parts.elementAtOrNull(winValueIndex)?.toFloatOrNull()
        score = parts.elementAtOrNull(scoreValueIndex)?.toFloatOrNull()
        visits = parts.elementAtOrNull(visitsValueIndex)?.substringAfter('=')?.toIntOrNull()?.takeIf { it > 0 }
        weight = parts.elementAtOrNull(visitsValueIndex + 1)?.substringAfter('=')?.toFloatOrNull()?.takeIf { it > 0.0f }
    }

    return if (winRate != null || score != null || visits != null || weight != null) {
        GraphPointData(this, winRate, score, visits, weight)
    } else {
        null
    }
}

private data class GraphPointData(val node: GameTreeNode, val winRate: Float?, val score: Float?, val visits: Int?, val weight: Float?)

private data class GraphData(val points: List<GraphPointData>) {
    val hasAnyComment: Boolean = points.any { it.winRate != null || it.score != null || it.visits != null || it.weight != null }

    val minScore: Float by lazy(LazyThreadSafetyMode.PUBLICATION) { points.minOf { it.score ?: 0.0f } }
    val maxScore: Float by lazy(LazyThreadSafetyMode.PUBLICATION) { points.maxOf { it.score ?: 0.0f } }
    val maxAbsScore: Float by lazy(LazyThreadSafetyMode.PUBLICATION) { max(abs(minScore), abs(maxScore)) }

    val maxVisits: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { points.maxOf { it.visits ?: 0 } }
    val maxWeight: Float by lazy(LazyThreadSafetyMode.PUBLICATION) { points.maxOf { it.weight ?: 0.0f } }
}