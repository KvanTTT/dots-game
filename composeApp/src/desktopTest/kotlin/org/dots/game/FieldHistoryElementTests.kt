package org.dots.game

import org.dots.game.core.EmptyHistoryElement
import org.dots.game.core.Field
import org.dots.game.core.FieldHistory
import org.dots.game.core.InitialPosition
import org.dots.game.core.Node
import org.dots.game.core.NodeHistoryElement
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.VerticalLineHistoryElement
import org.dots.game.core.getHistoryElements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldHistoryElementTests {
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
        with (initializeFieldHistory()) {
            val node0 = rootNode
            val node1 = makeMove(1, 1)
            back()
            val node2 = makeMove(2, 2)
            val node3 = makeMove(3, 3)

            assertEquals(
                listOf(
                    listOf(NodeHistoryElement(node0), VerticalLineHistoryElement), // x0
                    listOf(NodeHistoryElement(node1), NodeHistoryElement(node2)), // x1
                    listOf(EmptyHistoryElement, NodeHistoryElement(node3)) // x2
                ),
                getHistoryElements()
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
        with (initializeFieldHistory()) {
            val expectedNodes = setUpHistoryForTestsThatShowAffectionOfMainBranchIsAlwaysStraight()

            assertEquals(
                listOf(
                    listOf(NodeHistoryElement(expectedNodes[0]), VerticalLineHistoryElement, VerticalLineHistoryElement), // x0
                    listOf(NodeHistoryElement(expectedNodes[1]), VerticalLineHistoryElement, NodeHistoryElement(expectedNodes[10])), // x1
                    listOf(NodeHistoryElement(expectedNodes[2]), NodeHistoryElement(expectedNodes[7])), // x2
                    listOf(NodeHistoryElement(expectedNodes[3]), NodeHistoryElement(expectedNodes[8]), VerticalLineHistoryElement), // x3
                    listOf(NodeHistoryElement(expectedNodes[4]), VerticalLineHistoryElement, NodeHistoryElement(expectedNodes[9])), // x4
                    listOf(NodeHistoryElement(expectedNodes[5]), NodeHistoryElement(expectedNodes[6])), // x5
                ),
                getHistoryElements()
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
        with (initializeFieldHistory()) {
            val expectedNodes = setUpHistoryForTestsThatShowAffectionOfMainBranchIsAlwaysStraight()

            assertEquals(
                listOf(
                    listOf(NodeHistoryElement(expectedNodes[0]), VerticalLineHistoryElement, VerticalLineHistoryElement, VerticalLineHistoryElement), // x0
                    listOf(NodeHistoryElement(expectedNodes[1]), VerticalLineHistoryElement, VerticalLineHistoryElement, NodeHistoryElement(expectedNodes[10])), // x1
                    listOf(NodeHistoryElement(expectedNodes[2]), EmptyHistoryElement, NodeHistoryElement(expectedNodes[7])), // x2
                    listOf(NodeHistoryElement(expectedNodes[3]), EmptyHistoryElement, NodeHistoryElement(expectedNodes[8])), // x3
                    listOf(NodeHistoryElement(expectedNodes[4]), VerticalLineHistoryElement, NodeHistoryElement(expectedNodes[9])), // x4
                    listOf(NodeHistoryElement(expectedNodes[5]), NodeHistoryElement(expectedNodes[6])) // x5
                ),
                getHistoryElements(mainBranchIsAlwaysStraight = true)
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
        with (initializeFieldHistory()) {
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
                    listOf(NodeHistoryElement(node0), VerticalLineHistoryElement, VerticalLineHistoryElement), // x0
                    listOf(NodeHistoryElement(node1), EmptyHistoryElement, NodeHistoryElement(node5)), // x1
                    listOf(NodeHistoryElement(node2), VerticalLineHistoryElement, NodeHistoryElement(node6)), // x2
                    listOf(NodeHistoryElement(node3), NodeHistoryElement(node4)) // x3
                ),
                getHistoryElements(mainBranchIsAlwaysStraight = true)
            )
        }
    }

    private fun FieldHistory.setUpHistoryForTestsThatShowAffectionOfMainBranchIsAlwaysStraight(): List<Node> {
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

    private fun initializeFieldHistory(): FieldHistory {
        val field = Field(Rules(8, 8, initialPosition = InitialPosition.Empty))
        return FieldHistory(field)
    }

    private fun FieldHistory.makeMove(x: Int, y: Int): Node {
        assertTrue(add(field.makeMoveUnsafe(Position(x, y))!!))
        return currentNode
    }
}