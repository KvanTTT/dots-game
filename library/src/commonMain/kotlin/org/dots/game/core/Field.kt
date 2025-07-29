package org.dots.game.core

import org.dots.game.core.PositionXY.Companion.COORDINATE_BITS_COUNT
import render
import kotlin.jvm.JvmInline

class Field {
    companion object {
        const val OFFSET: Int = 1
        // Max field size is 62 * 62 (2 positions are reserved for a border)
        const val MAX_WIDTH = (1 shl COORDINATE_BITS_COUNT) - 2
        const val MAX_HEIGHT = (1 shl COORDINATE_BITS_COUNT) - 2

        fun checkWidth(value: Int): Boolean = value in 0..MAX_WIDTH
        fun checkHeight(value: Int): Boolean = value in 0..MAX_HEIGHT

        fun getStride(width: Int): Int = width + OFFSET * 2

        fun create(rules: Rules, onIncorrectInitialMove: (moveInfo: MoveInfo, incorrectMove: Boolean, moveNumber: Int) -> Unit = { _, _, _ -> }): Field {
            return Field(rules).apply {
                for (moveInfo in rules.initialMoves) {
                    val position = getPositionIfWithinBounds(moveInfo.positionXY)

                    if (position == null) {
                        onIncorrectInitialMove(moveInfo, false, currentMoveNumber)
                        continue
                    }

                    if (makeMoveUnsafe(position, moveInfo.player) == null) {
                        onIncorrectInitialMove(moveInfo, true, currentMoveNumber)
                    }
                }

                initialMovesCount = moveResults.size
            }
        }
    }

    private constructor(rules: Rules) {
        this.rules = rules
        this.width = rules.width
        this.height = rules.height
        this.realWidth = width + OFFSET * 2
        this.realHeight = height + OFFSET * 2
        this.dots = IntArray(realWidth * realHeight) {
            val x = it % realWidth
            val y = it / realWidth
            if (x == 0 || x == realWidth - 1 || y == 0 || y == realHeight - 1) {
                DotState.Wall.value
            } else {
                DotState.Empty.value
            }
        }
        this.numberOfLegalMoves = width * height
        require(checkWidth(width) && checkHeight(height))

        captureByBorder = rules.captureByBorder
    }

    val rules: Rules
    val width: Int
    val height: Int
    val realWidth: Int
    val realHeight: Int
    var initialMovesCount: Int = 0
        private set
    val captureByBorder: Boolean

    // Use primitive int array with expanded borders to get rid of extra range checks and boxing
    private val dots: IntArray

    private val moveResults = mutableListOf<MoveResult>()

    var numberOfLegalMoves: Int
        private set

    var gameResult: GameResult? = null
        private set

    var player1Score: Int = 0
        private set

    var player2Score: Int = 0
        private set

    private val startPositionsList = ArrayList<Position>(4)
    private val closuresList = ArrayList<ClosureData>(4)
    private val closurePositions: ArrayList<Position> = ArrayList()

    fun clone(): Field {
        val new = Field(rules)
        new.initialMovesCount = initialMovesCount
        dots.copyInto(new.dots)
        new.moveResults.clear()
        new.moveResults.addAll(moveResults)
        new.numberOfLegalMoves = numberOfLegalMoves
        new.gameResult = gameResult
        new.player1Score = player1Score
        new.player2Score = player2Score
        return new
    }

