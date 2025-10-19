package org.dots.game.core

import org.dots.game.ParsedNode
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.mutableMapOf
import kotlin.reflect.KProperty

class GameTree(val field: Field, parsedNode: ParsedNode? = null) {
    val rootNode: GameTreeNode = GameTreeNode(null, 0, mutableMapOf(), parsedNode)

    private val memoizedNextChild: MutableMap<GameTreeNode, GameTreeNode> = mutableMapOf()
    var currentNode: GameTreeNode = rootNode
        private set

    var memoizePaths: Boolean = true
    var loopedSiblingNavigation: Boolean = true

    enum class NodeKind {
        New,
        ExistingChild,
    }

    fun addChild(moveInfo: MoveInfo, moveReporter: (MoveInfo, MoveResult) -> Unit = { _, _ -> }): NodeKind {
        val properties = mutableMapOf<KProperty<*>, GameProperty<*>>().apply {
            val key = if (moveInfo.player == Player.First)
                GameTreeNode::player1Moves
            else
                GameTreeNode::player2Moves
            val gameProperty = GameProperty(listOf(moveInfo), changed = true)
            this[key] = gameProperty
        }
        return addChild(properties, parsedNode = null, moveReporter)
    }

    fun addChild(properties: PropertiesMap, parsedNode: ParsedNode?, moveReporter: (MoveInfo, MoveResult) -> Unit): NodeKind {
        var nodeKind = NodeKind.New
        var existingChild: GameTreeNode? = null
        for (child in currentNode.children) {
            if (compareProperties(properties, child.properties)) {
                nodeKind = NodeKind.ExistingChild
                existingChild = child
            }
        }
        val nextNode = existingChild ?: currentNode.createChild(properties, parsedNode)
        switch(nextNode, moveReporter)
        return nodeKind
    }

    fun rewindBack(): Boolean = switch(rootNode)

    fun rewindToEnd(): Boolean {
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

        (0..<count).forEach { _ ->
            if (!switch(currentNode.previousNode)) {
                return false
            }
        }

        return true
    }

    /**
     * @return `false` if there are no next nodes, otherwise move it to the next nodes on the main line returns `true`
     */
    fun next(count: Int = 1): Boolean {
        require(count >= 0) { "Count must be non-negative, got $count" }

        (0..<count).forEach { _ ->
            if (!switch(memoizedNextChild[currentNode] ?: currentNode.children.firstOrNull())) {
                return false
            }
        }

        return true
    }

    fun prevSibling(): Boolean {
        return switchToSibling(next = false)
    }

    fun nextSibling(): Boolean {
        return switchToSibling(next = true)
    }

    private fun switchToSibling(next: Boolean): Boolean {
        val previousNode = currentNode.previousNode ?: return false
        val siblings = previousNode.children
        val currentNodeIndex = siblings.indexOf(currentNode)
        if (!loopedSiblingNavigation) {
            if (next && currentNodeIndex == siblings.size - 1 || !next && currentNodeIndex == 0) {
                return false
            }
        }
        return switch(siblings[(currentNodeIndex + (if (next) 1 else (siblings.size - 1))) % siblings.size])
    }

    /**
     * @return `false` if @param[targetNode] is a @property[currentNode] or it's an unrelated node,
     * otherwise perform switching to the passed node and returns `true`
     */
    fun switch(targetNode: GameTreeNode?, moveReporter: (MoveInfo, MoveResult) -> Unit = { _, _ -> }): Boolean {
        if (targetNode == null || targetNode == currentNode) return false

        var currentRollbackNode: GameTreeNode? = currentNode
        var currentNextNode: GameTreeNode? = targetNode
        val rollbackNodes: MutableSet<GameTreeNode> = mutableSetOf()
        val reversedNextNodes: MutableSet<GameTreeNode> = mutableSetOf()

        while (currentRollbackNode != null || currentNextNode != null) {
            currentRollbackNode?.let { rollbackNodes.add(it) }
            currentNextNode?.let { reversedNextNodes.add(it) }

            fun checkIntersection(sequence: MutableSet<GameTreeNode>, otherSequence: MutableSet<GameTreeNode>, node: GameTreeNode?): Boolean {
                if (!sequence.contains(node)) return false

                for (rollbackNode in sequence.reversed()) {
                    require(sequence.remove(rollbackNode))
                    if (rollbackNode == node) {
                        break
                    }
                }
                otherSequence.remove(node)
                return true
            }

            if (checkIntersection(rollbackNodes, reversedNextNodes, currentNextNode)) {
                break
            }

            if (checkIntersection(reversedNextNodes, rollbackNodes, currentRollbackNode)) {
                break
            }

            currentRollbackNode = currentRollbackNode?.previousNode
            currentNextNode = currentNextNode?.previousNode
        }

        if (currentRollbackNode == null && currentNextNode == null) return false // No common root -> the `targetNode` is unrelated

        for (rollbackNode in rollbackNodes) {
            rollbackNode.unmakeMoves()
            val previousNode = currentNode.previousNode!!
            if (memoizePaths && previousNode.children.size > 1) {
                memoizedNextChild[previousNode] = currentNode
            }
            currentNode = previousNode
        }

        for (nextNode in reversedNextNodes.reversed()) {
            nextNode.makeMoves(moveReporter)
            if (memoizePaths && currentNode.children.size > 1) {
                memoizedNextChild[currentNode] = nextNode
            }
            currentNode = currentNode.children.single { it == nextNode }
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
            memoizedNextChild.remove(node)
            for (nextNode in node.children) {
                removeRecursively(nextNode)
            }
            node.children.clear()
        }

        val previousNode = currentNode
        removeRecursively(currentNode)
        require(back())

        requireNotNull(currentNode.children.remove(previousNode))

        return true
    }

