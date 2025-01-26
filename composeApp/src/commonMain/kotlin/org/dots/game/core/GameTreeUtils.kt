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
 */
fun GameTree.getElements(mainBranchIsAlwaysStraight: Boolean = false): GameTreeElements {
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
                (0 until numberOfSpaces).forEach { yLine.add(EmptyGameTreeElement) }
                yLine.add(NodeGameTreeElement(node))
            }

            fun insertVerticalLines(yLine: MutableList<GameTreeElement>, maxYSize: Int) {
                val numberOfVerticalLines = maxYSize - yLine.size
                (0 until numberOfVerticalLines).forEach { yLine.add(VerticalLineGameTreeElement) }
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