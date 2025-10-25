package org.dots.game.sgf

import org.dots.game.core.AppInfo
import org.dots.game.core.AppType
import org.dots.game.core.EndGameKind
import org.dots.game.core.EndGameResult
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Game
import org.dots.game.core.GameProperty
import org.dots.game.core.GameResult
import org.dots.game.core.GameTreeNode
import org.dots.game.core.Games
import org.dots.game.core.Label
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.sgf.SgfConverter.Companion.LOWER_CHAR_OFFSET
import org.dots.game.sgf.SgfConverter.Companion.UPPER_CHAR_OFFSET
import org.dots.game.sgf.SgfGameMode.Companion.SUPPORTED_GAME_MODE_NAME
import org.dots.game.toNeatNumber
import kotlin.reflect.KProperty

class SgfWriter(val oldSgfRoot: SgfRoot?) {
    companion object {
        fun write(games: Games): String {
            val sgfWriter = SgfWriter(games.parsedNode?.let { it as SgfRoot })
            with(sgfWriter) {
                return StringBuilder().appendGames(games).toString()
            }
        }
    }

    private fun StringBuilder.appendGames(games: Games): StringBuilder {
        for (game in games) {
            appendGame(game)
        }
        oldSgfRoot?.unparsedText?.let {
            appendLeadingWhitespaces(it)
            append(oldSgfRoot.text.subSequence(it.textSpan.start, it.textSpan.end))
        }
        return this
    }

    private fun StringBuilder.appendGame(game: Game) {
        val sgfGameTree = game.parsedNode?.let { it as SgfGameTree }
        val sgfRootNode = sgfGameTree?.nodes?.firstOrNull()

        appendLeadingWhitespaces(sgfGameTree?.lParen)
        append('(')

        appendLeadingWhitespaces(sgfRootNode?.semicolon)
        append(';')

        // Append required read-only properties
        if (game.sgfGameMode == null) {
            appendProperty(
                Game::sgfGameMode,
                GameProperty(SgfGameMode.gameModeNameToKey.getValue(SUPPORTED_GAME_MODE_NAME)),
                game,
            )
        }
        if (game.sgfFileFormat == null) {
            appendProperty(Game::sgfFileFormat, GameProperty(SUPPORTED_FILE_FORMAT), game)
        }

        for ((key, gameProperty) in game.properties) {
            appendProperty(key, gameProperty, game)
        }

        appendNodes(game.gameTree.rootNode, game)

        appendLeadingWhitespaces(sgfGameTree?.rParen)
        append(')')
    }

    private fun StringBuilder.appendNodes(rootNode: GameTreeNode, game: Game) {
        // Use stack for traversing to prevent stack overflow on very long games
        val stack = mutableListOf<Any>()
        stack.add(rootNode)

        while (stack.isNotEmpty()) {
            when (val element = stack.removeLast()) {
                is GameTreeNode -> {
                    val sgfNode = element.parsedNode?.let { it as SgfNode }

                    if (!element.isRoot) {
                        appendLeadingWhitespaces(sgfNode?.semicolon)
                        append(';')
                    }

                    for ((key, property) in element.properties) {
                        appendProperty(key, property, game)
                    }

                    val appendParens = element.children.size > 1
                    for (child in element.children.reversed()) {
                        if (appendParens) {
                            stack.add(')')
                        }
                        stack.add(child)
                        if (appendParens) {
                            stack.add('(')
                        }
                    }
                }

                is Char -> {
                    append(element)
                }

                else -> {
                    error("Unhandled element $element")
                }
            }
        }
    }

