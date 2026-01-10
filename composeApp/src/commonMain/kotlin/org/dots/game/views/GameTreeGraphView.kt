package org.dots.game.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.AppType
import org.dots.game.core.GameTreeNode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

private val scoreColor = Color(57, 141, 179, 255)
private val winRateColor = Color(27, 204, 27, 255)
private val weightColor = Color(191, 98, 0, 255)
private val barColor = Color(0, 0, 0, 150)

@Composable
fun GameTreeGraphView(
    currentNode: GameTreeNode?,
    gameTreeViewData: GameTreeViewData,
    uiSettings: UiSettings,
    onChangeCurrentNode: () -> Unit,
) {
    val gameTree = gameTreeViewData.gameTree
    if (gameTree.game?.appInfo?.appType != AppType.Katago) return

    val strings by remember { mutableStateOf(uiSettings.language.getStrings()) }

    val graphData = remember(gameTreeViewData) {
        val graphPointData = mutableListOf<GraphPointData>()
        val nodes = mutableListOf<GameTreeNode>()

        var node: GameTreeNode? = gameTree.rootNode
        while (node != null) {
            nodes.add(node)
            graphPointData.add(node.getGraphPointData()!!)
            node = node.children.firstOrNull { it.mainBranch }
        }
        GraphData(graphPointData)
    }

    val currentGraphPointData = remember(currentNode) {
        currentNode.getGraphPointData()
    }

    if (!graphData.hasAnyComment || graphData.points.isEmpty()) return

    val density = LocalDensity.current
    val height = 200.dp
    val heightPx = with(density) { height.toPx() }

    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tooltip(strings.winRateDescription) {
                    Text(
                        text = strings.winRate,
                        color = winRateColor,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
                currentGraphPointData?.winRate?.let { winRate ->
                    Text(
                        text = " ${round(winRate * 100).toInt()}%",
                        color = winRateColor,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                currentGraphPointData?.score?.let { score ->
                    Text(
                        text = "${if (score > 0) "+" else ""}$score ",
                        color = scoreColor,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
                Tooltip(strings.scoreDescription) {
                    Text(
                        text = strings.score,
                        color = scoreColor,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(graphData) {
                    detectTapGestures { offset ->
                        val index = round(offset.x / size.width * (graphData.points.size - 1)).toInt()
                        gameTree.switch(graphData.points[index].node)
                        onChangeCurrentNode()
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val step = width / (graphData.points.size - 1)

                val winRatePath = Path()
                val scorePath = Path()

                graphData.points.forEachIndexed { index, graphPointData ->
                    val (_ = node, winRate, score, visits, weight) = graphPointData
                    val x = index * step
                    val yWinScore =
                        heightPx * (1f - (winRate ?: 0.5f)) // 1.0 is top (second wins), 0.0 is bottom (first wins)
                    val yScore = heightPx * (1.0f - ((score ?: 0.0f) / graphData.maxAbsScore / 2 + 0.5f))
                    val yVisits = visits?.let { heightPx * (1.0f - it / graphData.maxVisits) }
                    val yWeight = weight.takeIf { it != 0.0f }?.let { heightPx * (1.0f - it / graphData.maxWeight) }

                    if (index == 0) {
                        winRatePath.moveTo(x, yWinScore)
                        scorePath.moveTo(x, yScore)
                    } else {
                        winRatePath.lineTo(x, yWinScore)
                        scorePath.lineTo(x, yScore)
                    }

                    yWeight?.let {
                        drawCircle(weightColor, radius = 2.0f, center = Offset(x, it))
                    }
                }

                drawPath(winRatePath, winRateColor, style = Stroke(width = 2f))
                drawPath(scorePath, scoreColor, style = Stroke(width = 2f))

                val zeroY = heightPx * 0.5f
                drawLine(
                    Color.Gray,
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1f
                )
            }

            Text(
                "100%",
                color = winRateColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                "0%",
                color = winRateColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            Text(
                "${graphData.maxScore}",
                color = scoreColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Text(
                "${graphData.minScore}",
                color = scoreColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            // Vertical bar synchronized with current node
            val currentIndex = graphData.points.indexOfFirst { it.node == currentNode }

            if (currentIndex != -1 && currentIndex < graphData.points.size) {
                Canvas(modifier = Modifier.matchParentSize().graphicsLayer {
                    translationX = round(currentIndex.toFloat() / (graphData.points.size - 1) * size.width)
                }) {
                    drawLine(
                        barColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, heightPx),
                        strokeWidth = 1f
                    )
                }
            }
        }
    }
}

private fun GameTreeNode?.getGraphPointData(): GraphPointData? {
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
                winValueIndex = 0
                scoreValueIndex = 1 // Drop loss and no result in a compact format
            }
            else -> {
                winValueIndex = -1
                scoreValueIndex = -1
            }
        }

        winRate = parts.elementAtOrNull(winValueIndex)?.toFloatOrNull()
        score = parts.elementAtOrNull(scoreValueIndex)?.toFloatOrNull()
        visits = parts.elementAtOrNull(visitsValueIndex)?.substringAfter('=')?.toIntOrNull()
        weight = parts.elementAtOrNull(visitsValueIndex + 1)?.substringAfter('=')?.toFloatOrNull()
    }

    return GraphPointData(this, winRate, score, visits, weight)
}

private data class GraphPointData(val node: GameTreeNode, val winRate: Float?, val score: Float?, val visits: Int?, val weight: Float?)

private data class GraphData(val points: List<GraphPointData>) {
    val hasAnyComment: Boolean = points.any { it.winRate != null || it.score != null}

    val minScore: Float = points.minOf { it.score ?: 0.0f }
    val maxScore: Float = points.maxOf { it.score ?: 0.0f }
    val maxAbsScore: Float = max(abs(minScore), abs(maxScore))

    val maxVisits: Int = points.maxOf { it.visits ?: 0 }
    val maxWeight: Float = points.maxOf { it.weight ?: 0.0f }
}