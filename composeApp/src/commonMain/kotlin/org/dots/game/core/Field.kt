package org.dots.game.core

class Field(val rules: Rules = Rules.Standard, onIncorrectInitialPosition: (Position, Player) -> Unit = { _, _ -> }) {
    companion object {
        const val OFFSET: Int = 1
        const val MAX_WIDTH = (1 shl Position.COORDINATE_BITS_COUNT) - 2
        const val MAX_HEIGHT = (1 shl Position.COORDINATE_BITS_COUNT) - 2
    }

    val width: Int = rules.width
    val height: Int = rules.height
    val realWidth: Int = width + OFFSET * 2
    val realHeight: Int = height + OFFSET * 2
    val initialMovesCount: Int

    // Initialize field as a primitive int array with expanded borders to get rid of extra range checks and boxing
    private val dots: IntArray = IntArray(realWidth * realHeight) { DotState.Empty.value }

    private val moveResults = mutableListOf<MoveResult>()

    init {
        require(width > 0 && height > 0 && width < MAX_WIDTH && height < MAX_HEIGHT)

        if (rules.captureByBorder) {
            val borderState = DotState.createBorderState()
            for (x in 0 until realWidth) {
                Position(x, 0).setState(borderState)
                Position(x, realHeight - 1).setState(borderState)
            }
            for (y in 0 until realHeight) {
                Position(0, y).setState(borderState)
                Position(realWidth - 1, y).setState(borderState)
            }
        }

        fun initializePosition(positions: List<Position>, player: Player) {
            for (position in positions) {
                if (makeMove(position, player) == null) {
                    onIncorrectInitialPosition(position, player)
                }
            }
        }

        initializePosition(rules.player1InitialPositions, Player.First)
        initializePosition(rules.player2InitialPositions, Player.Second)

        initialMovesCount = moveResults.size
    }

    val moveSequence: List<MoveResult> = moveResults

    val lastMove: MoveResult?
        get() = moveResults.lastOrNull()

    val currentMoveNumber: Int
        get() = moveResults.count()

    var player1Score: Int = 0
        private set

    var player2Score: Int = 0
        private set

    /**
     * Valid real positions starts from `1`, but not from `0`. `0` is reserved for internal purposes.
     */
    fun makeMove(position: Position, player: Player? = null): MoveResult? {
        val position = positionIfWithinBoundsAndFree(position) ?: return null
        return makeMoveUnsafe(position, player)
    }

    fun positionIfWithinBoundsAndFree(position: Position): Position? {
        val (x, y) = position
        return if (x < OFFSET || x >= width + OFFSET || y < OFFSET || y >= height + OFFSET) null else Position(x, y).takeIf {
            it.getState().checkValidMove()
        }
    }

    fun getCurrentPlayer(player: Player?): Player {
        return player ?: moveResults.lastOrNull()?.player?.opposite() ?: Player.First
    }

    fun unmakeMove(): MoveResult? {
        if (currentMoveNumber == 0) return null

        val moveResult = moveResults.removeLast()

        for (base in moveResult.bases) {
            for ((position, previousState) in base.territoryPreviousStates) {
                position.setState(previousState)
            }
            updateScoreCount(base.player, base.playerDiff, base.oppositePlayerDiff, rollback = true)
        }

        moveResult.position.setState(moveResult.positionPreviousState)

        return moveResult
    }

    internal fun makeMoveUnsafe(position: Position, player: Player? = null): MoveResult? {
        val currentPlayer = getCurrentPlayer(player)

        val originalState = position.getState()

        val state = currentPlayer.createPlacedState()
        position.setState(state)

        val bases = tryCapture(position, state)

        val resultBases = if (bases.isEmpty() && originalState.checkWithinEmptyTerritory(currentPlayer.opposite())) {
            // Check capturing by opposite player (rare case)
            listOf(captureEmptyTerritory(position, currentPlayer.opposite().createPlacedState()))
        } else {
            bases
        }

        return MoveResult(position, currentPlayer, currentMoveNumber, originalState, resultBases).also { moveResults.add(it) }
    }

    /**
     * Rare case, don't care about performance much
     */
    private fun captureEmptyTerritory(initialPosition: Position, oppositePlayerPlaced: DotState): Base {
        var (x, y) = initialPosition

        while (true) {
            x--
            val position = Position(x, y)
            if (!position.getState().checkActive(oppositePlayerPlaced)) continue

            val startPosition = getUnconnectedPositions(position, oppositePlayerPlaced).firstOrNull() ?: continue

            tryGetCounterCounterClockwiseClosure(position, startPosition, oppositePlayerPlaced)?.let {
                val base = buildBase(it.territoryFirstPosition, it.closure, oppositePlayerPlaced)
                if (!base.isEmpty) {
                    return base
                }
            }
        }

        error("Enemy's empty territory should be enclosed by an outer closure")
    }

