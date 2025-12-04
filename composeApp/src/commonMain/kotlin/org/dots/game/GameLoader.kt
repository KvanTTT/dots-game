package org.dots.game

import org.dots.game.core.EMPTY_POSITION_MARKER
import org.dots.game.core.FIRST_PLAYER_MARKER
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.core.SECOND_PLAYER_MARKER
import org.dots.game.dump.FieldParser
import org.dots.game.sgf.Sgf
import org.dots.game.sgf.SgfParser

object GameLoader {
    data class GameLoaderDiagnostic(val diagnostic: Diagnostic, val isContent: Boolean) {
        override fun toString(): String = diagnostic.toString()
    }

    /**
     * [rules] can be used when parsing raw fields that don't have extra info about rules.
     */
    suspend fun openOrLoad(
        pathOrContent: String,
        rules: Rules?,
        addFinishingMove: Boolean,
        diagnosticReporter: ((GameLoaderDiagnostic) -> Unit) = { println(it) }
    ): LoadResult {
        try {
            val inputType = getInputType(pathOrContent)
            var sgfContent: String?

            when (inputType) {
                InputType.FieldContent -> {
                    val field = FieldParser.parseAndConvert(
                        pathOrContent,
                        initializeRules = { width, height ->
                            Rules.createAndDetectInitPos(
                                width,
                                height,
                                captureByBorder = rules?.captureByBorder ?: Rules.Standard.captureByBorder,
                                baseMode = rules?.baseMode ?: Rules.Standard.baseMode,
                                suicideAllowed = rules?.suicideAllowed ?: Rules.Standard.suicideAllowed,
                                initialMoves = emptyList(),
                                komi = Rules.Standard.komi,
                            ).rules
                        }, diagnosticReporter = {
                            diagnosticReporter(GameLoaderDiagnostic(it, isContent = true))
                        }
                    )
                    return LoadResult(inputType, content = pathOrContent, Games.fromField(field))
                }

                InputType.SgfContent -> {
                    sgfContent = pathOrContent
                }

                is InputType.SgfFile -> {
                    sgfContent = if (inputType.isIncorrect) {
                        diagnosticReporter(
                            GameLoaderDiagnostic(
                                Diagnostic("Incorrect file `${inputType.name}`. The only .sgf and .sgfs files are supported", textSpan = null),
                                isContent = false
                            )
                        )
                        null
                    } else {
                        readFileText(inputType.refinedPath)
                    }
                }

                is InputType.SgfServerUrl -> {
                    sgfContent = downloadFileText(inputType.refinedPath)
                }

                is InputType.SgfClientUrl -> {
                    val gameSettings = GameSettings.parseUrlParams(inputType.name, inputType.paramsOffset) {
                        diagnosticReporter(GameLoaderDiagnostic(it, isContent = false))
                    }
                    sgfContent = gameSettings.sgf
                }

                is InputType.OtherUrl -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Incorrect url. The only `$ZAGRAM_LINK_PREFIX` and `$THIS_APP_SERVER_URL` are supported", textSpan = null), isContent = false))
                    sgfContent = null
                }

                is InputType.Empty -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Insert a path to .sgf(s) file or a link to zagram.org game", textSpan = null), isContent = false))
                    sgfContent = null
                }

                is InputType.Other -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Unrecognized input type. Insert a path to .sgf(s) file or a link to zagram.org game", textSpan = null), isContent = false))
                    sgfContent = null
                }
            }

            return LoadResult(inputType, sgfContent, sgfContent?.let {
                Sgf.parseAndConvert(it, onlySingleGameSupported = false, addFinishingMove = addFinishingMove, diagnosticReporter = { diagnostic ->
                    diagnosticReporter(GameLoaderDiagnostic(diagnostic, isContent = true))
                })
            } ?: Games())
        } catch (e: Exception) {
            diagnosticReporter(
                GameLoaderDiagnostic(Diagnostic(e.message ?: e.toString(), textSpan = null, DiagnosticSeverity.Critical), isContent = false)
            )
        }
        return LoadResult(InputType.Other, pathOrContent, Games())
    }

    private const val ZAGRAM_LINK_PREFIX = "https://zagram.org/eidokropki/"
    private const val ZAGRAM_DOWNLOAD_LINK_PREFIX = ZAGRAM_LINK_PREFIX + "backend/download.py?id="
    private const val ID_GROUP_NAME = "id"
    private const val URL_GROUP_NAME = "url"
    private const val URL_PARAMS_GROUP_NAME = "params"
    private val zagramIdRegex = Regex("""zagram\d+""")
    private val zagramDownloadLinkRegex = Regex(Regex.escape(ZAGRAM_DOWNLOAD_LINK_PREFIX) + """(?<$ID_GROUP_NAME>${zagramIdRegex.pattern})""")
    private val zagramGameViewLinkRegex = Regex(Regex.escape(ZAGRAM_LINK_PREFIX) + """index\.html#url:(?<$ID_GROUP_NAME>${zagramIdRegex.pattern})""")
    private val thisAppLinkRegex = Regex("""(?<$URL_GROUP_NAME>((${Regex.escape(THIS_APP_LOCAL_URL)}|${Regex.escape(THIS_APP_SERVER_URL)})/?))(?<$URL_PARAMS_GROUP_NAME>\?.+)?""")
    private val nameInParamsRegex = Regex("""${GameSettings::path.name}=(?<$ID_GROUP_NAME>[^&]+)""")
    private val filePathRegex = Regex(""".*[\\/](?!.*[\\/])(.*)""")
    private val httpRegex = Regex("https?://")

    internal fun getInputType(input: String): InputType {
        if (input.isBlank()) return InputType.Empty

        if (httpRegex.matchAt(input, 0) != null) {
            val zagramDownloadLinkMatch = zagramDownloadLinkRegex.matchEntire(input)
            if (zagramDownloadLinkMatch != null) {
                return InputType.SgfServerUrl(input, zagramDownloadLinkMatch.groups[ID_GROUP_NAME]!!.value)
            }

            val zagramGameViewLinkMatch = zagramGameViewLinkRegex.matchEntire(input)
            if (zagramGameViewLinkMatch != null) {
                val id = zagramGameViewLinkMatch.groups[ID_GROUP_NAME]!!.value
                return InputType.SgfServerUrl(ZAGRAM_DOWNLOAD_LINK_PREFIX + id, id)
            }

            val thisAppLinkMatch = thisAppLinkRegex.matchEntire(input)
            if (thisAppLinkMatch != null) {
                val path = thisAppLinkMatch.groups[URL_GROUP_NAME]!!.value
                val paramsGroup = thisAppLinkMatch.groups[URL_PARAMS_GROUP_NAME]
                val urlEncodedName = paramsGroup?.let {
                    nameInParamsRegex.findAll(it.value).firstOrNull()?.groups?.get(ID_GROUP_NAME)?.value
                } ?: ""
                val name = try {
                    UrlEncoderDecoder.decode(urlEncodedName)
                } catch (_: Exception) {
                    ""
                }
                return InputType.SgfClientUrl(input, name, paramsGroup?.value ?: "", path.length)
            }

            return InputType.OtherUrl(input)
        }

        if (zagramIdRegex.matchEntire(input) != null) {
            return InputType.SgfServerUrl(ZAGRAM_DOWNLOAD_LINK_PREFIX + input, input)
        }

        fun extractFileName(filePath: String): String {
            return filePath.substring(filePath.lastIndexOfAny(charArrayOf('/', '\\')) + 1)
        }

        val refinedPath = input.removeSurrounding("\"")
        val lower = refinedPath.lowercase()
        if (lower.endsWith(".sgf") || lower.endsWith(".sgfs")) {
            return InputType.SgfFile(refinedPath, extractFileName(refinedPath))
        }

        if (tryParseField(input)) return InputType.FieldContent
        if (tryParseSgf(input)) return InputType.SgfContent

        if (fileExists(refinedPath) || filePathRegex.matchEntire(refinedPath) != null) {
            return InputType.SgfFile(refinedPath, extractFileName(refinedPath), isIncorrect = true)
        }

        var notWhitespaceCharIndex = 0
        while (notWhitespaceCharIndex < input.length && input[notWhitespaceCharIndex].isWhitespace()) {
            notWhitespaceCharIndex++
        }

        if (input.elementAtOrNull(notWhitespaceCharIndex).let { it == FIRST_PLAYER_MARKER || it == SECOND_PLAYER_MARKER || it == EMPTY_POSITION_MARKER }) {
            return InputType.FieldContent
        }

        if (notWhitespaceCharIndex + 2 <= input.length && input.subSequence(notWhitespaceCharIndex, notWhitespaceCharIndex + 2) == "(;") {
            return InputType.SgfContent
        }

        return InputType.Other
    }

    private fun tryParseSgf(input: String): Boolean {
        var sgfContainsAError = false

        SgfParser.parse(input) {
            sgfContainsAError = sgfContainsAError || it.severity >= DiagnosticSeverity.Error
        }

        return !sgfContainsAError
    }

    private fun tryParseField(input: String): Boolean {
        var fieldContainsAError = false

        FieldParser.parse(input) {
            fieldContainsAError = fieldContainsAError || it.severity >= DiagnosticSeverity.Error
        }

        return !fieldContainsAError
    }
}

