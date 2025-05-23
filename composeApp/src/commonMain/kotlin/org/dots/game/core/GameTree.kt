package org.dots.game.core

class GameTree(val field: Field, val player1TimeLeft: Double? = null, val player2TimeLeft: Double? = null) {
    val rootNode: GameTreeNode = GameTreeNode(null, null, 0, mutableMapOf())
    val allNodes: MutableSet<GameTreeNode> = mutableSetOf(rootNode)
    var currentNode: GameTreeNode = rootNode
        private set
    var memoizePaths: Boolean = true
    var loopedSiblingNavigation: Boolean = true
    private val memoizedNextChild: MutableMap<GameTreeNode, GameTreeNode> = mutableMapOf()

    /**
     * @return `false` if such a node with the current @param[move] already exists,
     * otherwise add the new passed node and returns `true`
     */
    fun add(
        move: MoveResult?,
        timeLeft: Double? = null,
        comment: String? = null,
        labels: List<Label>? = null,
        circles: List<Position>? = null,
        squares: List<Position>? = null,
    ): Boolean {
        val positionPlayer = move?.positionPlayer
        val existingNode = currentNode.nextNodes[positionPlayer]

        var result: Boolean
        currentNode = if (existingNode == null) {
            result = true
            GameTreeNode(move, previousNode = currentNode, currentNode.number + 1, timeLeft = timeLeft, comment = comment, labels = labels, circles = circles, squares = squares).also {
                currentNode.nextNodes[positionPlayer] = it
                allNodes.add(it)
            }
        } else {
            result = false
            existingNode
        }

        return result
    }

    fun rewindBack(): Boolean = switch(rootNode)

    fun rewindForward(): Boolean {
        var result = false
        while (next()) {
            result = true
        }
        return result
    }

    /**
     * @return `false` if the @property[currentNode] is root, otherwise move it to the previous nodes and returns `true`
     */
    fun back(count: Int = 1): Boolean {
        require(count >= 0) { "Count must be non-negative, got $count" }
        var counter = 0

        while (counter < count) {
            val previousNode = currentNode.previousNode ?: break
            previousNode.memoizeCurrentNodeIfNeeded()
            unmakeMoveIfNeeded()
            currentNode = previousNode
            counter++
        }

        return counter > 0
    }

    /**
     * @return `false` if there are no next nodes, otherwise move it to the next nodes on the main line returns `true`
     */
    fun next(count: Int = 1): Boolean {
        require(count >= 0) { "Count must be non-negative, got $count" }
        var counter = 0

        while (counter < count) {
            val nextNode =
                (memoizedNextChild[currentNode] ?: currentNode.nextNodes.values.firstOrNull()) ?: break
            nextNode.moveResult.makeMoveIfNeeded()
            currentNode = nextNode
            counter++
        }

        return counter > 0
    }

    fun prevSibling(): Boolean {
        return switchToSibling(next = false)
    }

    fun nextSibling(): Boolean {
        return switchToSibling(next = true)
    }

    private fun switchToSibling(next: Boolean): Boolean {
        val previousNode = currentNode.previousNode ?: return false
        val nextNodeValues = previousNode.nextNodes.values
        if (nextNodeValues.size <= 1) return false

        var currentNodeFound = false
        var targetSiblingNode: GameTreeNode? = null
        val siblings = if (next) nextNodeValues else nextNodeValues.reversed()
        for (node in siblings) {
            if (node == currentNode) {
                currentNodeFound = true
            } else if (currentNodeFound) {
                targetSiblingNode = node
                break
            }
        }
        require(currentNodeFound)
        if (targetSiblingNode == null) {
            if (loopedSiblingNavigation) {
                targetSiblingNode = siblings.first()
            } else {
                return false
            }
        }

        unmakeMoveIfNeeded()
        targetSiblingNode.moveResult.makeMoveIfNeeded()
        currentNode = targetSiblingNode

        previousNode.memoizeCurrentNodeIfNeeded()

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
            unmakeMoveIfNeeded()
            currentNode = currentNode.previousNode!!.also { it.memoizeCurrentNodeIfNeeded() }
        }

        for (nextNode in nextNodes) {
            val moveResult = nextNode.moveResult
            moveResult.makeMoveIfNeeded()
            currentNode = currentNode.nextNodes.getValue(moveResult?.positionPlayer)
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
            memoizedNextChild.remove(node)
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

    private fun GameTreeNode.memoizeCurrentNodeIfNeeded() {
        if (memoizePaths && nextNodes.size > 1) {
            memoizedNextChild[this] = currentNode
        }
    }

    private fun MoveResult?.makeMoveIfNeeded() {
        if (this != null) {
            requireNotNull(field.makeMoveUnsafe(position, player))
        }
    }

    private fun unmakeMoveIfNeeded() {
        if (currentNode.moveResult != null) {
            requireNotNull(field.unmakeMove())
        }
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
    val nextNodes: MutableMap<PositionPlayer?, GameTreeNode> = mutableMapOf(),
    val timeLeft: Double? = null,
    val comment: String? = null,
    val labels: List<Label>? = null,
    val circles: List<Position>? = null,
    val squares: List<Position>? = null,
) {
    val isRoot = previousNode == null

    override fun toString(): String {
        return "#$number: " + (moveResult?.position?.toString() ?: "root")
    }
}