    fun transform(transformType: TransformType): Field {
        val newWidth: Int
        val newHeight: Int
        when (transformType) {
            TransformType.RotateCw90,
            TransformType.RotateCw270 -> {
                newWidth = height
                newHeight = width
            }
            TransformType.Rotate180,
            TransformType.FlipHorizontal,
            TransformType.FlipVertical -> {
                newWidth = width
                newHeight = height
            }
        }

        val newFieldStride = getStride(newWidth)
        fun Position.transform() = transform(transformType, realWidth, realHeight, newFieldStride)

        val newField = Field(Rules(
            width = newWidth,
            height = newHeight,
            captureByBorder = rules.captureByBorder,
            baseMode = rules.baseMode,
            suicideAllowed = rules.suicideAllowed,
            initialMoves = rules.initialMoves.map {
                MoveInfo(it.positionXY.transform(transformType, width, height, newFieldStride), it.player, it.extraInfo)
            },
        ))
        newField.initialMovesCount = initialMovesCount

        for (posIndex in 0..<dots.size) {
            val position = Position(posIndex)
            val oldState = position.getState()
            with(newField) {
                position.transform().setState(oldState)
            }
        }

        for (moveResult in moveResults) {
            val position = moveResult.position

            fun List<PositionState>.transformPositionStates(): List<PositionState> {
                return map { (position, state) -> PositionState(position.transform(), state) }
            }

            newField.moveResults.add(MoveResult(
                position.transform(),
                moveResult.player,
                moveResult.previousState,
                extraPreviousPositionStates = moveResult.extraPreviousPositionStates?.transformPositionStates(),
                bases = moveResult.bases?.map { base ->
                    Base(
                        base.player,
                        base.playerDiff,
                        base.oppositePlayerDiff,
                        closurePositions = base.closurePositions.map { it.transform() },
                        previousPositionStates = base.previousPositionStates.transformPositionStates(),
                        isReal = base.isReal
                    )
                },
                previousNumberOfLegalMoves = moveResult.previousNumberOfLegalMoves
            ))
        }
        newField.numberOfLegalMoves = numberOfLegalMoves
        newField.gameResult = gameResult
        newField.player1Score = player1Score
        newField.player2Score = player2Score
        return newField
    }

    fun isGameOver(): Boolean = gameResult != null

    val moveSequence: List<MoveResult> = moveResults

    val currentMoveNumber: Int
        get() = moveResults.size - initialMovesCount

    val lastMove: MoveResult?
        get() = moveResults.lastOrNull()

    fun getCurrentPlayer(): Player = moveResults.lastOrNull()?.player?.opposite() ?: Player.First

    fun getScoreDiff(player: Player? = null): Int {
        return if ((player ?: getCurrentPlayer()) == Player.First) { player1Score - player2Score } else { player2Score - player1Score }
    }

    fun makeMove(positionXY: PositionXY, player: Player? = null): MoveResult? {
        val (x, y) = positionXY
        return makeMove(x, y, player)
    }

    /**
     * Valid real positions starts from `1`, but not from `0`.
     * `0` is reserved for the initial position (cross, empty or other).
     */
    fun makeMove(x: Int, y: Int, player: Player? = null): MoveResult? {
        val position = getPositionIfWithinBounds(x, y)
        return if (position != null) makeMoveUnsafe(position, player) else null
    }

    fun finishGame(externalFinishReason: ExternalFinishReason, player: Player?): GameResult? {
        if (gameResult != null) return null

        val currentPlayer = player ?: getCurrentPlayer()

        var resultBases: List<Base>? = null
        var extraPreviousStates: List<PositionState>? = null

        gameResult = when (externalFinishReason) {
            ExternalFinishReason.Grounding -> {
                require(rules.baseMode != BaseMode.AllOpponentDots) {
                    "${BaseMode.AllOpponentDots::class.simpleName} is not yet supported (it requires handling of immortal groups that have two or more eyes)"
                }
                require(!rules.captureByBorder) {
                    "${rules.captureByBorder::class.simpleName} is not yet supported"
                }

                val (localResultBases, localExtraPreviousStates) =
                    captureNotGroundedGroups(currentPlayer)
                resultBases = localResultBases.takeIf { it.isNotEmpty() }
                extraPreviousStates = localExtraPreviousStates.takeIf { it.isNotEmpty() }

                finishGame(EndGameKind.Grounding)
            }
            ExternalFinishReason.Draw -> GameResult.Draw(endGameKind = null)
            ExternalFinishReason.Resign -> GameResult.ResignWin(currentPlayer.opposite())
            ExternalFinishReason.Time -> GameResult.TimeWin(currentPlayer.opposite())
            ExternalFinishReason.Interrupt -> GameResult.InterruptWin(currentPlayer.opposite())
            ExternalFinishReason.Unknown -> GameResult.UnknownWin(currentPlayer.opposite())
        }

        moveResults.add(MoveResult(
            Position.GAME_OVER,
            currentPlayer,
            Position.GAME_OVER.getState(),
            extraPreviousStates,
            resultBases,
            numberOfLegalMoves,
        ).also {
            numberOfLegalMoves = 0
        })

        return gameResult
    }

