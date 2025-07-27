package org.dots.game.gameTree

import org.dots.game.core.Field
import org.dots.game.core.GameTree
import org.dots.game.core.GameTreeNode
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.field.FieldTests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTreeTests : FieldTests() {
    @Test
    fun addBackNextCommands() {
        with(initializeGameTree()) {
            assertTrue(makeMove(1, 1))
            assertTrue(back())
            assertFalse(back()) // False because there are no more moves
            assertFalse(makeMove(1, 1)) // False, because such a node already exists
            assertTrue(back())
            assertTrue(makeMove(2, 1)) // True, because it's a new node
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

            assertTrue(makeMove(1, 1))
            val firstNode = currentNode
            back()

            assertTrue(makeMove(2, 1))
            val secondNode = currentNode
            back()

            assertTrue(makeMove(3, 1))
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

            assertTrue(makeMove(1, 1))
            back()
            assertTrue(makeMove(2, 1))

            val secondNode = currentNode

            assertTrue(makeMove(2, 2))
            back()
            assertTrue(makeMove(3, 2))

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
            assertTrue(makeMove(1, 1, Player.First))
            assertTrue(back())
            assertFalse(makeMove(1, 1, Player.First)) // False, because such a node already exists
            assertTrue(back())
            assertTrue(makeMove(1, 1, Player.Second)) // True, same position, but player is another
        }
    }

    @Test
    fun incorrectMoves() {
        with (initializeGameTree()) {
            assertTrue(makeMove(1, 1, Player.First))
            assertEquals(1,  currentNode.number)
            assertTrue(add(null))
            assertEquals(2,  currentNode.number)
            assertTrue(makeMove(2, 2, Player.First))
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

        assertFalse(gameTree.switch(GameTreeNode(null, null, null, 0))) // Try switching to unrelated node, no change
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
            assertEquals(1, nodeBackToRemoving.nextNodes.size)
            assertTrue(remove())
            assertEquals(nodeBackToRemoving, currentNode)
            assertEquals(0, nodeBackToRemoving.nextNodes.size)

            assertFalse(remove()) // Not possible to remove twice

            assertTrue(makeMove(1, 1)) // The move doesn't exist after removing, adding should be successful
        }
    }

    private fun initializeGameTree(): GameTree {
        val field = Field.create(Rules(4, 4, initialMoves = emptyList()))
        return GameTree(field)
    }

    private fun GameTree.makeMove(x: Int, y: Int, player: Player? = null): Boolean {
        return add(field.makeMoveUnsafe(Position(x, y, field.realWidth), player)!!)
    }
}