    private fun tryCapture(position: Position, playerPlaced: DotState): List<Base> {
        val unconnectedPositions = getUnconnectedPositions(position, playerPlaced)
        if (unconnectedPositions.size < 2) return emptyList()

        val closures = buildList {
            for (unconnectedPosition in unconnectedPositions) {
                tryGetCounterCounterClockwiseClosure(position, unconnectedPosition, playerPlaced)?.let { add(it) }
            }
        }

        val resultClosures = if (rules.captureByBorder) {
            // Ignore bound closure with max square
            val topLeftBoundClosureWithMaxSquare = calculateTopLeftBoundClosureWithMaxSquare(closures)
            closures.filter { it != topLeftBoundClosureWithMaxSquare }
        } else {
            closures
        }

        return resultClosures.map { buildBase(it.territoryFirstPosition, it.closure, playerPlaced) }
    }

    private fun calculateTopLeftBoundClosureWithMaxSquare(closures: List<ClosureData>): ClosureData? {
        var result: ClosureData? = null
        for (closure in closures) {
            if (!closure.containsBorder) continue

            if (result == null) {
                result = closure
                continue
            }

            val squareDiff = closure.square - result.square
            if (squareDiff > 0) {
                result = closure
                continue
            }

            if (squareDiff < 0) continue

            // Very rare case when a placed dot split the entire field into two equal parts
            // But we anyway need to formalize it
            val minPositionDiff = closure.closure.minOf { it.squareDistanceTo(Position.ZERO) } -
                    result.closure.minOf { it.squareDistanceTo(Position.ZERO) }
            if (minPositionDiff > 0) {
                result = closure
                continue
            }

            if (minPositionDiff < 0) continue

            // The rarer case when a placed dot split the entire field by diagonal chain
            // that starts from top-left corner to the bottom-right corner
            val diffX = closure.closure.minOf { it.x } - result.closure.minOf { it.x }
            if (diffX > 0) {
                result = closure
            }

            // Impossible case
            require(diffX != 0)
        }
        return result
    }

    private fun getUnconnectedPositions(position: Position, playerPlaced: DotState): List<Position> {
        val startPositions = mutableListOf<Position>()

        val (x, y) = position
        val xMinusOneY = Position(x - 1, y)
        val xYMinusOne = Position(x, y - 1)
        val xPlusOneY = Position(x + 1, y)
        val xYPlusOne = Position(x, y + 1)
        val xMinusOneYState = xMinusOneY.getState()
        val xYMinusOneState = xYMinusOne.getState()
        val xPlusOneYState = xPlusOneY.getState()
        val xYPlusOneState = xYPlusOne.getState()

        fun checkAndAdd(checkState: DotState, addPosition1: Position, addPosition2: Position, addPosition2State: DotState) {
            if (!checkState.checkActive(playerPlaced)) {
                if (addPosition1.getState().checkActive(playerPlaced)) {
                    startPositions.add(addPosition1)
                } else if (addPosition2State.checkActive(playerPlaced)) {
                    startPositions.add(addPosition2)
                }
            }
        }

        checkAndAdd(xPlusOneYState, Position(x + 1, y + 1), xYPlusOne, xYPlusOneState)
        checkAndAdd(xYPlusOneState, Position(x - 1, y + 1), xMinusOneY, xMinusOneYState)
        checkAndAdd(xMinusOneYState, Position(x - 1, y - 1), xYMinusOne, xYMinusOneState)
        checkAndAdd(xYMinusOneState, Position(x + 1, y - 1), xPlusOneY, xPlusOneYState)

        return startPositions
    }

    private fun buildBase(
        territoryFirstPosition: Position,
        closurePositions: List<Position>,
        playerPlaced: DotState,
    ): Base {
        val territoryPositions = getPositionsWithinClosure(territoryFirstPosition, closurePositions, playerPlaced)
        return updateStatesAndScores(closurePositions, territoryPositions, playerPlaced)
    }

    private data class ClosureData(
        val square: Int,
        val territoryFirstPosition: Position,
        val closure: List<Position>,
        val containsBorder: Boolean,
    )