    fun getPositionIfWithinBounds(positionXY: PositionXY): Position? {
        return getPositionIfWithinBounds(positionXY.x, positionXY.y)
    }

    fun getPositionIfWithinBounds(x: Int, y: Int): Position? {
        return if (x >= OFFSET && x < width + OFFSET && y >= OFFSET && y < height + OFFSET) {
            Position(x, y, realWidth)
        } else {
            null
        }
    }

    fun unmakeAllMoves() {
        while (currentMoveNumber > 0) {
            unmakeMove()
        }
    }

    fun unmakeMove(): MoveResult? {
        if (currentMoveNumber == 0) return null

        val moveResult = moveResults.removeLast()

        if (moveResult.bases != null) {
            for (base in moveResult.bases.reversed()) {
                for ((position, previousState) in base.previousPositionStates) {
                    position.setState(previousState)
                }
                updateScoreCount(base.player, base.playerDiff, base.oppositePlayerDiff, rollback = true)
            }
        }

        moveResult.position.setState(moveResult.previousState)
        if (moveResult.extraPreviousPositionStates != null) {
            for ((position, previousState) in moveResult.extraPreviousPositionStates) {
                position.setState(previousState)
            }
        }

        numberOfLegalMoves = moveResult.previousNumberOfLegalMoves
        gameResult = null

        return moveResult
    }

    fun getPositionIfValid(x: Int, y: Int, player: Player?): Position? {
         val position = getPositionIfWithinBounds(x, y) ?: return null

        val state = position.getState()
        val currentPlayer = player ?: getCurrentPlayer()
        if (state.getActivePlayer() == Player.None &&
            (rules.suicideAllowed ||
                    rules.baseMode == BaseMode.AtLeastOneOpponentDot &&
                    state.getEmptyTerritoryPlayer() != currentPlayer.opposite())
        ) {
            /**
             * Optimization: no need to check if the suicide is allowed
             * or the position inside a valid empty position
             * TODO: currently empty territory isn't supported only for [BaseMode.AllOpponentDots]
             * If they are implemented for this mode, the base mode check can be removed
             */
            return position
        }

        // Otherwise we have to check the validity by emulating move placing and rollback afterward
        return makeMoveUnsafe(position, player).let { moveResult ->
            if (moveResult != null) {
                unmakeMove()
                position
            } else {
                null
            }
        }
    }

