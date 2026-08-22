package org.dots.game.sgf

import org.dots.game.Diagnostic
import org.dots.game.DiagnosticSeverity
import org.dots.game.core.*

/**
 * Refine SGF games to make them consumable by this app and KataGoDots.
 *
 * - Filters out invalid and empty games
 * - Filters out games with broken alternation order, with multiple-moves in a single node, with invalid moves
 * - Performs recognition of start pos ([InitPosType.Cross], [InitPosType.DoubleCross]) and transform it to `AB`, `AW` properties
 * - Replaces manually made consecutive grounding moves with `B[]` or `B[resign]` and refines game result (`[W+93]` -> `[W+R]`)
 */
object SgfRefiner {
    fun refine(games: Games, diagnostics: List<Diagnostic> = emptyList(), diagnosticsReporter: (Diagnostic) -> Unit = {}): Games? {
        val refinedGames = buildList {
            if (diagnostics.any { it.severity == DiagnosticSeverity.Error || it.severity == DiagnosticSeverity.Critical }) {
                return null
            }

            for (game in games) {
                val refinedGame = recognizeStartPosIfNeeded(game, diagnosticsReporter)

                refineEndMoves(refinedGame, diagnosticsReporter)

                // We should run consistency checks after grounding refinement
                // Because it handles (removes) multiple manually made consecutive moves that
                // are treated as invalid in this check.
                if (!checkMovesConsistencyAndRemoveIrrelevantInfo(refinedGame, diagnosticsReporter)) {
                    continue
                }

                add(refinedGame)
            }
        }
        return if (refinedGames.isNotEmpty()) Games(refinedGames, games.parsedNode) else null
    }

    fun checkMovesConsistencyAndRemoveIrrelevantInfo(game: Game, diagnosticsReporter: (Diagnostic) -> Unit): Boolean {
        val gameTree = game.gameTree
        gameTree.rewindToBegin()

        var currentNode: GameTreeNode? = gameTree.rootNode
        var expectedNextPlayer = Player.First

        while (currentNode != null) {
            if (!currentNode.isRoot) {
                // Disallow multiple/zero moves in a single node
                val moveResult = currentNode.moveResults.singleOrNull() ?: run {
                    diagnosticsReporter(
                        Diagnostic(
                            "Multiple/zero moves in a single node are treated as invalid during refinement.",
                            currentNode.parsedNode?.textSpan,
                            DiagnosticSeverity.Error,
                        )
                    )
                    return false
                }

                // Disallow illegal moves
                if (moveResult !is LegalMove) {
                    diagnosticsReporter(
                        Diagnostic(
                            "Illegal moves are treated as invalid during refinement.",
                            currentNode.parsedNode?.textSpan,
                            DiagnosticSeverity.Error,
                        )
                    )
                    return false
                }

                // Disallow non-alternating moves
                if (moveResult.player != expectedNextPlayer) {
                    diagnosticsReporter(
                        Diagnostic(
                            "Non-alternating moves are treated as invalid during refinement.",
                            currentNode.parsedNode?.textSpan,
                            DiagnosticSeverity.Error,
                        )
                    )
                    return false
                }

                // Strip capturing information (`W[kj.lj.jj.ki]` -> `W[kj]`)
                val playerMovesProperty = if (moveResult.player == Player.First) GameTreeNode::player1Moves else GameTreeNode::player2Moves
                currentNode.properties[playerMovesProperty]?.let {
                    currentNode.properties[playerMovesProperty] = it.copy(changed = true)
                }

                expectedNextPlayer = expectedNextPlayer.opposite()
            } else if (currentNode.children.isEmpty()) {
                return false // Drop empty games
            }

            // Remove secondary branches
            if (currentNode.children.size > 1) {
                var secondaryBranchesTextSpan: TextSpan? = null
                for (child in currentNode.children.drop(1)) {
                    child.parsedNode?.textSpan?.let {
                        secondaryBranchesTextSpan = it + secondaryBranchesTextSpan
                    }
                    gameTree.switch(child)
                    require(gameTree.removeCurrentBranch())
                }
                diagnosticsReporter(
                    Diagnostic(
                        "Secondary branches are removed.",
                        secondaryBranchesTextSpan,
                        DiagnosticSeverity.Info,
                    )
                )
            }

            currentNode = if (gameTree.next()) gameTree.currentNode else null
        }

        return true
    }