class LoadResult(
    val inputType: InputType,
    val content: String?,
    val games: Games,
)

sealed class InputType {
    sealed class Content : InputType()
    object SgfContent : Content()
    object FieldContent : Content()

    sealed class InputTypeWithPath(val refinedPath: String, val name: String, val isIncorrect: Boolean) : InputType() {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as InputTypeWithPath
            return isIncorrect == other.isIncorrect && refinedPath == other.refinedPath && name == other.name
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + isIncorrect.hashCode()
            result = 31 * result + refinedPath.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }
    }

    class SgfFile(refinedPath: String, name: String, isIncorrect: Boolean = false) : InputTypeWithPath(refinedPath, name, isIncorrect)

    sealed class Url(refinedPath: String, name: String, isIncorrect: Boolean = false) : InputTypeWithPath(refinedPath, name, isIncorrect)
    class SgfServerUrl(refinedPath: String, name: String, isIncorrect: Boolean = false) : Url(refinedPath, name, isIncorrect)
    class SgfClientUrl(refinedPath: String, name: String, val params: String, val paramsOffset: Int, isIncorrect: Boolean = false) : Url(refinedPath, name, isIncorrect) {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as SgfClientUrl
            return params == other.params && paramsOffset == other.paramsOffset
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + params.hashCode()
            result = 31 * result + paramsOffset.hashCode()
            return result
        }
    }
    class OtherUrl(path: String) : Url(path, "", true)

    object Empty : InputType()
    object Other : InputType()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
