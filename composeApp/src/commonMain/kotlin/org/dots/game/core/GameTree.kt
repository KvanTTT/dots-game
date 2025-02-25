package org.dots.game.core

class GameTree(val field: Field, val player1TimeLeft: Double? = null, val player2TimeLeft: Double? = null) {
    val rootNode: GameTreeNode = GameTreeNode(null, null, 0, mutableMapOf())
    val allNodes: MutableSet<GameTreeNode> = mutableSetOf(rootNode)
    var currentNode: GameTreeNode = rootNode
        private set

    /**
     * @return `false` if such a node with the current @param[move] already exists,
     * otherwise add the new passed node and returns `true`
     */
    fun add(move: MoveResult, timeLeft: Double? = null): Boolean {
        val positionPlayer = move.positionPlayer
        val existingNode = currentNode.nextNodes[positionPlayer]

        var result: Boolean
        currentNode = if (existingNode == null) {
            result = true
            GameTreeNode(move, previousNode = currentNode, move.number - field.initialMovesCount + 1, timeLeft = timeLeft).also {
                currentNode.nextNodes[positionPlayer] = it
                allNodes.add(it)
            }
        } else {
            result = false
            existingNode
        }

        return result
    }

    /**
     * @return `false` if the @property[currentNode] is root, otherwise move it to the previous node and returns `true`
     */
    fun back(): Boolean {
        val previousNode = currentNode.previousNode ?: return false
        field.unmakeMove()
        currentNode = previousNode
        return true
    }

    /**
     * @return `false` if there are no next nodes, otherwise move it to the next node on the main line returns `true`
     */
    fun next(): Boolean {
        val nextNode = currentNode.nextNodes.values.firstOrNull() ?: return false
        val moveResult = nextNode.moveResult!!
        field.makeMoveUnsafe(moveResult.position, moveResult.player)
        currentNode = nextNode
        return true
    }

    /**
     * @return `false` if @param[targetNode] is a @property[currentNode] or it's an unrelated node,
     * otherwise perform switching to the passed node and returns `true`
     */
    fun switch(targetNode: GameTreeNode): Boolean {
        if (targetNode == currentNode) return false

        val reversedCurrentNodes = buildSet {
            currentNode.walkMovesReversed { node ->
                add(node)
                // Optimization: it's not necessary to walk up until the root node, if the `targetNode` is on the way
                return@walkMovesReversed node != targetNode
            }
        }

        var commonRootNode: GameTreeNode? = null
        val nextNodes = buildList {
            targetNode.walkMovesReversed { node ->
                if (reversedCurrentNodes.contains(node)) {
                    commonRootNode = node
                    false
                } else {
                    add(node)
                    true
                }
            }
        }.reversed()

        if (commonRootNode == null) return false // No common root -> the `targetNode` is unrelated

        val numberOfNodesToRollback = currentNode.number - commonRootNode.number
        (0 until numberOfNodesToRollback).forEach { i ->
            requireNotNull(field.unmakeMove())
            currentNode = currentNode.previousNode!!
        }

        for (nextNode in nextNodes) {
            val moveResult = nextNode.moveResult ?: continue
            val nextMovePosition = moveResult.position
            requireNotNull(field.makeMoveUnsafe(nextMovePosition, moveResult.player))
            currentNode = currentNode.nextNodes.getValue(moveResult.positionPlayer)
        }

        require(currentNode == targetNode)

        return true
    }

    /**
     * @return `false` if the @property[currentNode] is root,
     * otherwise removes the passed node with related branches and returns `true`
     */
    fun remove(): Boolean {
        if (currentNode == rootNode) return false

        fun removeRecursively(node: GameTreeNode) {
            require(allNodes.remove(node))
            for (nextNode in node.nextNodes.values) {
                removeRecursively(nextNode)
            }
            node.nextNodes.clear()
        }

        val currentNodePositionPlayer = currentNode.moveResult!!.positionPlayer

        removeRecursively(currentNode)
        require(back())

        requireNotNull(currentNode.nextNodes.remove(currentNodePositionPlayer))

        return true
    }

    private inline fun GameTreeNode.walkMovesReversed(action: (GameTreeNode) -> Boolean) {
        var move = this
        var distance = 0
        do {
            if (!action(move)) return
            distance++
            move = move.previousNode ?: break
        }
        while (true)
    }
}

class GameTreeNode(
    val moveResult: MoveResult?,
    val previousNode: GameTreeNode?,
    val number: Int,
    val nextNodes: MutableMap<PositionPlayer, GameTreeNode> = mutableMapOf(),
    val timeLeft: Double? = null,
) {
    val isRoot = moveResult == null

    override fun toString(): String {
        return "#$number: " + (moveResult?.position?.toString() ?: "root")
    }
}