    private fun recognizeStartPosIfNeeded(game: Game, diagnosticsReporter: (Diagnostic) -> Unit): Game {
        val gameTree = game.gameTree
        val field = gameTree.field

        gameTree.rewindToBegin()
        if (game.player1AddDots == null && game.player2AddDots == null) {
            var canRecognize = true
            val initialMovesInfo = buildList {
                // Repeat until max number of moves of initial start pos (DoubleCross).
                // Detection of quadruple cross is less reliable, and it's rarely used.
                repeat(8) {
                    if (!gameTree.next()) {
                        return@buildList
                    }

                    val legalMove = gameTree.currentNode.moveResults.singleOrNull() as? LegalMove
                    if (legalMove == null || legalMove is GameResult) {
                        // Non-legal, multi-move, or finishing move encountered: abort recognition
                        canRecognize = false
                        return@buildList
                    }

                    add(MoveInfo.fromLegalMove(legalMove, field, gameTree.currentNode.parsedNode))
                }
            }

            if (!canRecognize) return game

            val recognitionInfo = recognizeInitPosType(initialMovesInfo, field.width, field.height)
            when (val initPosType = recognitionInfo.initPosType) {
                Empty, Single, Custom, QuadrupleCross -> {} // Ignore because such positions can't be recognized reliably and they are rare
                Cross, DoubleCross -> {
                    val rules = field.rules
                    val newField = Field.create(
                        Rules.createAndDetectInitPos(
                            rules.width,
                            rules.height,
                            rules.captureByBorder,
                            rules.baseMode,
                            rules.suicideAllowed,
                            recognitionInfo.refinedInitMoves,
                            rules.komi,
                            rules.random,
                            initPosGenType = if (recognitionInfo.isRandomized) null else InitPosGenType.Static,
                        ).rules
                    )

                    val newProperties = game.properties.toMutableMap().apply {
                        this[Game::player1AddDots] =
                            GameProperty(recognitionInfo.refinedInitMoves.filter { it.player == First })
                        this[Game::player2AddDots] =
                            GameProperty(recognitionInfo.refinedInitMoves.filter { it.player == Second })
                    }

                    val newGameTree = GameTree(newField, gameTree.parsedNode)

                    val newGame = Game(
                        gameTree = newGameTree,
                        newProperties,
                        game.parsedNode,
                    )

                    recognitionInfo.remainingInitMoves.forEach {
                        newGameTree.addChild(it)
                    }

                    while (gameTree.next()) {
                        newGameTree.addChild(gameTree.currentNode.properties, gameTree.parsedNode)
                    }

                    var initMovesTextSpan: TextSpan? = null
                    recognitionInfo.refinedInitMoves.forEach {
                        it.parsedNode?.textSpan?.let { textSpan ->
                            initMovesTextSpan = textSpan + initMovesTextSpan
                        }
                    }

                    diagnosticsReporter(Diagnostic(
                        "$initPosType is recognized.",
                        initMovesTextSpan,
                        DiagnosticSeverity.Info
                    ))

                    return newGame
                }
            }
        }

        return game
    }

    private fun refineEndMoves(game: Game, diagnosticsReporter: (Diagnostic) -> Unit) {
        val gameTree = game.gameTree
        val field = gameTree.field
        gameTree.rewindToEnd()

        val currentNode = gameTree.currentNode

        val lastMovePlayer = (currentNode.moveResults.firstOrNull() as? LegalMove)?.player ?: return

        // Make sure that all last moves have a single player (otherwise it's not a valid game)
        if (currentNode.moveResults.any { it !is LegalMove || it.player != lastMovePlayer }) return

        val previousNode = currentNode.previousNode ?: return

        val previousMoveResult = previousNode.moveResults.singleOrNull() as? LegalMove ?: return

        // Detect a grounding move(s) and remove it because neither this app nor KataGoDots support grounding by multiple moves.
        // Also, refine output by using resignation instead of grounding.

        val gameResult = game.result
        val manualGrounding = currentNode.moveResults.size > 1 || previousMoveResult.player == lastMovePlayer
        val notagoGrounding =
            game.appInfo?.appType == Notago && (gameResult as? EndGameResult)?.endGameKind == Grounding

        if ((manualGrounding || notagoGrounding) && gameResult != null) {
            val groundingMovesTextSpan: TextSpan?
            val currentPlayer = if (manualGrounding) {
                if (previousMoveResult.player == lastMovePlayer) {
                    // Drop multiple manually made grounding dots
                    while (!gameTree.currentNode.isRoot && gameTree.currentNode.moveResults.all { it is LegalMove && it.player == lastMovePlayer }) {
                        gameTree.back()
                    }
                    // Remove all consecutive moves that start manual grounding
                    require(gameTree.next(2))
                } else {
                    // Drop last grounding move and a previous move before grounding to preserve alternation
                    if ((gameResult as GameResult.WinGameResult).winner == lastMovePlayer) {
                        require(gameTree.back(1))
                    }
                }
                groundingMovesTextSpan = gameTree.currentNode.parsedNode?.textSpan?.let {
                    it + currentNode.parsedNode?.textSpan
                } ?: currentNode.parsedNode?.textSpan

                require(gameTree.removeCurrentBranch())
                field.getCurrentPlayer()
            } else {
                groundingMovesTextSpan = gameTree.currentNode.parsedNode?.textSpan?.let {
                    TextSpan(it.end, 0)
                }
                lastMovePlayer.opposite()
            }

            val externalFinishReason =
                if (gameResult is GameResult.WinGameResult && gameResult.winner != currentPlayer) {
                    ExternalFinishReason.Resign
                } else {
                    ExternalFinishReason.Grounding
                }

            // Append the final normalized move
            gameTree.addChild(MoveInfo.createFinishingMove(currentPlayer, externalFinishReason))

            // Make sure the performed transformation didn't change the winner.
            require(
                if (gameResult is GameResult.WinGameResult) {
                    gameResult.winner == (field.gameResult as GameResult.WinGameResult).winner
                } else {
                    field.gameResult is GameResult.Draw
                }
            )

            game.result = field.gameResult

            diagnosticsReporter(
                Diagnostic(
                    "Grounding is recognized and replaced with single $externalFinishReason move.",
                    groundingMovesTextSpan,
                    DiagnosticSeverity.Info
                )
            )
        }
    }
}