package org.dots.game.core

import org.dots.game.core.GameResult.ResignWin
import org.dots.game.core.GameResult.TimeWin
import org.dots.game.core.GameResult.UnknownWin
import org.dots.game.core.PositionXY.Companion.COORDINATE_BITS_COUNT
import org.dots.game.dump.DumpParameters
import org.dots.game.dump.render

class Field {
    companion object {
        const val OFFSET: Int = 1
        // Max field size is 62 * 62 (2 positions are reserved for a border)
        const val MAX_SIZE: Int = (1 shl COORDINATE_BITS_COUNT) - 2

        fun checkSize(value: Int): Boolean = value in 0..MAX_SIZE

        fun getStride(width: Int): Int = width + OFFSET

        fun create(rules: Rules, onIncorrectInitialMove: (moveInfo: MoveInfo, incorrectMove: Boolean, moveNumber: Int) -> Unit = { _, _, _ -> }): Field {
            return Field(rules).apply {
                for (moveInfo in rules.initialMoves + rules.remainingInitMoves) {
                    val position = if (moveInfo.positionXY != null) {
                        val (x, y) = moveInfo.positionXY
                        getPositionIfWithinBounds(x, y)
                    } else {
                        Position.GAME_OVER
                    }

                    if (position == null) {
                        onIncorrectInitialMove(moveInfo, false, currentMoveNumber)
                        continue
                    }

                    if (makeMoveUnsafe(position, moveInfo.player, moveInfo.externalFinishReason) !is LegalMove) {
                        onIncorrectInitialMove(moveInfo, true, currentMoveNumber)
                    }
                }

                initialMovesCount = legalMoves.size
            }
        }
    }

    private constructor(rules: Rules) {
        this.rules = rules
        this.width = rules.width
        this.height = rules.height
        this.realWidth = width + OFFSET + (if (rules.captureByBorder) OFFSET else 0)
        this.realHeight = height + OFFSET * 2
        this.size = realWidth * realHeight + (if (rules.captureByBorder) 0 else 1)
        this.dots = ByteArray(size) {
            val x = it % realWidth
            val y = it / realWidth
            if (x == 0 || y == 0 || rules.captureByBorder && x == realWidth - 1 || y == realHeight - 1) {
                DotState.Wall.value
            } else {
                DotState.Empty.value
            }
        }
        this.numberOfLegalMovesIfSuicideAllowed = width * height
        this.positionHash = ZobristHash.widthHash[width] xor ZobristHash.heightHash[height]
        require(checkSize(width) && checkSize(height))

        captureByBorder = rules.captureByBorder

        startPositionsBuffer = PositionsList(4, realWidth)
        closureOrInvalidatePositionsBuffer = PositionsList(size, realWidth)
        walkStackPositionsBuffer = PositionsList(size, realWidth)
        territoryPositionsBuffer = PositionsList(size, realWidth)
    }

    val rules: Rules
    val width: Int
    val height: Int
    val realWidth: Int
    val realHeight: Int
    val size: Int
    var initialMovesCount: Int = 0
        private set
    val captureByBorder: Boolean

    // Use primitive int array with expanded borders to get rid of extra range checks and boxing
    private val dots: ByteArray

    private val legalMoves = mutableListOf<LegalMove>()

    /**
     * The field is being updated iteratively.
     * It's relevant only to suicidal mode because overwise it's expensive or complicated to update it iteratively.
     */
    var numberOfLegalMovesIfSuicideAllowed: Int
        private set

    val gameResult: GameResult?
        get() = lastMove as? GameResult

    var player1Score: Int = 0
        private set

    var player2Score: Int = 0
        private set

    private val startPositionsBuffer: PositionsList
    private val closureOrInvalidatePositionsBuffer: PositionsList
    private val walkStackPositionsBuffer: PositionsList
    private val territoryPositionsBuffer: PositionsList

    private val closuresList = ArrayList<ClosureData>(4)

    var positionHash: Long
        private set

    var disabled: Boolean = false

    fun clone(): Field {
        val new = Field(rules)
        new.initialMovesCount = initialMovesCount
        dots.copyInto(new.dots)
        new.legalMoves.clear()
        new.legalMoves.addAll(legalMoves)
        new.numberOfLegalMovesIfSuicideAllowed = numberOfLegalMovesIfSuicideAllowed
        new.player1Score = player1Score
        new.player2Score = player2Score
        new.positionHash = positionHash
        return new
    }

    /**
     * Performs fast transformation without using base recalculating
     */
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
        fun Position.transform(): Position = transform(transformType, realWidth, realHeight, newFieldStride)

