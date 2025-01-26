package org.dots.game.core

class FieldHistory(val field: Field) {
    val rootNode: Node = Node(null, null, 0, mutableMapOf())
    val allNodes: MutableSet<Node> = mutableSetOf(rootNode)
    var currentNode: Node = rootNode
        private set

    /**
     * @return `false` if such a node with the current @param[move] already exists,
     * otherwise add the new passed node and returns `true`
     */
    fun add(move: MoveResult): Boolean {
        val position = move.position
        val existingNode = currentNode.nextNodes[position]

        var result: Boolean
        currentNode = if (existingNode == null) {
            result = true
            Node(move, previousNode = currentNode, move.number - field.initialMovesCount + 1).also {
                currentNode.nextNodes[position] = it
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
    fun switch(targetNode: Node): Boolean {
        if (targetNode == currentNode) return false

        val reversedCurrentNodes = buildSet {
            currentNode.walkMovesReversed { node ->
                add(node)
                // Optimization: it's not necessary to walk up until the root node, if the `targetNode` is on the way
                return@walkMovesReversed node != targetNode
            }
        }

        var commonRootNode: Node? = null
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
            currentNode = currentNode.nextNodes.getValue(moveResult.position)
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

        fun removeRecursively(node: Node) {
            require(allNodes.remove(node))
            for (nextNode in node.nextNodes.values) {
                removeRecursively(nextNode)
            }
            node.nextNodes.clear()
        }

        val currentNodePosition = currentNode.moveResult!!.position

        removeRecursively(currentNode)
        require(back())

        requireNotNull(currentNode.nextNodes.remove(currentNodePosition))

        return true
    }

    private inline fun Node.walkMovesReversed(action: (Node) -> Boolean) {
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

class Node(
    val moveResult: MoveResult?,
    val previousNode: Node?,
    val number: Int,
    val nextNodes: MutableMap<Position, Node> = mutableMapOf(),
) {
    val isRoot = moveResult == null

    override fun toString(): String {
        return "#$number: " + (moveResult?.position?.toString() ?: "root")
    }
}
