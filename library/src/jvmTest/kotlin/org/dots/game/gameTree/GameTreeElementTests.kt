package org.dots.game.gameTree

import org.dots.game.core.EmptyGameTreeElement
import org.dots.game.core.Field
import org.dots.game.core.GameTree
import org.dots.game.core.GameTreeNode
import org.dots.game.core.MoveInfo
import org.dots.game.core.NodeGameTreeElement
import org.dots.game.core.PositionXY
import org.dots.game.core.VerticalLineGameTreeElement
import org.dots.game.core.getElements
import org.dots.game.createStandardRules
import kotlin.test.Test
import kotlin.test.assertEquals

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
                getElements(mainBranchIsAlwaysStraight = false, diagonalConnections = false)
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
            val expectedNodes = setUpGameTreeForTestsThatShowsAffectionOfMainBranchIsAlwaysStraight()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0]), VerticalLineGameTreeElement, VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[10])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), NodeGameTreeElement(expectedNodes[7])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), NodeGameTreeElement(expectedNodes[8]), VerticalLineGameTreeElement), // x3
                    listOf(NodeGameTreeElement(expectedNodes[4]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[9])), // x4
                    listOf(NodeGameTreeElement(expectedNodes[5]), NodeGameTreeElement(expectedNodes[6])), // x5
                ),
                getElements(mainBranchIsAlwaysStraight = false, diagonalConnections = false)
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
            val expectedNodes = setUpGameTreeForTestsThatShowsAffectionOfMainBranchIsAlwaysStraight()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0]), VerticalLineGameTreeElement, VerticalLineGameTreeElement, VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), VerticalLineGameTreeElement, VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[10])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[7])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[8])), // x3
                    listOf(NodeGameTreeElement(expectedNodes[4]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[9])), // x4
                    listOf(NodeGameTreeElement(expectedNodes[5]), NodeGameTreeElement(expectedNodes[6])) // x5
                ),
                getElements(mainBranchIsAlwaysStraight = true, diagonalConnections = false)
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
     * . - 5 - 6 - 7
     * ```
     */
    @Test
    fun mainBranchIsAlwaysStraightMultipleElementsToArrange() {
        with (initializeGameTree()) {
            val expectedNodes = setUpGameTreeForTestsWithMultipleElementsToArrange()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0]), VerticalLineGameTreeElement, VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[5])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), VerticalLineGameTreeElement, NodeGameTreeElement(expectedNodes[6])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), NodeGameTreeElement(expectedNodes[4]), NodeGameTreeElement(expectedNodes[7])) // x3
                ),
                getElements(mainBranchIsAlwaysStraight = true, diagonalConnections = false)
            )
        }
    }

    /**
     * # Expected
     *
     * ```
     * 0 - 1 - 2 - 3
     *   \       \
     *     5 - 6   4
     *           \
     *             7
     * ```
     */
    @Test
    fun mainBranchIsNotAlwaysStraightMultipleElementsToArrangeWithDiagonalConnections() {
        with (initializeGameTree()) {
            val expectedNodes = setUpGameTreeForTestsWithMultipleElementsToArrange()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0])), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), NodeGameTreeElement(expectedNodes[5])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), NodeGameTreeElement(expectedNodes[6])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), NodeGameTreeElement(expectedNodes[4]), NodeGameTreeElement(expectedNodes[7])), // x3
                ),
                getElements(mainBranchIsAlwaysStraight = false, diagonalConnections = true)
            )
        }
    }


    /**
     * # Expected
     *
     * ```
     * 0 - 1 - 2 - 3
     * |         \
     * .           4
     *   \
     *     5 - 6 - 7
     * ```
     */
    @Test
    fun mainBranchIsAlwaysStraightMultipleElementsToArrangeWithDiagonalConnections() {
        with (initializeGameTree()) {
            val expectedNodes = setUpGameTreeForTestsWithMultipleElementsToArrange()

            assertEquals(
                listOf(
                    listOf(NodeGameTreeElement(expectedNodes[0]), VerticalLineGameTreeElement), // x0
                    listOf(NodeGameTreeElement(expectedNodes[1]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[5])), // x1
                    listOf(NodeGameTreeElement(expectedNodes[2]), EmptyGameTreeElement, NodeGameTreeElement(expectedNodes[6])), // x2
                    listOf(NodeGameTreeElement(expectedNodes[3]), NodeGameTreeElement(expectedNodes[4]), NodeGameTreeElement(expectedNodes[7])), // x3
                ),
                getElements(mainBranchIsAlwaysStraight = true, diagonalConnections = true)
            )
        }
    }

    private fun GameTree.setUpGameTreeForTestsThatShowsAffectionOfMainBranchIsAlwaysStraight(): List<GameTreeNode> {
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

    private fun GameTree.setUpGameTreeForTestsWithMultipleElementsToArrange(): List<GameTreeNode> {
        return buildList {
            add(rootNode) // 0
            add(makeMove(1, 1)) // 1
            add(makeMove(2, 1)) // 2
            add(makeMove(3, 1)) // 3

            back()
            add(makeMove(1, 2)) // 4

            switch(rootNode)
            add(makeMove(1, 3)) // 5
            add(makeMove(2, 3)) // 6
            add(makeMove(3, 3)) // 7
        }
    }

    private fun initializeGameTree(): GameTree {
        val field = Field.create(createStandardRules(8, 8))
        return GameTree(field)
    }

    private fun GameTree.makeMove(x: Int, y: Int): GameTreeNode {
        addChild(MoveInfo(PositionXY(x, y), field.getCurrentPlayer()))
        return currentNode
    }
}