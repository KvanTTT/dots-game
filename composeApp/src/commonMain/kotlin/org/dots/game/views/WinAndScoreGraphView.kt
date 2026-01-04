package org.dots.game.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dots.game.UiSettings
import org.dots.game.core.GameTree
import org.dots.game.core.GameTreeNode
import kotlin.math.max
import kotlin.math.min

private val scoreColor = Color(57, 141, 179, 255)
private val winRateColor = Color(27, 204, 27, 255)
private val barColor = Color(101, 76, 0, 255)

@Composable
fun WinAndScoreGraphView(
    currentNode: GameTreeNode?,
    gameTree: GameTree,
    uiSettings: UiSettings,
) {
    val data = remember(gameTree) {
        val winPoints = mutableListOf<Float>()
        val scorePoints = mutableListOf<Float>()
        var minScore = 0f
        var maxScore = 0f
        var hasAnyComment = false

        var node: GameTreeNode? = gameTree.rootNode
        while (node != null) {
            val comment = node.comment
            if (comment != null) {
                val parts = comment.split(" ")
                if (parts.size >= 4) {
                    val win = parts[0].toFloatOrNull()
                    val score = parts[3].toFloatOrNull()
                    if (win != null && score != null) {
                        winPoints.add(win)
                        scorePoints.add(score)
                        minScore = min(minScore, score)
                        maxScore = max(maxScore, score)
                        hasAnyComment = true
                    } else {
                        // Keep the last value or 0 if we don't have one yet
                        winPoints.add(winPoints.lastOrNull() ?: 0.5f)
                        scorePoints.add(scorePoints.lastOrNull() ?: 0f)
                    }
                } else {
                    winPoints.add(winPoints.lastOrNull() ?: 0.5f)
                    scorePoints.add(scorePoints.lastOrNull() ?: 0f)
                }
            } else {
                winPoints.add(winPoints.lastOrNull() ?: 0.5f)
                scorePoints.add(scorePoints.lastOrNull() ?: 0f)
            }
            node = node.children.firstOrNull { it.mainBranch }
        }
        GraphData(winPoints, scorePoints, minScore, maxScore, hasAnyComment)
    }

    val currentWinAndScore = remember(currentNode) {
        val comment = currentNode?.comment
        if (comment != null) {
            val parts = comment.split(" ")
            if (parts.size >= 4) {
                val win = parts[0].toFloatOrNull()
                val score = parts[3].toFloatOrNull()
                if (win != null && score != null) {
                    return@remember win to score
                }
            }
        }
        null
    }

    if (!data.hasAnyComment || data.winPoints.size < 2) return

    val density = LocalDensity.current
    val height = 100.dp
    val heightPx = with(density) { height.toPx() }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Win Rate (P2)",
                    color = winRateColor,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold
                )
                if (currentWinAndScore != null) {
                    val win = currentWinAndScore.first
                    Text(
                        text = " ${((win * 1000).toInt() / 10f)}%",
                        color = winRateColor, //if (win > 0.5f) uiSettings.playerSecondColor else uiSettings.playerFirstColor,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentWinAndScore != null) {
                    val score = currentWinAndScore.second
                    Text(
                        text = "${if (score > 0) "+" else ""}${((score * 10).toInt() / 10f)} ",
                        color = scoreColor, //if (score > 0) uiSettings.playerSecondColor else uiSettings.playerFirstColor,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Score (P2)",
                    color = scoreColor,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val step = width / (data.winPoints.size - 1).coerceAtLeast(1)

                // Draw Win Graph (0.0 - 1.0)
                val winPath = Path()
                data.winPoints.forEachIndexed { index, win ->
                    val x = index * step
                    val y = heightPx * (1f - win) // 1.0 is top (white wins), 0.0 is bottom (black wins)
                    if (index == 0) winPath.moveTo(x, y) else winPath.lineTo(x, y)
                }
                drawPath(winPath, winRateColor, style = Stroke(width = 2f))

                // Draw Score Graph (scaled)
                val scorePath = Path()
                val scoreRange = (data.maxScore - data.minScore).coerceAtLeast(1f)
                data.scorePoints.forEachIndexed { index, score ->
                    val x = index * step
                    val y = heightPx * (1f - (score - data.minScore) / scoreRange)
                    if (index == 0) scorePath.moveTo(x, y) else scorePath.lineTo(x, y)
                }
                drawPath(scorePath, scoreColor, style = Stroke(width = 2f))

                // Draw zero score line if it's within range
                if (data.minScore <= 0f && data.maxScore >= 0f) {
                    val zeroY = heightPx * (1f - (0f - data.minScore) / scoreRange)
                    drawLine(
                        Color.Gray,
                        start = androidx.compose.ui.geometry.Offset(0f, zeroY),
                        end = androidx.compose.ui.geometry.Offset(width, zeroY),
                        strokeWidth = 1f
                    )
                }
            }

            // Win Rate min/max
            Text(
                "1.0",
                color = uiSettings.playerSecondColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                "0.0",
                color = uiSettings.playerFirstColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            // Score min/max
            Text(
                "${data.maxScore}",
                color = uiSettings.playerSecondColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Text(
                "${data.minScore}",
                color = uiSettings.playerFirstColor,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            // Vertical bar synchronized with current node
            val currentIndex = remember(currentNode, gameTree) {
                var index = 0
                var node: GameTreeNode? = gameTree.rootNode
                var found = false
                while (node != null) {
                    if (node == currentNode) {
                        found = true
                        break
                    }
                    if (node.mainBranch) {
                        index++
                    }
                    node = node.children.firstOrNull { it.mainBranch }
                }
                if (found) index else -1
            }

            if (currentIndex != -1 && currentIndex < data.winPoints.size) {
                Canvas(modifier = Modifier.matchParentSize().graphicsLayer {
                    val width = size.width
                    val step = width / (data.winPoints.size - 1).coerceAtLeast(1)
                    translationX = currentIndex * step
                }) {
                    drawLine(
                        barColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, heightPx),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

private data class GraphData(
    val winPoints: List<Float>,
    val scorePoints: List<Float>,
    val minScore: Float,
    val maxScore: Float,
    val hasAnyComment: Boolean,
)