package org.dots.game

import org.dots.game.core.ClassSettings
import org.dots.game.core.Games
import org.dots.game.sgf.SgfWriter
import org.dots.game.sgf.TextSpan
import kotlin.io.encoding.Base64

data class GameSettings(
    var path: String?,
    var sgf: String?,
    var game: Int?,
    var node: Int?,
) : ClassSettings<GameSettings>() {
    override val default: GameSettings
        get() = Default

    companion object {
        val Default = GameSettings(path = null, sgf = null, game = null, node = null)
        val Base64UrlParamsSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun parseUrlParams(paramsString: String, paramsOffset: Int, diagnosticReporter: (Diagnostic) -> Unit = { println(it) }): GameSettings {
            var index = 0
            if (paramsString.startsWith('?'))
                index++

            var path: String? = null
            var sgfContent: String? = null
            var currentGameNumber: Int? = null
            var currentNodeNumber: Int? = null

            while (index < paramsString.length) {
                val ampIndexOrEnd = paramsString.indexOf('&', index).let { if (it == -1) paramsString.length else it }
                val equalOrAmpIndex = paramsString.indexOf('=', index).let { if (it == -1 || it > ampIndexOrEnd) ampIndexOrEnd else it }

                val paramName = paramsString.substring(index, equalOrAmpIndex)
                if (paramName.isEmpty()) {
                    diagnosticReporter(Diagnostic("Empty parameter name", TextSpan(paramsOffset + index, 0)))
                } else if (equalOrAmpIndex == ampIndexOrEnd) {
                    diagnosticReporter(
                        Diagnostic("Parameter `$paramName` doesn't have a value",
                        TextSpan(paramsOffset + index, paramName.length),
                    ))
                } else {
                    val paramValue = paramsString.substring(equalOrAmpIndex + 1, ampIndexOrEnd)
                    try {
                        fun Int?.checkLegality() {
                            if (this == null) {
                                throw IllegalArgumentException("Invalid number format")
                            } else if (this < 0) {
                                throw IllegalArgumentException("Expected a non-negative integer")
                            }
                        }

                        when (paramName) {
                            GameSettings::path.name -> {
                                path = UrlEncoderDecoder.decode(paramValue)
                            }
                            GameSettings::sgf.name -> {
                                sgfContent = Gzip.decompress(Base64UrlParamsSafe.decode(paramValue.encodeToByteArray())).decodeToString()
                            }
                            GameSettings::game.name -> {
                                currentGameNumber = paramValue.toIntOrNull().also { it.checkLegality() }
                            }
                            GameSettings::node.name -> {
                                currentNodeNumber = paramValue.toIntOrNull().also { it.checkLegality() }
                            }
                            else -> {
                                diagnosticReporter(
                                    Diagnostic("Parameter `$paramName` is unknown",
                                    TextSpan(paramsOffset + index, paramName.length)))
                            }
                        }
                    } catch (ex: Throwable) {
                        diagnosticReporter(Diagnostic(
                            "Parameter `$paramName` has invalid value `$paramValue` (${ex.message ?: ex.toString()})",
                            TextSpan(paramsOffset + equalOrAmpIndex + 1, paramValue.length)
                        ))
                    }
                }

                index = ampIndexOrEnd + 1
            }

            return GameSettings(path, sgfContent, currentGameNumber, currentNodeNumber)
        }
    }

    fun update(games: Games?): GameSettings {
        if (games != null) {
            sgf = SgfWriter.write(games)
            val currentGame = game?.let { games.elementAtOrNull(it) }
            if (currentGame != null) {
                node = currentGame.gameTree.getCurrentNodeDepthFirstIndex()
            }
        }
        return this
    }

    fun toUrlParams(): String {
        return buildString {
            path?.let {
                append(GameSettings::path.name)
                append('=')
                append(UrlEncoderDecoder.encode(it))
            }
            sgf?.let {
                if (isNotEmpty())
                    append('&')
                append(GameSettings::sgf.name)
                append('=')
                append(Base64UrlParamsSafe.encode(Gzip.compress(it.encodeToByteArray())))
            }
            game?.let {
                if (isNotEmpty())
                    append('&')
                append(GameSettings::game.name)
                append('=')
                append(it)
            }
            node?.let {
                if (isNotEmpty())
                    append('&')
                append(GameSettings::node.name)
                append('=')
                append(it)
            }
            if (isNotEmpty())
                insert(0, '?')
        }
    }
}