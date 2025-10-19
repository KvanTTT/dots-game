package org.dots.game.sgf

import org.dots.game.core.AppInfo
import org.dots.game.core.Game
import org.dots.game.core.GameProperty
import org.dots.game.core.Games
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
                GameProperty(
                    SgfGameMode.gameModeNameToKey.getValue(SUPPORTED_GAME_MODE_NAME),
                    changed = true
                )
            )
        }
        if (game.sgfFileFormat == null) {
            appendProperty(Game::sgfFileFormat, GameProperty(SUPPORTED_FILE_FORMAT, changed = true))
        }

        for ((key, gameProperty) in game.properties) {
            appendProperty(key, gameProperty)
        }

        appendLeadingWhitespaces(sgfGameTree?.rParen)
        append(')')
    }

    private fun StringBuilder.appendProperty(propertyKey: KProperty<*>, gameProperty: GameProperty<*>) {
        val sgfPropertyInfo = SgfMetaInfo.gameToSgfProperty.getValue(propertyKey)
        val parsedNodes = gameProperty.parsedNodes
        val sgfFirstPropertyNode = parsedNodes.firstOrNull()?.let { it as SgfPropertyNode }
        if (sgfFirstPropertyNode != null) {
            appendLeadingWhitespaces(sgfFirstPropertyNode.identifier)
        }
        append(if (sgfPropertyInfo.isKnown) SgfMetaInfo.sgfPropertyInfoToKey.getValue(sgfPropertyInfo) else sgfFirstPropertyNode!!.identifier.value)
        if (sgfFirstPropertyNode != null && sgfFirstPropertyNode.value.isNotEmpty()) {
            appendLeadingWhitespaces(sgfFirstPropertyNode.value.first().lSquareBracket)
        }
        val gamePropertyValue = gameProperty.value
        // Try preserving original formatting
        if (gameProperty.changed && gamePropertyValue != null) {
            append('[')
            val appendValue: String = when (gamePropertyValue) {
                is Double -> {
                    gamePropertyValue.toNeatNumber().toString()
                }
                is String -> {
                    gamePropertyValue
                }
                is AppInfo -> {
                   gamePropertyValue.name.replace(":", "\\:") +
                           gamePropertyValue.version?.let { ":" + it.replace(":", "\\:")
                   }
                }
                is Pair<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val sizeValue = gamePropertyValue as Pair<Int, Int>
                    if (sizeValue.first == sizeValue.second) sizeValue.first.toString() else "${sizeValue.first}:${sizeValue.second}"
                }
                else -> {
                    gamePropertyValue.toString()
                }
            }
            append(appendValue)
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

    private fun StringBuilder.appendLeadingWhitespaces(sgfToken: SgfToken?) {
        sgfToken?.leadingWs?.let { append(it.value) }
    }
}