package org.dots.game.views

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.dots.game.HorizontalScrollbar
import org.dots.game.UiSettings
import org.dots.game.VerticalScrollbar
import org.dots.game.core.*

private val stepSize = 40.dp
private val nodeRatio = 0.33f
private val rootNodeColor = Color.LightGray
private val lineColor = Color(0f, 0f, 0f, 0.8f)
private val textColor: Color = Color.White
private val lineThickness = 1.dp

private val nodeRadius = stepSize * nodeRatio
private val nodeSize = nodeRadius * 2

private val fieldHistoryViewWidth = 300.dp
private val fieldHistoryViewHeight = 200.dp
private val scrollbarSize = 10.dp

private val horizontalLineModifier = Modifier
    .size(stepSize, lineThickness)
    .background(lineColor)
    .zIndex(0f)

private val verticalLineModifier = Modifier
    .size(lineThickness, stepSize)
    .background(lineColor)
    .zIndex( 0f)

private val nodeModifier = Modifier
    .size(nodeSize, nodeSize)
    .zIndex(1f)

private val selectedNodeModifier = Modifier
    .size(nodeSize, nodeSize)
    .border(lineThickness, Color.Black)
    .zIndex( 2f)

@Composable
fun FieldHistoryView(
    currentNode: Node?,
    fieldHistory: FieldHistory,
    fieldHistoryViewData: FieldHistoryViewData,
    uiSettings: UiSettings,
    onChangeCurrentNode: () -> Unit
) {
    val requester = remember { FocusRequester() }

    Box(
        Modifier
            .padding(top = 10.dp)
            .size(fieldHistoryViewWidth, fieldHistoryViewHeight)
            .focusRequester(requester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handleKeyEvent(keyEvent, fieldHistory).also {
                    if (it) {
                        onChangeCurrentNode()
                    }
                }
            }
    ) {
        val horizontalScrollState = rememberScrollState()
        val verticalScrollState = rememberScrollState()
        Box(Modifier
            .horizontalScroll(horizontalScrollState)
            .verticalScroll(verticalScrollState)
            .padding(end = scrollbarSize, bottom = scrollbarSize)
            .size(fieldHistoryViewData.size)
        ) {
            ConnectionsAndNodes(
                fieldHistory,
                fieldHistoryViewData.fieldHistoryElements,
                onChangeCurrentNode,
                uiSettings
            )
            CurrentNode(currentNode, fieldHistoryViewData)
        }

        HorizontalScrollbar(horizontalScrollState, Modifier
            .align(Alignment.BottomStart)
            .size(fieldHistoryViewWidth - scrollbarSize, scrollbarSize)
        )
        VerticalScrollbar(verticalScrollState, Modifier
            .align(Alignment.TopEnd)
            .size(scrollbarSize, fieldHistoryViewHeight - scrollbarSize)
        )
    }
    LaunchedEffect(fieldHistory) {
        requester.requestFocus()
    }
}

class FieldHistoryViewData(fieldHistory: FieldHistory) {
    val fieldHistoryElements: FieldHistoryElements = fieldHistory.getHistoryElements(mainBranchIsAlwaysStraight = true)

    val size: DpSize = DpSize(
        stepSize * (fieldHistoryElements.size - 1) + nodeSize,
        stepSize * (fieldHistoryElements.maxOf { yLine -> yLine.size } - 1) + nodeSize
    )

    val nodeToIndexMap: Map<Node, Pair<Int, Int>> by lazy {
        buildMap {
            for (xIndex in fieldHistoryElements.indices) {
                for ((yIndex, element) in fieldHistoryElements[xIndex].withIndex()) {
                    val node = (element as? NodeHistoryElement)?.node ?: continue
                    this[node] = xIndex to yIndex
                }
            }
        }
    }
}

@Composable
private fun ConnectionsAndNodes(
    fieldHistory: FieldHistory,
    fieldHistoryElements: FieldHistoryElements,
    onChangeCurrentNode: () -> Unit,
    uiSettings: UiSettings
) {
    for (xIndex in fieldHistoryElements.indices) {
        val offsetX = stepSize * xIndex

        for ((yIndex, element) in fieldHistoryElements[xIndex].withIndex()) {
            val offsetY = stepSize * yIndex

            when (element) {
                is NodeHistoryElement -> {
                    val node = element.node

                    val color: Color
                    val moveNumber: Int
                    if (node.isRoot) {
                        color = rootNodeColor
                        moveNumber = 0
                    } else {
                        color = uiSettings.toColor(node.moveResult!!.player)
                        moveNumber = node.number

                        // Render horizontal connection line
                        Box(
                            Modifier.offset(
                                stepSize * (xIndex - 1) + nodeRadius,
                                offsetY - lineThickness / 2 + nodeRadius
                            ).then(horizontalLineModifier)
                        )
                    }

                    // Render node
                    Box(
                        Modifier
                            .offset(offsetX, offsetY)
                            .then(nodeModifier)
                            .background(color, CircleShape)
                            .pointerInput(fieldHistory) {
                                detectTapGestures(onPress = {
                                    if (fieldHistory.switch(node)) {
                                        onChangeCurrentNode()
                                    }
                                })
                            }
                    ) {
                        Text(moveNumber.toString(), Modifier.align(Alignment.Center), textColor)
                    }
                }

                is VerticalLineHistoryElement -> {
                    // Render vertical connection line
                    Box(
                        Modifier
                            .offset(stepSize * xIndex - lineThickness / 2 + nodeRadius, offsetY - stepSize + nodeRadius)
                            .then(verticalLineModifier)
                    )
                }

                is EmptyHistoryElement -> {}
            }
        }
    }
}

@Composable
private fun CurrentNode(currentNode: Node?, fieldHistoryViewData: FieldHistoryViewData) {
    if (currentNode == null) return
    val (xIndex, yIndex) = fieldHistoryViewData.nodeToIndexMap.getValue(currentNode)

    Box(
        Modifier.offset(stepSize * xIndex, stepSize * yIndex).then(selectedNodeModifier)
    )
}

private fun handleKeyEvent(keyEvent: KeyEvent, fieldHistory: FieldHistory): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown) {
        if (keyEvent.key == Key.DirectionLeft) {
            return fieldHistory.back()
        } else if (keyEvent.key == Key.DirectionRight) {
            return fieldHistory.next()
        }
    }

    return false
}