    private fun StringBuilder.appendProperty(propertyKey: KProperty<*>, gameProperty: GameProperty<*>, game: Game) {
        val appType = game.appInfo?.appType
        val sgfPropertyInfo = SgfMetaInfo.gameToSgfProperty.getValue(propertyKey)

        val gamePropertyValue = gameProperty.value
        if (sgfPropertyInfo.name.let { it == SgfMetaInfo.PLAYER1_MOVE_KEY || it == SgfMetaInfo.PLAYER2_MOVE_KEY } && gamePropertyValue is GameResult) {
            if (appType != AppType.DotsGame && appType != AppType.Katago) {
                return // Other app types don't support finishing moves
            }

            if (appType == AppType.Katago && (gamePropertyValue as? EndGameResult)?.endGameKind != EndGameKind.Grounding) {
                return // Katago supports only grounding
            }
        }

        val parsedNodes = gameProperty.parsedNodes
        val sgfFirstPropertyNode = parsedNodes.firstOrNull()?.let { it as SgfPropertyNode }
        if (sgfFirstPropertyNode != null) {
            appendLeadingWhitespaces(sgfFirstPropertyNode.identifier)
        }
        append(if (sgfPropertyInfo.isKnown) SgfMetaInfo.sgfPropertyInfoToKey.getValue(sgfPropertyInfo) else sgfFirstPropertyNode!!.identifier.value)
        if (sgfFirstPropertyNode != null && sgfFirstPropertyNode.value.isNotEmpty()) {
            appendLeadingWhitespaces(sgfFirstPropertyNode.value.first().lSquareBracket)
        }
        // Try preserving original formatting
        if (gameProperty.changed && gamePropertyValue != null) {
            append('[')
            when (gamePropertyValue) {
                is Double -> {
                    append(gamePropertyValue.toNeatNumber())
                }

                is String -> {
                    append(gamePropertyValue)
                }

                is Int -> {
                    append(gamePropertyValue)
                }

                is AppInfo -> {
                    append(gamePropertyValue.name.replace(":", "\\:"))
                    if (gamePropertyValue.version != null) {
                        append(':')
                        append(gamePropertyValue.version.replace(":", "\\:"))
                    }
                }

                is GameResult -> {
                    when (gamePropertyValue) {
                        is GameResult.Draw -> {
                            append(if (appType == AppType.Notago) "Draw" else "0")
                        }
                        is GameResult.WinGameResult -> {
                            append(if (gamePropertyValue.winner == Player.First) SgfMetaInfo.PLAYER1_MARKER else SgfMetaInfo.PLAYER2_MARKER)
                            append('+')
                            append(when (gamePropertyValue) {
                                is GameResult.InterruptWin -> SgfMetaInfo.UNKNOWN_WIN_GAME_RESULT // TODO: which symbol is supposed to be used here?
                                is GameResult.ResignWin -> SgfMetaInfo.RESIGN_WIN_GAME_RESULT
                                is GameResult.ScoreWin -> {
                                    if (appType == AppType.Notago && (gamePropertyValue as? EndGameResult)?.endGameKind == EndGameKind.Grounding) {
                                        'G'
                                    } else {
                                        gamePropertyValue.score.toNeatNumber()
                                    }
                                }
                                is GameResult.TimeWin -> SgfMetaInfo.TIME_WIN_GAME_RESULT
                                is GameResult.UnknownWin -> SgfMetaInfo.UNKNOWN_WIN_GAME_RESULT
                            })
                        }
                    }
                }

                else -> {
                    fun appendPosition(positionXY: PositionXY?) {
                        if (positionXY == null) return
                        append(positionXY.x.coordinateToChar())
                        append(positionXY.y.coordinateToChar())
                    }

                    fun <T> List<T>.iterate(appended: (T) -> Unit) {
                        for ((index, element) in withIndex()) {
                            if (index > 0) {
                                append("][")
                            }
                            appended(element)
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    when (propertyKey) {
                        Game::size -> {
                            val sizeValue = gamePropertyValue as Pair<Int, Int>
                            if (sizeValue.first == sizeValue.second) {
                                append(sizeValue.first)
                            } else {
                                append(sizeValue.first)
                                append(':')
                                append(sizeValue.second)
                            }
                        }

                        Game::player1AddDots,
                        Game::player2AddDots,
                        GameTreeNode::player1Moves,
                        GameTreeNode::player2Moves -> {
                            (gamePropertyValue as List<MoveInfo>).iterate { moveInfo ->
                                if (moveInfo.positionXY != null) {
                                    appendPosition(moveInfo.positionXY)
                                } else {
                                    append(when (moveInfo.externalFinishReason) {
                                        ExternalFinishReason.Grounding -> "" // Treat grounding as pass
                                        else -> moveInfo.externalFinishReason.toString().lowercase()
                                    })
                                }
                            }
                        }

                        GameTreeNode::circles,
                        GameTreeNode::squares -> {
                            (gamePropertyValue as List<PositionXY>).iterate { appendPosition(it) }
                        }

                        GameTreeNode::labels -> {
                            (gamePropertyValue as List<Label>).iterate {
                                appendPosition(it.positionXY)
                                append(':')
                                append(it.text)
                            }
                        }

                        else -> {
                            append(gamePropertyValue.toString())
                        }
                    }
                }
            }
            append(']')

        } else if (sgfFirstPropertyNode != null) {
            // If the property is not set or not changed, preserve the previous values
            for (propertyValue in sgfFirstPropertyNode.value) {
                val textSpan = propertyValue.textSpan
                append(oldSgfRoot!!.text.subSequence(textSpan.start, textSpan.end))
            }
        }

        // Append duplicated properties if any
        var firstOtherNode = true
        for (otherNode in parsedNodes.drop(1)) {
            otherNode as SgfPropertyNode
            val textSpan = otherNode.textSpan
            if (firstOtherNode) {
                appendLeadingWhitespaces(otherNode.identifier)
            }
            append(oldSgfRoot!!.text.subSequence(textSpan.start, textSpan.end))
            firstOtherNode = false
        }
    }

    private fun Int.coordinateToChar(): Char {
        return when (this) {
            in 1..26 -> (this + LOWER_CHAR_OFFSET.code).toChar()
            in 27..52 -> (this + UPPER_CHAR_OFFSET.code).toChar()
            else -> error("Negative or too big value for coordinate: $this")
        }
    }

    private fun StringBuilder.appendLeadingWhitespaces(sgfToken: SgfToken?) {
        sgfToken?.leadingWs?.let { append(it.value) }
    }
}