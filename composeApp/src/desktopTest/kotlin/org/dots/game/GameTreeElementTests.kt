package org.dots.game

import org.dots.game.core.EmptyGameTreeElement
import org.dots.game.core.Field
import org.dots.game.core.GameTree
import org.dots.game.core.GameTreeNode
import org.dots.game.core.NodeGameTreeElement
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.VerticalLineGameTreeElement
import org.dots.game.core.getElements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTreeElementTests {
    /**
     * # Expected
     *
     * ```
     * 0 - 1
     * |
     * . - 2 - 3
     * ```
     */
    @Test
    fun simple() {
        with (initializeGameTree()) {
            val node0 = rootNode
            val node1 = makeMove(1, 1)
            back()
            val node2 = makeMove(2, 2)
            val node3 = makeMove(3, 3)

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(node0), VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(node1), NodeGameTreeElement(node2)), // x1
                    listOf(EmptyGameTreeElement, NodeGameTreeElement(node3)) // x2
                ),
                getElements()
            )
        }
    }

    /**
     * # Expected
     *
     * ```
     * 0 - 1 - 2 - 3 - 4 - 5
     * |   |           |
     * .   . - 7 - 8   . - 6
     * |           |
     * . - 10      . - 9
     * ```
     */
    @Test
    fun mainBranchIsAlwaysStraightIsDisabled() {
        with (initializeGameTree()) {
            val expectedNodes = setUpGameTreeForTestsThatShowAffectionOfMainBranchIsAlwaysStraight()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0]), VerticalLineGameTreeElement, VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[10])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), NodeGameTreeElement(expectedNodes[7])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), NodeGameTreeElement(expectedNodes[8]), VerticalLineGameTreeElement), // x3
                    listOf(NodeGameTreeElement(expectedNodes[4]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[9])), // x4
                    listOf(NodeGameTreeElement(expectedNodes[5]), NodeGameTreeElement(expectedNodes[6])), // x5
                ),
                getElements()
            )
        }
    }

    /**
     * # Expected
     *
     * ```
     * 0 - 1 - 2 - 3 - 4 - 5
     * |   |           |
     * .   .           . - 6
     * |   |
     * .   . - 7 - 8 - 9
     * |
     * . - 10
     * ```
     */
    @Test
    fun mainBranchIsAlwaysStraightIsEnabled() {
        with (initializeGameTree()) {
            val expectedNodes = setUpGameTreeForTestsThatShowAffectionOfMainBranchIsAlwaysStraight()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0]), VerticalLineGameTreeElement, VerticalLineGameTreeElement, VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), VerticalLineGameTreeElement, VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[10])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[7])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[8])), // x3
                    listOf(NodeGameTreeElement(expectedNodes[4]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[9])), // x4
                    listOf(NodeGameTreeElement(expectedNodes[5]), NodeGameTreeElement(expectedNodes[6])) // x5
                ),
                getElements(mainBranchIsAlwaysStraight = true)
            )
        }
    }

    /**
     * # Expected
     *
     * ```
     * 0 - 1 - 2 - 3
     * |       |
     * .       . - 4
     * |
     * . - 5 - 6
     * ```
     */
    @Test
    fun mainBranchIsAlwaysStraightMultipleElementsToArrange() {
        with (initializeGameTree()) {
            val node0 = rootNode
            val node1 = makeMove(1, 1)
            val node2 = makeMove(2, 1)
            val node3 = makeMove(3, 1)

            back()
            val node4 = makeMove(1, 2)

            switch(rootNode)
            val node5 = makeMove(1, 3)
            val node6 = makeMove(2, 3)

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(node0), VerticalLineGameTreeElement, VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(node1), EmptyGameTreeElement, NodeGameTreeElement(node5)), // x1
                    listOf(NodeGameTreeElement(node2), VerticalLineGameTreeElement, NodeGameTreeElement(node6)), // x2
                    listOf(NodeGameTreeElement(node3), NodeGameTreeElement(node4)) // x3
                ),
                getElements(mainBranchIsAlwaysStraight = true)
            )
        }
    }

    private fun GameTree.setUpGameTreeForTestsThatShowAffectionOfMainBranchIsAlwaysStraight(): List<GameTreeNode> {
        return buildList {
            add(rootNode)       // 0
            add(makeMove(1, 1)) // 1
            add(makeMove(2, 1)) // 2
            add(makeMove(3, 1)) // 3
            add(makeMove(4, 1)) // 4
            add(makeMove(5, 1)) // 5

            back()
            add(makeMove(1, 2)) // 6

            switch(rootNode)
            next()

            add(makeMove(1, 3)) // 7
            add(makeMove(2, 3)) // 8
            add(makeMove(3, 3)) // 9

            switch(rootNode)
            add(makeMove(1, 4)) // 10
        }
    }

    private fun initializeGameTree(): GameTree {
        val field = Field(Rules(8, 8))
        return GameTree(field)
    }

    private fun GameTree.makeMove(x: Int, y: Int): GameTreeNode {
        assertTrue(add(field.makeMoveUnsafe(Position(x, y))!!))
        return currentNode
    }
}