    fun forEachNode(action: (GameTreeNode) -> Unit) {
        fun forEachNodeRecursively(node: GameTreeNode) {
            action(node)
            node.children.forEach { forEachNodeRecursively(it) }
        }

        forEachNodeRecursively(rootNode)
    }

    @Suppress("UNCHECKED_CAST")
    private fun GameTreeNode.makeMoves(moveReporter: (MoveInfo, MoveResult) -> Unit) {
        val newMoveResults = mutableListOf<MoveResult>()
        for ((key, property) in properties) {
            when (key) {
                GameTreeNode::player1Moves,
                GameTreeNode::player2Moves -> {
                    val moveInfos = property.value as? List<MoveInfo>
                    if (moveInfos != null) {
                        for (moveInfo in moveInfos) {
                            val moveResult = field.makeMove(moveInfo)
                            moveReporter(moveInfo, moveResult)
                            newMoveResults.add(moveResult)
                        }
                    }
                }
            }
        }
        moveResults = newMoveResults
    }

    private fun GameTreeNode.unmakeMoves() {
        moveResults.forEach { moveResult ->
            if (moveResult is LegalMove) {
                field.unmakeMove()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareProperties(newProperties: PropertiesMap, existingProperties: PropertiesMap): Boolean {
        for ((key, newProperty) in newProperties) {
            when (key) {
                GameTreeNode::player1Moves,
                GameTreeNode::player2Moves -> {
                    val newMoveInfos = newProperty.value as? List<MoveInfo>
                    val existingMoveInfos = existingProperties[key]?.value as? List<MoveInfo>
                    if (newMoveInfos != null && existingMoveInfos != null) {
                        if (newMoveInfos.size != existingMoveInfos.size) {
                            return false
                        }
                        newMoveInfos.zip(existingMoveInfos).forEach { (newMoveInfo, existingMoveInfo) ->
                            if (newMoveInfo.positionXY != existingMoveInfo.positionXY ||
                                newMoveInfo.player != existingMoveInfo.player ||
                                newMoveInfo.externalFinishReason != existingMoveInfo.externalFinishReason ||
                                newMoveInfo.parsedNode != null && newMoveInfo.parsedNode != existingMoveInfo.parsedNode
                            ) {
                                return false
                            }
                        }
                    }
                    else if (newMoveInfos == null) {
                        continue
                    }
                    else {
                        return false
                    }
                }

                GameTreeNode::player1TimeLeft,
                GameTreeNode::player2TimeLeft -> {
                    val newTimeLeft = newProperty.value as? Double
                    val existingTimeLeft = existingProperties[key]?.value as? Double
                    if (newTimeLeft != null && existingTimeLeft != null) {
                        if (newTimeLeft != existingTimeLeft) {
                            return false
                        }
                    } else if (newTimeLeft == null) {
                        continue
                    } else {
                        return false
                    }
                }

                PropertiesHolder::comment -> {
                    val newComment = newProperty.value as? String
                    val existingComment = existingProperties[key]?.value as? String
                    if (newComment != null && existingComment != null) {
                        if (newComment != existingComment) {
                            return false
                        }
                    } else if (newComment == null) {
                        continue
                    } else {
                        return false
                    }
                }
            }
        }

        // Allow merging since existing properties holds more of same number of properties
        return true
    }
}

class GameTreeNode internal constructor(
    val previousNode: GameTreeNode?,
    val number: Int,
    properties: PropertiesMap,
    parsedNode: ParsedNode? = null,
    val children: MutableList<GameTreeNode> = mutableListOf(),
) : PropertiesHolder(properties, parsedNode) {
    // Used to handle rollback correctly
    var moveResults: List<MoveResult> = listOf()
        internal set

    internal fun createChild(properties: PropertiesMap, parsedNode: ParsedNode? = null): GameTreeNode {
        return GameTreeNode(this, this.number + 1, properties, parsedNode).also {
            children.add(it)
        }
    }

    val player1Moves: List<MoveInfo>? by PropertyDelegate()
    val player2Moves: List<MoveInfo>? by PropertyDelegate()
    val labels: List<Label>? by PropertyDelegate()
    val circles: List<PositionXY>? by PropertyDelegate()
    val squares: List<PositionXY>? by PropertyDelegate()

    val isRoot = previousNode == null

    override fun toString(): String {
        return buildString {
            append("#$number; ")
            player1Moves?.let { moveInfos ->
                append(moveInfos.joinToString { it.toString() })
            }
            player2Moves?.let { moveInfos ->
                append(moveInfos.joinToString { it.toString() })
            }
        }
    }
}


