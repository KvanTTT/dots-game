package org.dots.game.sgf

import org.dots.game.core.*

/**
 * Refine SGF games to make them consumable by this app and KataGoDots.
 *
 * - Filter outs empty and invalid games
 * - Performs recognition of start pos ([InitPosType.Cross], [InitPosType.DoubleCross], [InitPosType.QuadrupleCross]) and transform it to `AB`, `AW` properties
 * - Replaces manually made consecutive grounding moves with `B[]` or `B[resign]` and refines game result (`[W+93]` -> `[W+R]`)
 */
object SgfRefiner {
    fun refine(games: Games): Games? {
        val refinedGames = buildList {
            for (game in games) {
                val refinedGame = recognizeStartPosIfNeeded(game)

                if (!refineGrounding(refinedGame)) continue

                add(refinedGame)
            }
        }
        return if (refinedGames.isNotEmpty()) Games(refinedGames, games.parsedNode) else null
    }

    private fun recognizeStartPosIfNeeded(game: Game): Game {
        val gameTree = game.gameTree
        val field = gameTree.field

        gameTree.rewindToBegin()
        if (game.player1AddDots == null && game.player2AddDots == null) {
            val initialMovesInfo = buildList {
                repeat(16) { // Max number of moves of initial start pos (quadruple cross)
                    if (!gameTree.next()) {
                        return@repeat
                    }

                    val legalMove = gameTree.currentNode.moveResults.firstOrNull() as? LegalMove ?: return@repeat

                    add(MoveInfo.fromLegalMove(legalMove, field, gameTree.currentNode.parsedNode))
                }
            }

            val recognitionInfo = recognizeInitPosType(initialMovesInfo, field.width, field.height)
            when (recognitionInfo.initPosType) {
                Empty, Single, Custom -> {} // Ignore because such positions can't be recognized reliably and they are rare
                Cross, DoubleCross, QuadrupleCross -> {
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

                    return newGame
                }
            }
        }

        return game
    }

    private fun refineGrounding(game: Game): Boolean {
        val gameTree = game.gameTree
        val field = gameTree.field
        gameTree.rewindToEnd()

        val currentNode = gameTree.currentNode

        // Detect last move player and drop empty games
        val lastMovePlayer = (currentNode.moveResults.firstOrNull() as? LegalMove)?.player ?: return false

        // Make sure that all last moves have a single player (otherwise it's not a valid game)
        if (currentNode.moveResults.any { it !is LegalMove || it.player != lastMovePlayer }) return false

        val previousNode = currentNode.previousNode
        if (previousNode != null) {
            val previousMoveResult = previousNode.moveResults.singleOrNull() as? LegalMove ?: return false

            // Detect a grounding move(s) and remove it because neither this app nor KataGoDots support grounding by multiple moves.
            // Also, refine output by using resignation instead of grounding.

            val gameResult = game.result
            val manualGrounding = currentNode.moveResults.size > 1 || previousMoveResult.player == lastMovePlayer
            val notagoGrounding = game.appInfo?.appType == Notago && (gameResult as? EndGameResult)?.endGameKind == Grounding
            val winGameResult = gameResult as? GameResult.WinGameResult

            if ((manualGrounding || notagoGrounding) && winGameResult != null) {
                val currentPlayer = if (manualGrounding) {
                    if (previousMoveResult.player == lastMovePlayer) {
                        // Drop multiple manually made grounding dots
                        while (gameTree.currentNode.moveResults.all { it is LegalMove && it.player == lastMovePlayer }) {
                            gameTree.back()
                        }
                        // Remove all consecutive moves that start manual grounding
                        require(gameTree.next(2))
                    } else {
                        // Drop last grounding move and a previous move before grounding to preserve alternation
                        if (winGameResult.winner == lastMovePlayer) {
                            require(gameTree.back(1))
                        }
                    }
                    require(gameTree.removeCurrentBranch())
                    field.getCurrentPlayer()
                } else {
                    lastMovePlayer.opposite()
                }

                val externalFinishReason = if (gameResult.winner != currentPlayer) {
                    ExternalFinishReason.Resign
                } else {
                    ExternalFinishReason.Grounding
                }

                // Append the final normalized move
                gameTree.addChild(MoveInfo.createFinishingMove(currentPlayer, externalFinishReason))

                // Make sure the performed transformation didn't change the winner.
                require(gameResult.winner == (field.gameResult as GameResult.WinGameResult).winner)

                game.result = field.gameResult
            }
        }

        return true
    }
}