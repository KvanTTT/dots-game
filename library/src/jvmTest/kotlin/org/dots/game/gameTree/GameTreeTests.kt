package org.dots.game.gameTree

import org.dots.game.core.Field
import org.dots.game.core.GameTree
import org.dots.game.core.GameTree.NodeKind
import org.dots.game.core.GameTreeNode
import org.dots.game.core.InitPosType
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.PositionXY
import org.dots.game.createStandardRules
import org.dots.game.field.FieldTests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTreeTests : FieldTests() {
    @Test
    fun addChildBackNextCommands() {
        with(initializeGameTree()) {
            assertEquals(NodeKind.New,makeMove(1, 1))
            assertTrue(back())
            assertFalse(back()) // False because there are no more moves
            assertEquals(NodeKind.ExistingChild, makeMove(1, 1)) // False, because such a node already exists
            assertTrue(back())
            assertEquals(NodeKind.New, makeMove(2, 1)) // True, because it's a new node
            assertTrue(back())
            assertTrue(next()) // True, the current node should be (2;1) (the second branch, because the previous path is memorized)
            assertEquals(Position(2, 1, field.realWidth), field.lastMove!!.positionPlayer.position)
            assertFalse(next()) // False, there are no more moves
        }
    }

    @Test
    fun nextPrevSiblingCommandsLooped() {
        nextPrevSiblingCommands(loopedSiblingNavigation = true)
    }

    @Test
    fun nextPrevSiblingCommandsNoLooped() {
        nextPrevSiblingCommands(loopedSiblingNavigation = false)
    }

    private fun nextPrevSiblingCommands(loopedSiblingNavigation: Boolean) {
        with(initializeGameTree()) {
            this.loopedSiblingNavigation = loopedSiblingNavigation

            assertEquals(NodeKind.New,makeMove(1, 1))
            val firstNode = currentNode
            back()

            assertEquals(NodeKind.New,makeMove(2, 1))
            val secondNode = currentNode
            back()

            assertEquals(NodeKind.New,makeMove(3, 1))
            val thirdNode = currentNode

            assertTrue(prevSibling())
            assertEquals(secondNode, currentNode)
            assertTrue(prevSibling())
            assertEquals(firstNode, currentNode)
            val prevSiblingResult = prevSibling()
            if (loopedSiblingNavigation) {
                assertTrue(prevSiblingResult)
                assertEquals(thirdNode, currentNode)
            } else {
                assertFalse(prevSiblingResult)
            }

            switch(firstNode)

            assertTrue(nextSibling())
            assertEquals(secondNode, currentNode)
            assertTrue(nextSibling())
            assertEquals(thirdNode, currentNode)
            val nextSiblingResult = nextSibling()
            if (loopedSiblingNavigation) {
                assertTrue(nextSiblingResult)
                assertEquals(firstNode, currentNode)
            } else {
                assertFalse(nextSiblingResult)
            }
        }
    }

    @Test
    fun memoizedNodes() {
        with(initializeGameTree()) {
            val initNode = currentNode

            assertEquals(NodeKind.New,makeMove(1, 1))
            back()
            assertEquals(NodeKind.New,makeMove(2, 1))

            val secondNode = currentNode

            assertEquals(NodeKind.New,makeMove(2, 2))
            back()
            assertEquals(NodeKind.New,makeMove(3, 2))

            val thirdNode = currentNode

            switch(initNode)
            next()
            assertEquals(secondNode, currentNode)
            next()
            assertEquals(thirdNode, currentNode)
        }
    }

    @Test
    fun newNodeIfPlacedToTheSamePositionButByDifferentPlayers() {
        with(initializeGameTree()) {
            assertEquals(NodeKind.New,makeMove(1, 1, Player.First))
            assertTrue(back())
            assertEquals(NodeKind.ExistingChild,makeMove(1, 1, Player.First)) // False, because such a node already exists
            assertTrue(back())
            assertEquals(NodeKind.New,makeMove(1, 1, Player.Second)) // True, same position, but player is another
        }
    }

    @Test
    fun incorrectMoves() {
        with (initializeGameTree()) {
            assertEquals(NodeKind.New,makeMove(1, 1, Player.First))
            assertEquals(1,  currentNode.number)
            assertEquals(NodeKind.New, addChild(MoveInfo(PositionXY(100, 100), Player.None)))
            assertEquals(2,  currentNode.number)
            assertEquals(NodeKind.New,makeMove(2, 2, Player.First))
            assertEquals(3,  currentNode.number)

            assertTrue(back())
            assertTrue(back())
            assertTrue(back())
            assertFalse(back())

            assertEquals(rootNode, currentNode)
        }
    }

    @Test
    fun switchOnTheSameNode() {
        val gameTree = initializeGameTree()

        gameTree.makeMove(1, 1)
        val currentNode = gameTree.currentNode
        assertFalse(gameTree.switch(currentNode)) // No switch on the same node
    }

    @Test
    fun switchOnPreviousAndNextNode() {
        with(initializeGameTree()) {
            makeMove(1, 1)
            val firstMoveNode = currentNode

            makeMove(2, 1)
            makeMove(2, 2)
            val lastMoveNode = currentNode

            assertTrue(switch(firstMoveNode)) // Only rollback 2 nodes
            assertEquals(firstMoveNode, currentNode)

            assertTrue(switch(lastMoveNode)) // Only push 2 nodes
            assertEquals(lastMoveNode, currentNode)
        }
    }

    @Test
    fun switchOnDifferentBranches() {
        with (initializeGameTree()) {
            makeMove(1, 1)
            val firstMoveNode = currentNode

            makeMove(2, 1)
            back()
            back()

            makeMove(2, 2)
            makeMove(3, 3)

            assertTrue(switch(firstMoveNode)) // Rollback 2 nodes and add 1 one
            assertEquals(firstMoveNode, currentNode)
        }
    }

    @Test
    fun switchOnUnrelatedNode() {
        val gameTree = initializeGameTree()

        gameTree.makeMove(1, 1)
        gameTree.makeMove(2, 2)

        // Try switching to an unrelated node, no change
        assertFalse(gameTree.switch(GameTreeNode(null, 0, mutableMapOf())))
    }

    @Test
    fun removeRootNode() {
        assertFalse(initializeGameTree().remove())
    }

    @Test
    fun removeBranch() {
        with(initializeGameTree()) {
            makeMove(1, 1)
            val nodeToRemove = currentNode

            makeMove(2, 1)
            back()
            makeMove(2, 2)

            assertTrue(switch(nodeToRemove))

            val nodeBackToRemoving = rootNode
            assertEquals(1, nodeBackToRemoving.children.size)
            assertTrue(remove())
            assertEquals(nodeBackToRemoving, currentNode)
            assertEquals(0, nodeBackToRemoving.children.size)

            assertFalse(remove()) // Not possible to remove twice

            // The move doesn't exist after removing, adding should be successful
            assertEquals(NodeKind.New,makeMove(1, 1))
        }
    }

    private fun initializeGameTree(): GameTree {
        val field = Field.create(createStandardRules(4, 4, initPosType = InitPosType.Empty))
        return GameTree(field)
    }

    private fun GameTree.makeMove(x: Int, y: Int, player: Player? = null): NodeKind {
        return addChild(MoveInfo(PositionXY(x, y), player ?: field.getCurrentPlayer()))
    }
}