    fun makeMoveUnsafe(position: Position, player: Player? = null): MoveResult? {
        if (isGameOver()) return null

        val originalState: DotState = position.getState()
        if (originalState.getActivePlayer() != Player.None) return null

        val currentPlayer = player ?: getCurrentPlayer()
        val resultBases: List<Base>
        val extraPreviousStates: List<PositionState>
        val previousNumberOfLegalMoves: Int = numberOfLegalMoves

        numberOfLegalMoves--

        position.setState(DotState.createPlaced(currentPlayer))

        val bases = tryCapture(position, currentPlayer, emptyBaseCapturing = false)

        // Handle possible suicidal moves.
        if (bases.isEmpty()) {
            resultBases = if (rules.baseMode != BaseMode.AllOpponentDots) {
                val oppositePlayer = currentPlayer.opposite()
                if (originalState.getEmptyTerritoryPlayer() == oppositePlayer) {
                    if (rules.suicideAllowed) {
                        // Check capturing by the opposite player
                        val oppositeBase =
                            captureWhenEmptyTerritoryBecomesRealBase(position, oppositePlayer)
                        listOf(oppositeBase)
                    } else {
                        position.setState(originalState)
                        return null
                    }
                } else {
                    bases
                }
            } else {
                tryGetBaseForAllOpponentDotsMode(
                    position,
                    currentPlayer.opposite(),
                    capturingByOppositePlayer = true
                )?.let { (base, suicidalMove) ->
                    if (suicidalMove) {
                        // Rollback state in case of a suicidal move
                        position.setState(originalState)
                        return null
                    } else {
                        base?.let { listOf(it) } ?: bases
                    }
                } ?: bases
            }
            extraPreviousStates = emptyList() // Not used in `AllOpponentDots` mode
        } else {
            resultBases = bases
            extraPreviousStates = if (rules.baseMode != BaseMode.AllOpponentDots &&
                originalState.isWithinEmptyTerritory(currentPlayer.opposite())
            ) {
                // Invalidate empty territory of the opposite player in case of capturing that is more prioritized
                // It makes the positions legal for further game.
                invalidateEmptyTerritory(position)
            } else {
                emptyList()
            }
        }

        for (resultBase in resultBases) {
            for ((_, previousState) in resultBase.previousPositionStates) {
                if (!previousState.isActive()) {
                    if (resultBase.isReal) {
                        numberOfLegalMoves--
                    } else {
                        // TODO: implement for suicidal case
                    }
                }
            }
        }

        if (numberOfLegalMoves == 0) {
            gameResult = finishGame(EndGameKind.NoLegalMoves)
        }

        return MoveResult(
            position,
            currentPlayer,
            originalState,
            extraPreviousStates.takeIf { it.isNotEmpty() },
            resultBases.takeIf { it.isNotEmpty() },
            previousNumberOfLegalMoves,
        ).also { moveResults.add(it) }
    }

    private fun finishGame(endGameKind: EndGameKind): GameResult {
        val scoreForFirstPlayer = getScoreDiff(Player.First)
        return if (scoreForFirstPlayer == 0) {
            GameResult.Draw(endGameKind)
        } else {
            val winner: Player
            val score: Int
            if (scoreForFirstPlayer > 0) {
                winner = Player.First
                score = scoreForFirstPlayer
            } else {
                winner = Player.Second
                score = -scoreForFirstPlayer
            }
            GameResult.ScoreWin(score.toDouble(), endGameKind, winner)
        }
    }

    private fun captureNotGroundedGroups(player: Player): Pair<List<Base>, List<PositionState>> {
        val processedPositions = mutableListOf<Position>()
        val oppositePlayer = player.opposite()
        val extraPreviousStates = mutableListOf<PositionState>()

        val bases = mutableListOf<Base>()

        for (move in moveResults) {
            val position = move.position

            if (position.getState().let { !it.isVisited() && it.isActive(player) }) {
                var grounded = false
                val territoryPositions = getTerritoryPositions(oppositePlayer, position) {
                    val state = it.getState()
                    if (state.getActivePlayer() == Player.WallOrBoth) {
                        grounded = true
                        false
                    } else {
                        state.isActive(player)
                    }
                }

                processedPositions.addAll(territoryPositions)

                if (!grounded) {
                    for (position in territoryPositions) {
                        position.forEachAdjacent(realWidth) {
                            if (it.getState().isWithinEmptyTerritory(player)) {
                                extraPreviousStates.addAll(invalidateEmptyTerritory(position))
                                false
                            } else {
                                true
                            }
                        }
                    }
                    bases.add(
                        calculateBase(
                            closurePositions = emptyList(),
                            territoryPositions,
                            oppositePlayer,
                        )
                    )
                }
            }
        }

        processedPositions.clearVisited()

        return bases to extraPreviousStates
    }

    private fun captureWhenEmptyTerritoryBecomesRealBase(initialPosition: Position, oppositePlayer: Player): Base {
        var position = initialPosition

        // Searching for an opponent dot that makes a closure that contains the `initialPosition`.
        // The closure always exists, otherwise there is an error in previous calculations.
        while (position.value > 0) {
            position = position.xm1y()

            // Try to peek an active opposite player dot
            if (!position.getState().checkActiveAndWall(oppositePlayer)) continue

            val oppositePlayerBased = tryCapture(position, oppositePlayer, emptyBaseCapturing = true)
            // The found base always should be real and include the `initialPosition`
            return oppositePlayerBased.firstOrNull { it.isReal } ?: continue
        }

        error("Enemy's empty territory should be enclosed by an outer closure at $initialPosition")
    }

