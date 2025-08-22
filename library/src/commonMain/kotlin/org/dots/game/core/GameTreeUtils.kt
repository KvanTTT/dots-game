package org.dots.game.core

import kotlin.collections.List

private const val X_OFFSET = 1
private const val Y_OFFSET = 1

typealias GameTreeElements = List<List<GameTreeElement>>

sealed class GameTreeElement()

data class NodeGameTreeElement(val node: GameTreeNode) : GameTreeElement() {
    override fun toString(): String = node.toString()
}

object VerticalLineGameTreeElement : GameTreeElement() {
    override fun toString(): String = "|"
}

object EmptyGameTreeElement : GameTreeElement() {
    override fun toString(): String = "Empty"
}

/**
 * @return a structure that represents drawing elements of the game tree
 * The first `List` stands for X coordinate, the second `List` stands for Y coordinate.
 * The element could be node, vertical line or empty.
 *
 * @param[mainBranchIsAlwaysStraight] stands for rendering main branches.
 * The main branch is a branch composed of every first next node.
 * If it's `true`, then such branches are rendered as straight lines (see tests for clarity)
 *
 * @param[diagonalConnections] stands for using diagonal connections that are used for first node of secondary branches.
 * With this parameter, the game tree becomes more compact.
 */
fun GameTree.getElements(mainBranchIsAlwaysStraight: Boolean = true, diagonalConnections: Boolean = true): GameTreeElements {
    val result = mutableListOf<MutableList<GameTreeElement>>(mutableListOf(NodeGameTreeElement(rootNode)))

    fun walk(node: GameTreeNode, xIndex: Int) {
        val currentYLine = result[xIndex]

        val nextNodes = node.nextNodes.values
        val firstNode = nextNodes.firstOrNull()
        val lastNode = nextNodes.lastOrNull()
        for (nextNode in nextNodes) {
            val nextXIndex = xIndex + X_OFFSET
            val nextYLine = if (nextXIndex >= result.size) {
                mutableListOf<GameTreeElement>().also { result.add(it) }
            } else {
                result[nextXIndex]
            }

            val currentYLineSize = currentYLine.size
            val nextYLineSize = nextYLine.size + Y_OFFSET

            fun insertSpacesAndNode(yLine: MutableList<GameTreeElement>, maxYSize: Int, node: GameTreeNode) {
                val numberOfSpaces = maxYSize - yLine.size - 1
                (0 until numberOfSpaces).forEach { _ -> yLine.add(EmptyGameTreeElement) }
                yLine.add(NodeGameTreeElement(node))
            }

            fun insertVerticalLines(yLine: MutableList<GameTreeElement>, maxYSize: Int) {
                val numberOfVerticalLines = maxYSize - yLine.size - (if (diagonalConnections) 1 else 0)
                (0 until numberOfVerticalLines).forEach { _ -> yLine.add(VerticalLineGameTreeElement) }
            }

            val maxYSize: Int
            if (nextYLineSize > currentYLineSize) {
                // Increase Y index of the current branch to avoid collision with next nodes
                maxYSize = nextYLineSize

                if (mainBranchIsAlwaysStraight && nextNode == firstNode) {
                    // Realign parent nodes using the obtained new offset (`nextYLineSize`)
                    var xIndexOfMainBranch = xIndex
                    var yLineOfMainBranch = result[xIndex]
                    var currentNode: GameTreeNode
                    var nodeToRealign: GameTreeNode = node
                    do {
                        require((yLineOfMainBranch.removeLast() as NodeGameTreeElement).node == nodeToRealign)

                        insertSpacesAndNode(yLineOfMainBranch, maxYSize, nodeToRealign)

                        xIndexOfMainBranch--
                        yLineOfMainBranch = result[xIndexOfMainBranch]
                        currentNode = nodeToRealign
                        nodeToRealign = currentNode.previousNode!!
                    } while (nodeToRealign.nextNodes.values.firstOrNull() == currentNode && maxYSize > yLineOfMainBranch.size)

                    // Draw connection line from parent to each child node
                    insertVerticalLines(yLineOfMainBranch, maxYSize)
                }
            } else {
                // Not increase the Y index, but keep the index of the current branch
                maxYSize = currentYLineSize
            }

            insertSpacesAndNode(nextYLine, maxYSize, nextNode)

            if (nextNode == lastNode) {
                // Draw connection line from parent to each child node
                insertVerticalLines(currentYLine, maxYSize)
            }

            walk(nextNode, nextXIndex)
        }
    }

    walk(rootNode, 0)

    return result
}

fun GameTree.transform(transformType: TransformType): GameTree {
    val newWidth: Int
    val newHeight: Int
    val rules = field.rules
    when (transformType) {
        TransformType.RotateCw90,
        TransformType.RotateCw270 -> {
            newWidth = rules.height
            newHeight = rules.width
        }
        TransformType.Rotate180,
        TransformType.FlipHorizontal,
        TransformType.FlipVertical -> {
            newWidth = rules.width
            newHeight = rules.height
        }
    }

    val newFieldStride = Field.getStride(newWidth)
    fun Position.transform() = transform(transformType, field.realWidth, field.realHeight, newFieldStride)

    val newField = Field.create(Rules(
        width = newWidth,
        height = newHeight,
        captureByBorder = rules.captureByBorder,
        baseMode = rules.baseMode,
        suicideAllowed = rules.suicideAllowed,
        initialMoves = rules.initialMoves.map { (positionXY, player, extraInfo) ->
            MoveInfo(positionXY?.transform(transformType, field.realWidth, field.realHeight), player, extraInfo)
        }
    ))

    val newGameTree = GameTree(newField, player1TimeLeft, player2TimeLeft)
    newGameTree.memoizePaths = memoizePaths
    newGameTree.loopedSiblingNavigation = loopedSiblingNavigation

    val savedCurrentNode = currentNode
    var newCurrentNode = newGameTree.currentNode

    fun GameTreeNode.traverseChildren() {
        if (!isRoot) {
            val positionPlayer = moveResult?.positionPlayer
            val newMoveResult = positionPlayer?.let {
                newField.makeMoveUnsafe(it.position.transform(), it.player)
            }
            newGameTree.add(
                newMoveResult,
                newField.gameResult,
                timeLeft,
                comment,
                labels,
                circles?.map { it.transform() },
                squares?.map { it.transform() },
            )
        }
        if (this == savedCurrentNode) {
            newCurrentNode = newGameTree.currentNode
        }
        for (nextNode in nextNodes.values) {
            nextNode.traverseChildren()
            back()
            newGameTree.back()
        }
    }

    rootNode.traverseChildren()

    newGameTree.switch(newCurrentNode)

    return newGameTree
}