    private fun tryGetCounterCounterClockwiseClosure(initialPosition: Position, startPosition: Position, playerPlaced: DotState): ClosureData? {
        val closurePositions = mutableListOf<Position>(initialPosition, startPosition)
        var square = initialPosition.getSquare(startPosition)

        var currentPosition: Position = startPosition
        var nextPosition: Position = initialPosition

        var territoryFirstPosition: Position? = null
        var containsBorder = false

        loop@ do {
            val clockwiseWalkCompleted = currentPosition.clockwiseWalk(nextPosition) {
                val state  = it.getState()
                val isActive = state.checkActive(playerPlaced)

                if (territoryFirstPosition == null) {
                    require(!isActive)
                    territoryFirstPosition = it
                }

                if (isActive) {
                    square += currentPosition.getSquare(it)

                    if (it == initialPosition) {
                        break@loop
                    }

                    if (state.checkBorder()) {
                        containsBorder = true
                    }

                    closurePositions.add(it)
                    nextPosition = currentPosition
                    currentPosition = it
                    return@clockwiseWalk false
                }
                return@clockwiseWalk true
            }
            if (clockwiseWalkCompleted) {
                return null
            }
        } while (true)

        return if (square > 0) ClosureData(square, territoryFirstPosition, closurePositions, containsBorder) else null
    }

    private fun getPositionsWithinClosure(
        territoryFirstPosition: Position,
        closurePositions: List<Position>,
        playerPlaced: DotState
    ): Set<Position> {
        val walkStack = mutableListOf<Position>()
        val closurePositionsSet = closurePositions.toSet()
        val territoryPositions = mutableSetOf<Position>()
        val playerTerritory = playerPlaced.getPlacedPlayer().createTerritoryState()

        fun Position.checkAndAdd() {
            if (this.getState().checkTerritory(playerTerritory)) return // Ignore already captured territory
            if (this in closurePositionsSet) return
            if (!territoryPositions.add(this)) return

            walkStack.add(this)
        }

        territoryFirstPosition.checkAndAdd()

        while (walkStack.isNotEmpty()) {
            val currentPosition = walkStack.removeLast()

            val (x, y) = currentPosition
            Position(x, y - 1).checkAndAdd()
            Position(x + 1, y).checkAndAdd()
            Position(x, y + 1).checkAndAdd()
            Position(x - 1, y).checkAndAdd()
        }

        return territoryPositions
    }

    private fun updateStatesAndScores(
        closurePositions: List<Position>,
        territoryPositions: Set<Position>,
        playerPlaced: DotState
    ): Base {
        val territoryPreviousStates = territoryPositions.associate { it to it.getState() }

        var currentPlayerDiff = 0
        var oppositePlayerDiff = 0

        val player = playerPlaced.getPlacedPlayer()
        val oppositePlayer = player.opposite()
        val oppositePlayerPlaced = oppositePlayer.createPlacedState()
        val oppositePlayerTerritory = oppositePlayer.createTerritoryState()

        fun DotState.updateScoreDiff() {
            if (checkPlaced(oppositePlayerPlaced)) {
                currentPlayerDiff++
            }

            // No diff for the territory of the current player
            if (checkTerritory(oppositePlayerTerritory) && checkPlaced(playerPlaced)) {
                oppositePlayerDiff--
            }
        }

        for (territoryPosition in territoryPositions) {
            territoryPosition.getState().updateScoreDiff()
        }

        val nonCapturingTerritory = currentPlayerDiff == 0 && oppositePlayerDiff == 0 && !rules.captureEmptyBase

        val playerTerritory = player.createTerritoryState()
        val playerEmptyTerritory = player.createEmptyTerritoryState()

        for (territoryPosition in territoryPositions) {
            val territoryPositionState = territoryPosition.getState()
            if (nonCapturingTerritory) {
                if (territoryPositionState.checkValidMove()) {
                    territoryPosition.setState(playerEmptyTerritory)
                }
            } else {
                territoryPosition.setState(territoryPositionState.setTerritory(playerTerritory))
            }
        }

        updateScoreCount(player, currentPlayerDiff, oppositePlayerDiff, rollback = false)

        return Base(player, currentPlayerDiff, oppositePlayerDiff, closurePositions, territoryPreviousStates, nonCapturingTerritory)
    }

    private fun updateScoreCount(player: Player, currentPlayerDiff: Int, oppositePlayerDiff: Int, rollback: Boolean) {
        val multiplier = if (rollback) -1 else 1
        if (player == Player.First) {
            player1Score += currentPlayerDiff * multiplier
            player2Score += oppositePlayerDiff * multiplier
        } else {
            player1Score += oppositePlayerDiff * multiplier
            player2Score += currentPlayerDiff * multiplier
        }
    }

    internal fun Position.getState(): DotState {
        return DotState(dots[y * realWidth + x])
    }

    private fun Position.setState(state: DotState) {
        this@Field.dots[y * realWidth + x] = state.value
    }
}

data class MoveResult(
    val position: Position,
    val player: Player,
    val number: Int,
    val positionPreviousState: DotState,
    val bases: List<Base>,
)

class Base(
    val player: Player,
    val playerDiff: Int,
    val oppositePlayerDiff: Int,
    val closurePositions: List<Position>,
    val territoryPreviousStates: Map<Position, DotState>,
    val isEmpty: Boolean,
)