    private fun tryCapture(position: Position, player: Player, emptyBaseCapturing: Boolean): List<Base> {
        return if (rules.baseMode != BaseMode.AllOpponentDots) {
            getUnconnectedPositions(position, player)

            // Optimization: in a regular case it should be at least 2 connection dots, otherwise there is no surrounding.
            // However, in the case of empty territory, the connection might be singular since the base is already built.
            val minNumberOfConnections = if (emptyBaseCapturing) 1 else 2
            if (startPositionsList.size < minNumberOfConnections) return emptyList()

            val closuresData = closuresList.apply {
                clear()
                for (unconnectedPosition in startPositionsList) {
                    // Optimization: it doesn't make sense to check the latest unconnected dot
                    // when all previous connections form minimal bases
                    // because the latest always forms a base with maximal square that should be dropped
                    if (!rules.captureByBorder && isNotEmpty() && size == startPositionsList.size - 1) {
                        break
                    }

                    tryGetCounterCounterClockwiseClosure(position, unconnectedPosition, player)?.let {
                        var added = false
                        for (closureDataIndex in 0 until size) {
                            if (it.closure.size < this[closureDataIndex].closure.size) {
                                add(closureDataIndex, it)
                                added = true
                                break
                            }
                        }
                        if (!added) {
                            add(it)
                        }
                    }
                }
            }

            val resultClosures = if (rules.captureByBorder) {
                // Ignore bound closure with max square
                val topLeftBoundClosureWithMaxSquare = calculateTopLeftBoundClosureWithMaxSquare(closuresData)
                closuresData.filter { it != topLeftBoundClosureWithMaxSquare }
            } else {
                closuresData
            }

            resultClosures.map { buildBase(player, it.closure) }
        } else {
            getOppositeAdjacentPositions(position, player)

            ArrayList<Base>(4).apply {
                for (oppositeAdjacentPosition in startPositionsList) {
                    tryGetBaseForAllOpponentDotsMode(oppositeAdjacentPosition, player, capturingByOppositePlayer = false)?.let {
                        require(!it.suicidalMove)
                        add(it.base!!)
                    }
                }
            }
        }
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
            val minPositionDiff = closure.closure.minOf { it.squareDistanceToZero(realWidth) } -
                    result.closure.minOf { it.squareDistanceToZero(realWidth) }
            if (minPositionDiff > 0) {
                result = closure
                continue
            }

            if (minPositionDiff < 0) continue

            // The rarer case when a placed dot split the entire field by diagonal chain
            // that starts from top-left corner to the bottom-right corner
            val diffX = closure.closure.minOf { it.getX(realWidth) } - result.closure.minOf { it.getX(realWidth) }
            if (diffX > 0) {
                result = closure
            }

            // Impossible case
            require(diffX != 0)
        }
        return result
    }

    /**
     * Returns the number of connections that should be checked on surrounding: `1` for a singular dot, `4` for the following case:
     *
     * ```
     * + . +
     * . o .
     * + . +
     * ```
     *
     * Where `o` is the checking @param [position].
     */
    private fun getUnconnectedPositions(position: Position, player: Player) {
        startPositionsList.clear()

        val xMinusOneY = position.xm1y()
        val xYMinusOne = position.xym1(realWidth)
        val xPlusOneY = position.xp1y()
        val xYPlusOne = position.xyp1(realWidth)
        val xMinusOneYState = xMinusOneY.getState()
        val xYMinusOneState = xYMinusOne.getState()
        val xPlusOneYState = xPlusOneY.getState()
        val xYPlusOneState = xYPlusOne.getState()

        fun checkAndAdd(
            checkState: DotState,
            addPosition1: Position,
            addPosition2: Position,
            addPosition2State: DotState
        ) {
            if (!checkState.checkActiveAndWall(player)) {
                if (addPosition1.getState().checkActiveAndWall(player)) {
                    startPositionsList.add(addPosition1)
                } else if (addPosition2State.checkActiveAndWall(player)) {
                    startPositionsList.add(addPosition2)
                }
            }
        }

        checkAndAdd(xPlusOneYState, position.xp1yp1(realWidth), xYPlusOne, xYPlusOneState)
        checkAndAdd(xYPlusOneState, position.xm1yp1(realWidth), xMinusOneY, xMinusOneYState)
        checkAndAdd(xMinusOneYState, position.xm1ym1(realWidth), xYMinusOne, xYMinusOneState)
        checkAndAdd(xYMinusOneState, position.xp1ym1(realWidth), xPlusOneY, xPlusOneYState)
    }

    private fun getOppositeAdjacentPositions(position: Position, player: Player) {
        startPositionsList.clear()
        val oppositePlayer = player.opposite()
        position.forEachAdjacent(realWidth) {
            if (it.getState().isActive(oppositePlayer)) {
                startPositionsList.add(it)
            }
            true
        }
    }

    private fun buildBase(player: Player, closurePositions: List<Position>): Base {
        closurePositions.forEach { it.setVisited() }
        val territoryFirstPosition = getNextPosition(closurePositions[1],closurePositions[0], realWidth)
        val territoryPositions = getTerritoryPositions(player, territoryFirstPosition) { true }
        closurePositions.clearVisited()
        territoryPositions.clearVisited()
        return calculateBase(closurePositions, territoryPositions, player)
    }

    private data class ClosureData(
        val square: Int,
        val closure: List<Position>,
        val containsBorder: Boolean,
    )

    private fun tryGetCounterCounterClockwiseClosure(
        initialPosition: Position,
        startPosition: Position,
        player: Player,
    ): ClosureData? {
        closurePositions.clear()
        closurePositions.add(initialPosition)
        closurePositions.add(startPosition)
        var square = initialPosition.getSquare(startPosition, realWidth)

        var currentPosition: Position = startPosition
        var nextPosition: Position = initialPosition

        var containsBorder = false

        loop@ do {
            val clockwiseWalkCompleted = currentPosition.clockwiseBigJumpWalk(nextPosition, realWidth) {
                val state = it.getState()
                val activePlayer = state.getActivePlayer().value

                val isActive = if (activePlayer == Player.WallOrBoth.value) {
                    if (!rules.captureByBorder) {
                        // Optimization: there is no need to walk anymore because the border can't enclosure anything
                        square = 0
                        break@loop
                    } else {
                        containsBorder = true
                        true
                    }
                } else {
                    activePlayer == player.value
                }

                if (isActive) {
                    square += currentPosition.getSquare(it, realWidth)

                    if (it.value == initialPosition.value) {
                        break@loop
                    }

                    closurePositions.add(it)
                    nextPosition = currentPosition
                    currentPosition = it
                    return@clockwiseBigJumpWalk false
                }
                return@clockwiseBigJumpWalk true
            }
            if (clockwiseWalkCompleted) {
                return null
            }
        } while (true)

        return if (square > 0) ClosureData(square, closurePositions.toList(), containsBorder) else null
    }

    private fun getTerritoryPositions(player: Player, firstPosition: Position, positionCheck: (Position) -> Boolean): List<Position> {
        val walkStack = mutableListOf<Position>()
        val territoryPositions = mutableListOf<Position>()

        fun Position.checkAndAdd() {
            val state = getState()
            if (state.isActiveAndTerritory(player)) return // Ignore already captured territory

            if (state.isVisited()) return
            if (!positionCheck(this)) return

            territoryPositions.add(this)
            setVisited()

            walkStack.add(this)
        }

        firstPosition.checkAndAdd()

        while (walkStack.isNotEmpty()) {
            walkStack.removeLast().forEachAdjacent(realWidth) {
                it.checkAndAdd()
                true
            }
        }

        return territoryPositions
    }

    private fun tryGetBaseForAllOpponentDotsMode(
        territoryFirstPosition: Position,
        player: Player,
        capturingByOppositePlayer: Boolean,
    ): BaseWithRollbackInfo? {
        require(rules.baseMode == BaseMode.AllOpponentDots)

        val oppositePlayer = player.opposite()

        if (territoryFirstPosition.getState().isActive(player)) {
            return null // Ignore already processed bases
        }

        val walkStack = mutableListOf<Position>()
        val territoryPositions = mutableListOf<Position>()
        val closurePositions = mutableListOf<Position>()
        var currentPlayerDiff = 0
        var oppositePlayerDiff = 0

        fun Position.checkAndAdd(): Boolean {
            val state = getState()
            val activePlayer = state.getActivePlayer()

            if (activePlayer == Player.WallOrBoth) {
                return if (!rules.captureByBorder) {
                    false
                } else {
                    closurePositions.add(this)
                    true
                }
            } else if (activePlayer == Player.None) {
                return false // early return if encounter an empty position
            }

            if (state.isVisited()) return true

            if (state.isPlaced(player)) {
                if (state.isActive(oppositePlayer)) {
                    oppositePlayerDiff--
                } else {
                    closurePositions.add(this)
                    return true
                }
            } else { // Opposite player placed
                if (!state.isActive(player)) {
                    currentPlayerDiff++
                }
            }

            setVisited()
            territoryPositions.add(this)
            walkStack.add(this)

            return true
        }

        if (!territoryFirstPosition.checkAndAdd()) {
            territoryPositions.clearVisited()
            return null
        }

        while (walkStack.isNotEmpty()) {
            if (!walkStack.removeLast().forEachAdjacent(realWidth) {
                it.checkAndAdd()
            }) {
                territoryPositions.clearVisited()
                return null
            }
        }

        territoryPositions.clearVisited()

        val base: Base?
        val suicidalMove: Boolean

        if (capturingByOppositePlayer && !rules.suicideAllowed) {
            base = null
            suicidalMove = true
        } else {
            base = createBaseAndUpdateStates(
                player,
                currentPlayerDiff,
                oppositePlayerDiff,
                ArrayList<Position>(closurePositions.size).apply { addAll(closurePositions) },
                territoryPositions,
                isReal = true,
            )
            suicidalMove = false
        }

        return BaseWithRollbackInfo(base, suicidalMove)
    }

    private data class BaseWithRollbackInfo(val base: Base?, val suicidalMove: Boolean)

    private fun calculateBase(
        closurePositions: List<Position>,
        territoryPositions: List<Position>,
        player: Player,
    ): Base {
        var currentPlayerDiff = 0
        var oppositePlayerDiff = 0

        val oppositePlayer = player.opposite()

        fun DotState.updateScoreDiff() {
            if (isPlaced(oppositePlayer)) {
                // The `getTerritoryPositions` never returns positions inside already owned territory,
                // so there is no need to check for the territory flag.
                currentPlayerDiff++
            } else if (isPlaced(player) && isActive(oppositePlayer)) {
                // No diff for the territory of the current player
                oppositePlayerDiff--
            }
        }

        for (territoryPosition in territoryPositions) {
            territoryPosition.getState().updateScoreDiff()
        }

        val isReal = when (rules.baseMode) {
            BaseMode.AtLeastOneOpponentDot -> currentPlayerDiff > 0
            BaseMode.AnySurrounding -> true
            BaseMode.AllOpponentDots -> error("The mode ${BaseMode.AllOpponentDots.name} is handled by ${::tryGetBaseForAllOpponentDotsMode.name}")
        }

        return createBaseAndUpdateStates(
            player,
            currentPlayerDiff,
            oppositePlayerDiff,
            closurePositions,
            territoryPositions,
            isReal,
        )
    }

    private fun createBaseAndUpdateStates(
        player: Player,
        currentPlayerDiff: Int,
        oppositePlayerDiff: Int,
        closurePositions: List<Position>,
        territoryPositions: List<Position>,
        isReal: Boolean,
    ): Base {
        val previousPositionStates = ArrayList<PositionState>(territoryPositions.size)

        updateScoreCount(player, currentPlayerDiff, oppositePlayerDiff, rollback = false)

        val playerEmptyTerritory = DotState.createEmptyTerritory(player)

        for (territoryPosition in territoryPositions) {
            val territoryPositionState = territoryPosition.getState()

            fun savePreviousStateAndSetNew(newState: DotState) {
                previousPositionStates.add(PositionState(territoryPosition, territoryPositionState))
                territoryPosition.setState(newState)
            }

            if (!isReal) {
                if (!territoryPositionState.isActive()) {
                    savePreviousStateAndSetNew(playerEmptyTerritory)
                }
            } else {
                savePreviousStateAndSetNew(territoryPositionState.setTerritory(player))
            }
        }

        return Base(
            player,
            currentPlayerDiff,
            oppositePlayerDiff,
            closurePositions,
            previousPositionStates,
            isReal,
        )
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

    /**
     * Invalidates states of an empty base that becomes broken.
     */
    private fun invalidateEmptyTerritory(position: Position): List<PositionState> {
        val walkStack = mutableListOf<Position>().also { it.add(position) }
        val emptyTerritoryPositions = hashMapOf<Position, DotState>()

        fun Position.checkAndAdd() {
            val state = getState()
            if (state.getEmptyTerritoryPlayer() == Player.None) return
            val existingTerritoryPosition = emptyTerritoryPositions[this]
            if (existingTerritoryPosition == null) {
                emptyTerritoryPositions[this] = state
                setState(DotState.Empty)
            } else {
                return
            }

            walkStack.add(this)
        }

        while (walkStack.isNotEmpty()) {
            walkStack.removeLast().forEachAdjacent(realWidth) {
                it.checkAndAdd()
                true
            }
        }

        return emptyTerritoryPositions.map { PositionState(it.key, it.value) }
    }

    fun Position.getState(): DotState {
        return DotState(dots[value])
    }

    private fun Position.setVisited() {
        setState(getState().setVisited())
    }

    private fun List<Position>.clearVisited() {
        forEach { it.setState(it.getState().clearVisited()) }
    }

    private fun Position.setState(state: DotState) {
        dots[value] = state.value
    }

    private fun DotState.checkActiveAndWall(player: Player): Boolean {
        return getActivePlayer().let { it == player || captureByBorder && it == Player.WallOrBoth }
    }

    override fun toString(): String = render()
}