        val newField = Field(Rules.createAndDetectInitPos(
            width = newWidth,
            height = newHeight,
            captureByBorder = rules.captureByBorder,
            baseMode = rules.baseMode,
            suicideAllowed = rules.suicideAllowed,
            initialMoves = rules.initialMoves.map {
                MoveInfo(it.positionXY?.transform(transformType, width, height), it.player, it.externalFinishReason, parsedNode = it.parsedNode)
            },
            komi = rules.komi,
        ).rules)
        newField.initialMovesCount = initialMovesCount

        for (posIndex in 0..<dots.size - 1) {
            val position = Position(posIndex.toShort())
            val oldState = position.getState()
            with(newField) {
                val newPosition = position.transform()
                newPosition.setState(oldState)
                val activePlayer = oldState.getActivePlayer()
                if (activePlayer != Player.None) {
                    updatePositionHash(newPosition, activePlayer)
                }
            }
        }

        for (moveResult in legalMoves) {
            val position = moveResult.position

            newField.legalMoves.add(LegalMove(
                position.transform(),
                moveResult.player,
                moveResult.previousState,
                emptyBaseInvalidatePositions = moveResult.emptyBaseInvalidatePositions.map { it.transform() },
                bases = moveResult.bases.map { base ->
                    Base(
                        base.player,
                        closurePositions = base.closurePositions.map { it.transform() },
                        rollbackPositions = base.rollbackPositions.map { it.transform() },
                        rollbackDotStates = base.rollbackDotStates.copy(),
                        isReal = base.isReal
                    )
                },
            ))
        }
        newField.numberOfLegalMovesIfSuicideAllowed = numberOfLegalMovesIfSuicideAllowed
        newField.player1Score = player1Score
        newField.player2Score = player2Score
        return newField
    }

    fun isGameOver(): Boolean = gameResult != null

    val moveSequence: List<LegalMove> = legalMoves

    val currentMoveNumber: Int
        get() = legalMoves.size - initialMovesCount

    val lastMove: LegalMove?
        get() = legalMoves.lastOrNull()

    fun getCurrentPlayer(): Player = legalMoves.lastOrNull()?.player?.opposite() ?: Player.First

    fun getScoreDiff(player: Player? = null): Double {
        return if ((player ?: getCurrentPlayer()) == Player.First) {
            player1Score - player2Score - rules.komi
        } else {
            player2Score - player1Score + rules.komi
        }
    }

    fun makeMove(moveInfo: MoveInfo): MoveResult {
        return makeMove(moveInfo.positionXY, moveInfo.player, moveInfo.externalFinishReason)
    }

    fun makeMove(positionXY: PositionXY?, player: Player? = null, externalFinishReason: ExternalFinishReason? = null): MoveResult {
        val position = if (positionXY == null) {
            Position.GAME_OVER
        } else {
            getPositionIfWithinBounds(positionXY.x, positionXY.y) ?: return PosOutOfBoundsIllegalMove(positionXY, player ?: getCurrentPlayer())
        }
        return makeMoveUnsafe(position, player, externalFinishReason)
    }

    /**
     * Valid real positions starts from `1`, but not from `0`.
     * `0` is reserved for the initial position (cross, empty or other).
     */
    fun makeMove(x: Int, y: Int, player: Player? = null, externalFinishReason: ExternalFinishReason? = null): MoveResult {
        val position = getPositionIfWithinBounds(x, y)
        return if (position != null) {
            makeMoveUnsafe(position, player, externalFinishReason)
        } else {
            PosOutOfBoundsIllegalMove(PositionXY(x, y), player ?: getCurrentPlayer())
        }
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
            require(unmakeMove() is LegalMove)
        }
    }

    fun unmakeMove(): MoveResult {
        if (currentMoveNumber == 0) return NoLegalMoves

        val moveResult = legalMoves.removeLast()

        for (base in moveResult.bases.reversed()) {
            val basePlayer = base.player

            base.rollbackPositions.iterateWithIndex { index, rollbackPosition ->
                val rollbackDotState = base.rollbackDotStates.get(index)
                rollbackPosition.setState(rollbackDotState)
                if (base.isReal) {
                    updateScoreAndHashForTerritory(rollbackPosition, rollbackDotState, basePlayer, rollback = true)
                }
            }
        }

        if (moveResult.emptyBaseInvalidatePositions.size > 0) {
            val emptyTerritoryState = DotState.createEmptyTerritory(moveResult.player.opposite())
            moveResult.emptyBaseInvalidatePositions.iterate { position ->
                position.setState(emptyTerritoryState)
            }
        }

        if (moveResult.position != Position.GAME_OVER) {
            moveResult.position.setState(moveResult.previousState)
            updatePositionHash(moveResult.position, moveResult.player)
            if (rules.suicideAllowed) {
                numberOfLegalMovesIfSuicideAllowed++
            }
        }

        return moveResult
    }

    fun getPositionIfValid(x: Int, y: Int, player: Player?): Position? {
        if (gameResult != null) return null
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
             * TODO: currently empty territory isn't supported only for [BaseMode.OnlyOpponentDots]
             * If they are implemented for this mode, the base mode check can be removed
             */
            return position
        }

        // Otherwise we have to check the validity by emulating move placing and rollback afterward
        return makeMoveUnsafe(position, player).let { moveResult ->
            if (moveResult is LegalMove) {
                require(unmakeMove() is LegalMove)
                position
            } else {
                null
            }
        }
    }

    fun makeMoveUnsafe(position: Position, player: Player? = null, externalFinishReason: ExternalFinishReason? = null): MoveResult {
        val currentPlayer = player ?: getCurrentPlayer()

        if (isGameOver()) return GameIsAlreadyOverIllegalMove(position, currentPlayer)

        if (position == Position.GAME_OVER) {
            require(externalFinishReason != null)
            return finishGame(externalFinishReason, currentPlayer)
        }

        val originalState: DotState = position.getState()
        if (originalState.getActivePlayer() != Player.None) return PosIsOccupiedIllegalMove(position, currentPlayer)

        val resultBases: List<Base>
        val emptyBaseInvalidatePositions: PositionsList

        if (rules.suicideAllowed) {
            numberOfLegalMovesIfSuicideAllowed--
        }

        position.setState(DotState.createPlaced(currentPlayer))
        val hashValue = ZobristHash.getPositionsValue(position, currentPlayer)
        updatePositionHash(hashValue)

        val bases = tryCapture(position, currentPlayer, emptyBaseCapturing = false)

        // Handle possible suicidal moves.
        if (bases.isEmpty()) {
            resultBases = if (rules.baseMode != BaseMode.OnlyOpponentDots) {
                val oppositePlayer = currentPlayer.opposite()
                if (originalState.getEmptyTerritoryPlayer() == oppositePlayer) {
                    if (rules.suicideAllowed) {
                        // Check capturing by the opposite player
                        val oppositeBase =
                            captureWhenEmptyTerritoryBecomesRealBase(position, oppositePlayer)
                        listOf(oppositeBase)
                    } else {
                        position.setState(originalState)
                        updatePositionHash(hashValue)
                        if (rules.suicideAllowed) {
                            numberOfLegalMovesIfSuicideAllowed++
                        }
                        return SuicidalIllegalMove(position, currentPlayer)
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
                        updatePositionHash(hashValue)
                        if (rules.suicideAllowed) {
                            numberOfLegalMovesIfSuicideAllowed++
                        }
                        return SuicidalIllegalMove(position, currentPlayer)
                    } else {
                        base?.let { listOf(it) } ?: bases
                    }
                } ?: bases
            }
            emptyBaseInvalidatePositions = PositionsList.EMPTY // Not used in `AllOpponentDots` mode
        } else {
            resultBases = bases
            emptyBaseInvalidatePositions = if (rules.baseMode != BaseMode.OnlyOpponentDots &&
                originalState.isWithinEmptyTerritory(currentPlayer.opposite())
            ) {
                // Invalidate empty territory of the opposite player in case of capturing that is more prioritized
                // It makes the positions legal for further game.
                invalidateEmptyTerritory(position)
                closureOrInvalidatePositionsBuffer.copy()
            } else {
                PositionsList.EMPTY
            }
        }

        val result = if (rules.suicideAllowed && numberOfLegalMovesIfSuicideAllowed == 0) {
            calculateGameResult(
                EndGameKind.NoLegalMoves,
                currentPlayer,
                position,
                originalState,
                emptyBaseInvalidatePositions,
                resultBases
            )
        } else {
            LegalMove(
                position,
                currentPlayer,
                originalState,
                emptyBaseInvalidatePositions,
                resultBases,
            )
        }
        legalMoves.add(result)
        return result
    }

    private fun finishGame(externalFinishReason: ExternalFinishReason, player: Player?): MoveResult {
        val currentPlayer = player ?: getCurrentPlayer()

        if (gameResult != null) return GameIsAlreadyOverIllegalMove(Position.GAME_OVER, currentPlayer)

        val gameResult = when (externalFinishReason) {
            ExternalFinishReason.Grounding -> {
                require(rules.baseMode != BaseMode.OnlyOpponentDots) {
                    "${BaseMode.OnlyOpponentDots::class.simpleName} is not yet supported (it requires handling of immortal groups that have two or more eyes)"
                }
                require(!rules.captureByBorder) {
                    "${rules.captureByBorder::class.simpleName} is not yet supported"
                }

                val (bases, emptyBaseInvalidatePositions) = ground(currentPlayer)

                calculateGameResult(
                    EndGameKind.Grounding,
                    currentPlayer,
                    Position.GAME_OVER,
                    Position.GAME_OVER.getState(),
                    emptyBaseInvalidatePositions,
                    bases
                )
            }
            ExternalFinishReason.Draw -> GameResult.Draw(endGameKind = null, currentPlayer)
            ExternalFinishReason.Resign -> ResignWin(currentPlayer.opposite())
            ExternalFinishReason.Time -> TimeWin(currentPlayer.opposite())
            ExternalFinishReason.Interrupt -> GameResult.InterruptWin(currentPlayer.opposite())
            ExternalFinishReason.Unknown -> UnknownWin(currentPlayer.opposite())
        }

        legalMoves.add(gameResult)

        return gameResult
    }

    private fun calculateGameResult(
        endGameKind: EndGameKind,
        player: Player,
        position: Position,
        originalState: DotState,
        emptyBaseInvalidatePositions: PositionsList,
        resultBases: List<Base>
    ): GameResult {
        val scoreForFirstPlayer = getScoreDiff(Player.First)
        return if (scoreForFirstPlayer == 0.0) {
            GameResult.Draw(endGameKind, player, position,originalState, emptyBaseInvalidatePositions, resultBases)
        } else {
            val winner: Player
            val score: Double
            if (scoreForFirstPlayer > 0) {
                winner = Player.First
                score = scoreForFirstPlayer
            } else {
                winner = Player.Second
                score = -scoreForFirstPlayer
            }
            GameResult.ScoreWin(score, endGameKind, winner, player, position, originalState, emptyBaseInvalidatePositions, resultBases)
        }
    }

    private data class GroundInfo(val bases: List<Base>, val emptyBaseInvalidatePositions: PositionsList)

    private fun ground(player: Player): GroundInfo {
        val processedPositions = PositionsList(size, realWidth)
        val oppositePlayer = player.opposite()
        val emptyBaseInvalidatePositions = PositionsList(size, realWidth)

        val bases = mutableListOf<Base>()

        for (move in legalMoves) {
            val position = move.position

            if (position.getState().let { !it.isVisited() && it.isActive(player) }) {
                val grounded = getTerritoryPositions(player, position, grounding = true)

                if (!grounded) {
                    territoryPositionsBuffer.iterate { position ->
                        position.forEachAdjacent(realWidth) {
                            if (it.getState().isWithinEmptyTerritory(player)) {
                                invalidateEmptyTerritory(position)
                                emptyBaseInvalidatePositions.addAll(closureOrInvalidatePositionsBuffer)
                                false
                            } else {
                                true
                            }
                        }
                    }
                    bases.add(calculateBase(closurePositions = PositionsList.EMPTY, oppositePlayer))
                }

                territoryPositionsBuffer.iterate {
                    it.setVisited()
                    processedPositions.add(it)
                }
            }
        }

        processedPositions.clearVisited()

        return GroundInfo(bases, emptyBaseInvalidatePositions)
    }

    private fun captureWhenEmptyTerritoryBecomesRealBase(initialPosition: Position, oppositePlayer: Player): Base {
        var position = initialPosition

        // Searching for an opponent dot that makes a closure that contains the `initialPosition`.
        // The closure always exists, otherwise there is an error in previous calculations.
        while (position.value > 0) {
            position = position.xm1y()

            // Try to peek an active opposite player dot
            if (!position.getState().getActivePlayer().checkActiveAndWall(oppositePlayer)) continue

            val oppositePlayerBased = tryCapture(position, oppositePlayer, emptyBaseCapturing = true)
            // The found base always should be real and include the `initialPosition`
            return oppositePlayerBased.firstOrNull { it.isReal } ?: continue
        }

        error("Enemy's empty territory should be enclosed by an outer closure at $initialPosition")
    }

    private fun tryCapture(position: Position, player: Player, emptyBaseCapturing: Boolean): List<Base> {
        return if (rules.baseMode != BaseMode.OnlyOpponentDots) {
            getUnconnectedPositions(position, player)

            // Optimization: in a regular case it should be at least 2 connection dots, otherwise there is no surrounding.
            // However, in the case of empty territory, the connection might be singular since the base is already built.
            val minNumberOfConnections = if (emptyBaseCapturing) 1 else 2
            if (startPositionsBuffer.size < minNumberOfConnections) return emptyList()

            val closuresData = closuresList.apply {
                clear()
                startPositionsBuffer.iterate { unconnectedPosition ->
                    // Optimization: it doesn't make sense to check the latest unconnected dot
                    // when all previous connections form minimal bases
                    // because the latest always forms a base with maximal square that should be dropped
                    if (!rules.captureByBorder && isNotEmpty() && size == startPositionsBuffer.size - 1) {
                        return@iterate
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
                startPositionsBuffer.iterate { oppositeAdjacentPosition ->
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
        startPositionsBuffer.clear()

        val xm1y = position.xm1y()
        val xym1 = position.xym1(realWidth)
        val xp1y = position.xp1y()
        val xyp1 = position.xyp1(realWidth)
        val xm1Player = xm1y.getState().getActivePlayer()
        val xym1Player = xym1.getState().getActivePlayer()
        val xp1yPlayer = xp1y.getState().getActivePlayer()
        val xyp1Player = xyp1.getState().getActivePlayer()

        checkAndAdd(xp1yPlayer, player, position.xp1yp1(realWidth), xyp1)
        checkAndAdd(xyp1Player, player,position.xm1yp1(realWidth), xm1y)
        checkAndAdd(xm1Player, player,position.xm1ym1(realWidth), xym1)
        checkAndAdd(xym1Player, player,position.xp1ym1(realWidth), xp1y)
    }

    private fun checkAndAdd(checkPlayer: Player, currentPlayer: Player, addPosition1: Position, addPosition2: Position) {
        if (!checkPlayer.checkActiveAndWall(currentPlayer)) {
            if (addPosition1.getState().getActivePlayer().checkActiveAndWall(currentPlayer)) {
                startPositionsBuffer.add(addPosition1)
            } else if (addPosition2.getState().getActivePlayer().checkActiveAndWall(currentPlayer)) {
                startPositionsBuffer.add(addPosition2)
            }
        }
    }

    private fun getOppositeAdjacentPositions(position: Position, player: Player) {
        startPositionsBuffer.clear()
        val oppositePlayer = player.opposite()
        position.forEachAdjacent(realWidth) {
            if (it.getState().isActive(oppositePlayer)) {
                startPositionsBuffer.add(it)
            }
            true
        }
    }

    private fun buildBase(player: Player, closurePositions: PositionsList): Base {
        closurePositions.iterate { it.setVisited() }
        val territoryFirstPosition = getNextPosition(closurePositions.get(1),closurePositions.get(0), realWidth)
        val isGrounded = getTerritoryPositions(player, territoryFirstPosition, grounding = false)
        if (!rules.captureByBorder) {
            require(!isGrounded)
        }
        closurePositions.clearVisited()
        return calculateBase(closurePositions, player)
    }

    private data class ClosureData(
        val square: Int,
        val closure: PositionsList,
        val containsBorder: Boolean,
    )

    private fun tryGetCounterCounterClockwiseClosure(
        initialPosition: Position,
        startPosition: Position,
        player: Player,
    ): ClosureData? {
        closureOrInvalidatePositionsBuffer.clear()
        closureOrInvalidatePositionsBuffer.add(initialPosition)
        initialPosition.setVisited()
        closureOrInvalidatePositionsBuffer.add(startPosition)
        startPosition.setVisited()
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

                    if (state.isVisited()) {
                        // Remove trailing dots
                        var currentPosition = it
                        do {
                            val lastPosition = closureOrInvalidatePositionsBuffer.pop()
                            square += currentPosition.getSquare(lastPosition, realWidth)
                            currentPosition = lastPosition
                            lastPosition.clearVisited()
                        } while (lastPosition != it)
                    }

                    closureOrInvalidatePositionsBuffer.add(it)
                    it.setVisited()
                    nextPosition = currentPosition
                    currentPosition = it
                    return@clockwiseBigJumpWalk false
                }
                return@clockwiseBigJumpWalk true
            }
            if (clockwiseWalkCompleted) {
                square = 0
                break
            }
        } while (true)

        closureOrInvalidatePositionsBuffer.clearVisited()

        return if (square > 0) ClosureData(square, closureOrInvalidatePositionsBuffer.copy(), containsBorder) else null
    }

    private fun getTerritoryPositions(player: Player, firstPosition: Position, grounding: Boolean): Boolean {
        walkStackPositionsBuffer.clear()
        territoryPositionsBuffer.clear()

        var grounded = false

        fun checkAndAdd(position: Position) {
            val state = position.getState()
            if (!state.isVisited()) {
                val activePlayer = state.getActivePlayer();

                if (activePlayer == Player.WallOrBoth) {
                    require(grounding)
                    grounded = true
                } else {
                    val isPositionLegal = if (grounding) {
                        activePlayer == player
                    } else {
                        activePlayer != player || !state.isTerritory()
                    }

                    if (isPositionLegal) {
                        territoryPositionsBuffer.add(position)
                        position.setVisited()

                        walkStackPositionsBuffer.add(position)
                    }
                }
            }
        }

        checkAndAdd(firstPosition)

        while (walkStackPositionsBuffer.size > 0) {
            walkStackPositionsBuffer.pop().forEachAdjacent(realWidth) {
                checkAndAdd(it)
                true
            }
        }

        territoryPositionsBuffer.clearVisited()

        return grounded
    }

    private fun tryGetBaseForAllOpponentDotsMode(
        territoryFirstPosition: Position,
        player: Player,
        capturingByOppositePlayer: Boolean,
    ): BaseWithRollbackInfo? {
        require(rules.baseMode == BaseMode.OnlyOpponentDots)

        val oppositePlayer = player.opposite()

        if (territoryFirstPosition.getState().isActive(player)) {
            return null // Ignore already processed bases
        }

        walkStackPositionsBuffer.clear()
        territoryPositionsBuffer.clear()
        closureOrInvalidatePositionsBuffer.clear()

        fun Position.checkAndAdd(): Boolean {
            val state = getState()
            val activePlayer = state.getActivePlayer()

            if (activePlayer == Player.WallOrBoth) {
                return if (!rules.captureByBorder) {
                    false
                } else {
                    closureOrInvalidatePositionsBuffer.add(this)
                    true
                }
            } else if (activePlayer == Player.None) {
                return false // early return if encounter an empty position
            }

            if (state.isVisited()) return true

            if (state.isPlaced(player)) {
                if (!state.isActive(oppositePlayer)) {
                    closureOrInvalidatePositionsBuffer.add(this)
                    return true
                }
            }

            setVisited()
            territoryPositionsBuffer.add(this)
            walkStackPositionsBuffer.add(this)

            return true
        }

        if (!territoryFirstPosition.checkAndAdd()) {
            territoryPositionsBuffer.clearVisited()
            return null
        }

        while (walkStackPositionsBuffer.size > 0) {
            if (!walkStackPositionsBuffer.pop().forEachAdjacent(realWidth) {
                it.checkAndAdd()
            }) {
                territoryPositionsBuffer.clearVisited()
                return null
            }
        }

        territoryPositionsBuffer.clearVisited()

        val base: Base?
        val suicidalMove: Boolean

        if (capturingByOppositePlayer && !rules.suicideAllowed) {
            base = null
            suicidalMove = true
        } else {
            base = createBaseAndUpdateStates(
                player,
                closureOrInvalidatePositionsBuffer.copy(),
                isReal = true,
            )
            suicidalMove = false
        }

        return BaseWithRollbackInfo(base, suicidalMove)
    }

    private data class BaseWithRollbackInfo(val base: Base?, val suicidalMove: Boolean)

    private fun calculateBase(closurePositions: PositionsList, player: Player): Base {
        val isReal = when (rules.baseMode) {
            BaseMode.AtLeastOneOpponentDot -> {
                var atLeastOneCapturedDot = false
                val oppositePlayer = player.opposite()
                territoryPositionsBuffer.iterate { territoryPosition ->
                    if (territoryPosition.getState().isPlaced(oppositePlayer)) {
                        // The `getTerritoryPositions` never returns positions inside already owned territory,
                        // so there is no need to check for the territory flag.
                        atLeastOneCapturedDot = true
                        return@iterate
                    }
                }
                atLeastOneCapturedDot
            }
            BaseMode.AnySurrounding -> true
            BaseMode.OnlyOpponentDots -> error("The mode ${BaseMode.OnlyOpponentDots.name} is handled by ${::tryGetBaseForAllOpponentDotsMode.name}")
        }

        return createBaseAndUpdateStates(player, closurePositions, isReal)
    }

    private fun createBaseAndUpdateStates(
        basePlayer: Player,
        closurePositions: PositionsList,
        isReal: Boolean,
    ): Base {
        val rollbackPositions = PositionsList(territoryPositionsBuffer.size, realWidth)
        val rollbackDotStates = DotStatesList(territoryPositionsBuffer.size)
        val playerEmptyTerritory = DotState.createEmptyTerritory(basePlayer)

        territoryPositionsBuffer.iterate { territoryPosition ->
            val territoryDotState = territoryPosition.getState()

            val territoryActivePlayer = territoryDotState.getActivePlayer()
            // Don't change the state and its zobrist hash if the position is in active state
            // because the positions inside the base are filled with the current player dots
            if (territoryActivePlayer != basePlayer) {
                val newState = if (isReal) {
                    updateScoreAndHashForTerritory(territoryPosition, territoryDotState, basePlayer, rollback = false)
                    territoryDotState.setTerritoryAndActivePlayer(basePlayer)
                } else {
                    playerEmptyTerritory
                }

                rollbackPositions.add(territoryPosition)
                rollbackDotStates.add(territoryDotState)
                territoryPosition.setState(newState)
            }
        }

        return Base(
            basePlayer,
            closurePositions,
            rollbackPositions,
            rollbackDotStates,
            isReal,
        )
    }

    /**
     * Invalidates states of an empty base that becomes broken.
     */
    private fun invalidateEmptyTerritory(position: Position) {
        walkStackPositionsBuffer.clear()
        walkStackPositionsBuffer.add(position)
        closureOrInvalidatePositionsBuffer.clear()

        while (walkStackPositionsBuffer.size > 0) {
            walkStackPositionsBuffer.pop().forEachAdjacent(realWidth) {
                val state = it.getState()
                if (state.getEmptyTerritoryPlayer() == Player.None) return@forEachAdjacent true
                if (state.isVisited()) return@forEachAdjacent true

                closureOrInvalidatePositionsBuffer.add(it)
                it.setState(DotState.Empty)
                it.setVisited()

                walkStackPositionsBuffer.add(it)
                true
            }
        }

        closureOrInvalidatePositionsBuffer.clearVisited()
    }

    fun Position.getState(): DotState {
        return DotState(dots[value.toInt()])
    }

    private fun Position.setVisited() {
        setState(getState().setVisited())
    }

    private fun PositionsList.clearVisited() {
        iterate { it.clearVisited() }
    }

    private fun Position.clearVisited() {
        setState(getState().clearVisited())
    }

    private fun Position.setState(state: DotState) {
        dots[value.toInt()] = state.value
    }

    private fun Player.checkActiveAndWall(player: Player): Boolean {
        return this == player || captureByBorder && this == Player.WallOrBoth
    }

    private fun updateScoreAndHashForTerritory(position: Position, state: DotState, basePlayer: Player, rollback: Boolean) {
        val currentPlayer = state.getActivePlayer()
        val baseOppositePlayer = basePlayer.opposite()

        if (state.isPlaced(baseOppositePlayer)) {
            // The `getTerritoryPositions` never returns positions inside already owned territory,
            // so there is no need to check for the territory flag.
            if (basePlayer == Player.First) {
                if (!rollback) {
                    player1Score++
                } else {
                    player1Score--
                }
            } else {
                if (!rollback) {
                    player2Score++
                } else {
                    player2Score--
                }
            }
        } else if (state.isPlaced(basePlayer) && state.isActive(baseOppositePlayer)) {
            // No diff for the territory of the current player
            if (basePlayer == Player.First) {
                if (!rollback) {
                    player2Score--
                } else {
                    player2Score++
                }
            } else {
                if (!rollback) {
                    player1Score--
                } else {
                    player1Score++
                }
            }
        }

        if (currentPlayer == Player.None) {
            if (rules.suicideAllowed) {
                if (!rollback) {
                    numberOfLegalMovesIfSuicideAllowed--
                } else {
                    numberOfLegalMovesIfSuicideAllowed++
                }
            }
            updatePositionHash(position, basePlayer)
        } else {
            if (currentPlayer == baseOppositePlayer) {
                val positionsHash = ZobristHash.positionsHash[position.value.toInt()]
                // Simulate unmaking the opponent move and making the player's move
                updatePositionHash(positionsHash[baseOppositePlayer.value.toInt()])
                updatePositionHash(positionsHash[basePlayer.value.toInt()])
            }
        }
    }

    private fun updatePositionHash(position: Position, player: Player) {
        updatePositionHash(ZobristHash.getPositionsValue(position, player))
    }

    private fun updatePositionHash(value: Hash) {
        positionHash = positionHash xor value
    }

    override fun toString(): String = render(DumpParameters.PLAIN_FIELD)
}

sealed class MoveResult {
    val mark: String
        get() {
            return when (this) {
                is GameResult.Draw,
                is GameResult.ScoreWin -> {
                    if (endGameKind == EndGameKind.Grounding) "⏚" else  "🏁"
                }
                is ResignWin -> "🏳️"
                is TimeWin -> "⏲️"
                is UnknownWin -> "❔"
                is GameResult.InterruptWin -> "❕"
                is LegalMove -> {
                    if (player == Player.First) "1" else "2" // Or use "🔵", "🔴" or "1️⃣", "2️⃣" instead
                }
                is IllegalMove -> "🚫"
            }
        }

    override fun toString(): String = mark
}

sealed class IllegalMove(val position: Position?, val player: Player) : MoveResult()

class GameIsAlreadyOverIllegalMove(position: Position, player: Player) : IllegalMove(position, player)

class PosIsOccupiedIllegalMove(position: Position, player: Player) : IllegalMove(position, player)

class SuicidalIllegalMove(position: Position, player: Player) : IllegalMove(position, player)

class PosOutOfBoundsIllegalMove(val positionXY: PositionXY, player: Player) : IllegalMove(null, player)

object NoLegalMoves : IllegalMove(position = null, Player.None)

open class LegalMove(
    val position: Position,
    val player: Player,
    val previousState: DotState,
    val emptyBaseInvalidatePositions: PositionsList,
    val bases: List<Base>,
) : MoveResult()

class Base(
    val player: Player,
    val closurePositions: PositionsList,
    val rollbackPositions: PositionsList,
    val rollbackDotStates: DotStatesList,
    val isReal: Boolean,
)

enum class ExternalFinishReason {
    Grounding,
    Draw,
    Resign,
    Time,
    Interrupt,
    Unknown;

    companion object {
        val textToValue: Map<String, ExternalFinishReason> = entries.associateBy { it.name.lowercase() }
    }
}

enum class EndGameKind {
    Grounding,
    NoLegalMoves,
}

interface EndGameResult {
    val endGameKind: EndGameKind?
}

sealed class GameResult(
    player: Player?,
    position: Position,
    previousState: DotState,
    emptyBaseInvalidatePositions: PositionsList,
    bases: List<Base> = emptyList(),
) : LegalMove(position, player ?: Player.None, previousState, emptyBaseInvalidatePositions, bases) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other != null && this::class == other::class && player == (other as GameResult).player
    }

    override fun hashCode(): Int {
        return 31 * this::class.hashCode() + player.hashCode()
    }

    class Draw(
        override val endGameKind: EndGameKind?,
        player: Player?,
        position: Position = Position.GAME_OVER,
        previousState: DotState = DotState.Empty,
        emptyBaseInvalidatePositions: PositionsList = PositionsList.EMPTY,
        bases: List<Base> = emptyList(),
    ) : GameResult(player, position, previousState, emptyBaseInvalidatePositions, bases), EndGameResult {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            return endGameKind == (other as Draw).endGameKind
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + endGameKind.hashCode()
        }

        override fun toString(): String {
            return this::class.simpleName + (endGameKind?.let { " ($it)" } ?: "")
        }
    }

    sealed class WinGameResult(
        val winner: Player,
        player: Player?,
        position: Position = Position.GAME_OVER,
        previousState: DotState = DotState.Empty,
        emptyBaseInvalidatePositions: PositionsList = PositionsList.EMPTY,
        bases: List<Base> = emptyList(),
    ) : GameResult(player, position, previousState, emptyBaseInvalidatePositions, bases) {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            return winner == (other as WinGameResult).winner
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + winner.hashCode()
        }

        override fun toString(): String {
            return this::class.simpleName + "(${::winner.name} : $winner" +
                    player.takeIf { it != Player.None }?.let { ", ${::player.name}: $player" } +
                    ")"
        }
    }

    class ScoreWin(
        val score: Double,
        override val endGameKind: EndGameKind?,
        winner: Player,
        player: Player?,
        position: Position = Position.GAME_OVER,
        previousState: DotState = DotState.Empty,
        emptyBaseInvalidatePositions: PositionsList = PositionsList.EMPTY,
        bases: List<Base> = emptyList()
    ) : WinGameResult(winner, player, position, previousState, emptyBaseInvalidatePositions, bases), EndGameResult {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as ScoreWin
            return score == other.score && endGameKind == other.endGameKind
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + score.hashCode()
            result = 31 * result + endGameKind.hashCode()
            return result
        }

        override fun toString(): String {
            return this::class.simpleName + "(${::winner.name}: $winner, $score" +
                    endGameKind?.let { ", $endGameKind" } +
                    (player.takeIf { it != winner }?.let { ", ${::player.name}: $player" } ?: "") +
                    ")"
        }
    }

    class ResignWin(winner: Player) : WinGameResult(winner, winner.opposite())

    class TimeWin(winner: Player) : WinGameResult(winner, winner.opposite())

    class InterruptWin(winner: Player) : WinGameResult(winner, winner.opposite())

    class UnknownWin(winner: Player) : WinGameResult(winner, winner.opposite())

    fun toExternalFinishReason(): ExternalFinishReason? {
        return when (this) {
            is Draw -> {
                when (endGameKind) {
                    EndGameKind.Grounding -> ExternalFinishReason.Grounding
                    EndGameKind.NoLegalMoves -> null
                    null -> ExternalFinishReason.Unknown
                }
            }
            is ResignWin -> ExternalFinishReason.Resign
            is TimeWin -> ExternalFinishReason.Time
            is InterruptWin -> ExternalFinishReason.Interrupt
            is UnknownWin -> ExternalFinishReason.Unknown
            is ScoreWin -> {
                when (endGameKind) {
                    EndGameKind.Grounding -> ExternalFinishReason.Grounding
                    EndGameKind.NoLegalMoves -> null
                    null -> ExternalFinishReason.Unknown
                }
            }
        }
    }
}
