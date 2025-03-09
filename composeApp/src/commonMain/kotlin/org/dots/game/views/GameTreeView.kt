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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.dots.game.HorizontalScrollbar
import org.dots.game.UiSettings
import org.dots.game.VerticalScrollbar
import org.dots.game.core.*
import kotlin.math.round

private val stepSize = 40.dp
private val nodeRadius = 15.dp
private val rootNodeColor = Color.LightGray
private val lineColor = Color(0f, 0f, 0f, 0.8f)
private val textColor: Color = Color.White
private val lineThickness = 1.dp
private val selectedNodeVisibleRange = stepSize * 1.5f

private val nodeSize = nodeRadius * 2

private val gameTreeViewWidth = 350.dp
private val gameTreeViewHeight = 250.dp
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
fun GameTreeView(
    currentNode: GameTreeNode?,
    gameTree: GameTree,
    gameTreeViewData: GameTreeViewData,
    uiSettings: UiSettings,
    onChangeCurrentNode: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Box(
        Modifier
            .padding(top = 10.dp)
            .size(gameTreeViewWidth, gameTreeViewHeight)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handleKeyEvent(keyEvent, gameTree).also {
                    if (it) {
                        onChangeCurrentNode()
                    }
                }
            }
    ) {
        Box(Modifier
            .horizontalScroll(horizontalScrollState)
            .verticalScroll(verticalScrollState)
            .padding(end = scrollbarSize, bottom = scrollbarSize)
            .size(gameTreeViewData.size)
        ) {
            ConnectionsAndNodes(
                gameTree,
                gameTreeViewData.elements,
                onChangeCurrentNode,
                uiSettings
            )
            CurrentNode(currentNode, gameTreeViewData)
        }

        HorizontalScrollbar(horizontalScrollState, Modifier
            .align(Alignment.BottomStart)
            .size(gameTreeViewWidth - scrollbarSize, scrollbarSize)
        )
        VerticalScrollbar(verticalScrollState, Modifier
            .align(Alignment.TopEnd)
            .size(scrollbarSize, gameTreeViewHeight - scrollbarSize)
        )
    }
    LaunchedEffect(gameTree) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(gameTreeViewData.size, currentNode) {
        scrollToSelectedNodeIfNeeded(
            gameTreeViewData,
            horizontalScrollState,
            verticalScrollState,
            coroutineScope
        )
    }
}

private fun scrollToSelectedNodeIfNeeded(
    gameTreeViewData: GameTreeViewData,
    horizontalScrollState: ScrollState,
    verticalScrollState: ScrollState,
    coroutineScope: CoroutineScope,
) {
    val (xIndex, yIndex) = gameTreeViewData.nodeToIndexMap.getValue(gameTreeViewData.gameTree.currentNode)

    horizontalScrollState.scrollToSelectedNodeIfNeeded(xIndex, gameTreeViewData.size.width, coroutineScope)
    verticalScrollState.scrollToSelectedNodeIfNeeded(yIndex, gameTreeViewData.size.height, coroutineScope)
}

private fun ScrollState.scrollToSelectedNodeIfNeeded(
    coordinateIndex: Int,
    dimension: Dp,
    coroutineScope: CoroutineScope,
) {
    val offset = stepSize * coordinateIndex + nodeSize / 2
    val minOffset = offset - selectedNodeVisibleRange
    val maxOffset = offset + selectedNodeVisibleRange

    val maxScrollAndViewportSize = maxValue + viewportSize

    val minNodeScrollValue = round(minOffset / dimension * maxScrollAndViewportSize).toInt()
    val maxNodeScrollValue = round(maxOffset / dimension * maxScrollAndViewportSize).toInt()

    if (minNodeScrollValue < value) {
        coroutineScope.launch {
            animateScrollTo(minNodeScrollValue)
        }
    } else if (maxNodeScrollValue > value + viewportSize) {
        coroutineScope.launch {
            animateScrollTo(maxNodeScrollValue - viewportSize)
        }
    }
}

class GameTreeViewData(val gameTree: GameTree) {
    val elements: GameTreeElements = gameTree.getElements(mainBranchIsAlwaysStraight = true)

    val size: DpSize = DpSize(
        stepSize * (elements.size - 1) + nodeSize,
        stepSize * (elements.maxOf { yLine -> yLine.size } - 1) + nodeSize
    )

    val nodeToIndexMap: Map<GameTreeNode, Pair<Int, Int>> by lazy {
        buildMap {
            for (xIndex in elements.indices) {
                for ((yIndex, element) in elements[xIndex].withIndex()) {
                    val node = (element as? NodeGameTreeElement)?.node ?: continue
                    this[node] = xIndex to yIndex
                }
            }
        }
    }
}

@Composable
private fun ConnectionsAndNodes(
    gameTree: GameTree,
    gameTreeElements: GameTreeElements,
    onChangeCurrentNode: () -> Unit,
    uiSettings: UiSettings
) {
    for (xIndex in gameTreeElements.indices) {
        val offsetX = stepSize * xIndex

        for ((yIndex, element) in gameTreeElements[xIndex].withIndex()) {
            val offsetY = stepSize * yIndex

            when (element) {
                is NodeGameTreeElement -> {
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
                            .pointerInput(gameTree) {
                                detectTapGestures(onPress = {
                                    if (gameTree.switch(node)) {
                                        onChangeCurrentNode()
                                    }
                                })
                            }
                    ) {
                        Text(moveNumber.toString(), Modifier.align(Alignment.Center), textColor)
                    }
                }

                is VerticalLineGameTreeElement -> {
                    // Render vertical connection line
                    Box(
                        Modifier
                            .offset(stepSize * xIndex - lineThickness / 2 + nodeRadius, offsetY - stepSize + nodeRadius)
                            .then(verticalLineModifier)
                    )
                }

                is EmptyGameTreeElement -> {}
            }
        }
    }
}

@Composable
private fun CurrentNode(currentNode: GameTreeNode?, gameTreeViewData: GameTreeViewData) {
    if (currentNode == null) return
    val (xIndex, yIndex) = gameTreeViewData.nodeToIndexMap.getValue(currentNode)

    Box(
        Modifier.offset(stepSize * xIndex, stepSize * yIndex).then(selectedNodeModifier)
    )
}

private fun handleKeyEvent(keyEvent: KeyEvent, gameTree: GameTree): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown) {
        return when (keyEvent.key) {
            Key.DirectionLeft -> gameTree.back()
            Key.DirectionRight -> gameTree.next()
            Key.DirectionUp -> gameTree.prevSibling()
            Key.DirectionDown -> gameTree.nextSibling()
            Key.MoveHome -> gameTree.rewindBack()
            Key.MoveEnd -> gameTree.rewindForward()
            else -> false
        }
    }

    return false
}