data class MoveResult(
    val position: Position,
    val player: Player,
    val previousState: DotState,
    val extraPreviousPositionStates: List<PositionState>?,
    val bases: List<Base>?,
    val previousNumberOfLegalMoves: Int,
) {
    val positionPlayer: PositionPlayer
        get() = PositionPlayer(position, player)
}

enum class ExternalFinishReason {
    Grounding,
    Draw,
    Resign,
    Time,
    Interrupt,
    Unknown,
}

class Base(
    val player: Player,
    val playerDiff: Int,
    val oppositePlayerDiff: Int,
    val closurePositions: List<Position>,
    val previousPositionStates: List<PositionState>,
    val isReal: Boolean,
)

data class PositionPlayer(
    val position: Position,
    val player: Player,
)

@JvmInline
value class PositionState(val value: Int) {
    companion object {
        const val POSITION_BITS_COUNT = COORDINATE_BITS_COUNT * 2
        const val STATE_BITS_COUNT = 8
        const val STATE_MASK = (1 shl STATE_BITS_COUNT) - 1

        init {
            require(POSITION_BITS_COUNT + STATE_BITS_COUNT <= Int.SIZE_BITS)
        }
    }

    constructor(position: Position, state: DotState) : this((position.value shl STATE_BITS_COUNT) or (state.value and STATE_MASK))

    val position: Position
        get() = Position(value shr STATE_BITS_COUNT)

    val state: DotState
        get() = DotState(value and STATE_MASK)

    operator fun component1(): Position = position

    operator fun component2(): DotState = state

    override fun toString(): String {
        return "${position};${state}"
    }
}
