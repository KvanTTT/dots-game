package org.dots.game

import org.dots.game.core.EMPTY_POSITION_MARKER
import org.dots.game.core.FIRST_PLAYER_MARKER
import org.dots.game.core.Game
import org.dots.game.core.GameTree
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.core.SECOND_PLAYER_MARKER
import org.dots.game.dump.FieldParser
import org.dots.game.sgf.Sgf
import org.dots.game.sgf.SgfParser

class LoadResult(
    val inputType: InputType,
    val content: String?,
    val games: Games,
)

/**
 * [rules] can be used when parsing raw fields that don't have extra info about rules.
 */
suspend fun openOrLoad(
    pathOrContent: String,
    rules: Rules?,
    addFinishingMove: Boolean,
    diagnosticReporter: ((Diagnostic) -> Unit) = { println(it) }
): LoadResult {
    try {
        val inputType = getInputType(pathOrContent)
        var sgfContent: String?

        when (inputType) {
            InputType.FieldContent -> {
                val field = FieldParser.parseAndConvert(
                    pathOrContent,
                    initializeRules = { width, height ->
                        Rules(
                            width, height,
                            captureByBorder = rules?.captureByBorder ?: Rules.Standard.captureByBorder,
                            baseMode = rules?.baseMode ?: Rules.Standard.baseMode,
                            suicideAllowed = rules?.suicideAllowed ?: Rules.Standard.suicideAllowed,
                            initialMoves = emptyList(),
                        )
                    }, diagnosticReporter
                )
                val gameTree = GameTree(field).apply {
                    for (move in field.moveSequence) {
                        add(move)
                    }
                }
                return LoadResult(inputType, content = pathOrContent, Games(Game(gameTree)))
            }

            InputType.SgfContent -> {
                sgfContent = pathOrContent
            }

            is InputType.SgfFile -> {
                sgfContent = if (inputType.isIncorrect) {
                    diagnosticReporter(Diagnostic("Incorrect file `${inputType.name}`. The only .sgf and .sgfs files are supported", textSpan = null))
                    null
                } else {
                    readFileText(inputType.refinedPath)
                }
            }

            is InputType.SgfUrl -> {
                sgfContent = if (inputType.isIncorrect) {
                    diagnosticReporter(Diagnostic("Incorrect url. The only `$zagramLinkPrefix` is supported", textSpan = null))
                    null
                } else {
                    downloadFileText(inputType.refinedPath)
                }
            }

            is InputType.Other -> {
                diagnosticReporter(Diagnostic("Unrecognized input type. Insert a path to .sgf file or a link to zagram.org game", textSpan = null))
                sgfContent = null
            }
        }

        return LoadResult(inputType, sgfContent, sgfContent?.let {
            Sgf.parseAndConvert(it, onlySingleGameSupported = false, addFinishingMove = addFinishingMove, diagnosticReporter)
        } ?: Games())
    } catch (e: Exception) {
        diagnosticReporter(Diagnostic(e.message ?: e.toString(), textSpan = null, DiagnosticSeverity.Critical))
    }
    return LoadResult(InputType.Other, null, Games())
}

sealed class InputType {
    sealed class Content : InputType()
    object SgfContent : Content()
    object FieldContent : Content()

    sealed class InputTypeWithName(val refinedPath: String, val name: String, val isIncorrect: Boolean) : InputType() {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as InputTypeWithName
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

    class SgfFile(refinedPath: String, name: String, isIncorrect: Boolean = false) : InputTypeWithName(refinedPath, name, isIncorrect)
    class SgfUrl(refinedPath: String, name: String, isIncorrect: Boolean = false) : InputTypeWithName(refinedPath, name, isIncorrect)

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

private const val zagramLinkPrefix = "https://zagram.org/eidokropki/"
private const val zagramDownloadLinkPrefix = zagramLinkPrefix + "backend/download.py?id="
private const val zagramIdGroupName = "id"
private val zagramIdRegex = Regex("""zagram\d+""")
private val zagramDownloadLinkRegex = Regex(Regex.escape(zagramDownloadLinkPrefix) + """(?<$zagramIdGroupName>${zagramIdRegex.pattern})""")
private val zagramGameViewLinkRegex = Regex(Regex.escape(zagramLinkPrefix) + """index\.html#url:(?<$zagramIdGroupName>${zagramIdRegex.pattern})""")
private val filePathRegex = Regex(""".*[\\/](?!.*[\\/])(.*)""")

internal fun getInputType(input: String): InputType {
    if (input.startsWith("https://")) {
        val zagramDownloadLinkMatch = zagramDownloadLinkRegex.matchEntire(input)
        if (zagramDownloadLinkMatch != null) {
            return InputType.SgfUrl(input, zagramDownloadLinkMatch.groups[zagramIdGroupName]!!.value)
        }

        val zagramGameViewLinkMatch = zagramGameViewLinkRegex.matchEntire(input)
        if (zagramGameViewLinkMatch != null) {
            val id = zagramGameViewLinkMatch.groups[zagramIdGroupName]!!.value
            return InputType.SgfUrl(zagramDownloadLinkPrefix + id, id)
        }

        return InputType.SgfUrl(input, "", isIncorrect = true)
    }

    if (zagramIdRegex.matchEntire(input) != null) {
        return InputType.SgfUrl(zagramDownloadLinkPrefix + input, input)
    }

    fun extractFileName(filePath: String): String {
        return filePath.substring(filePath.lastIndexOfAny(charArrayOf('/', '\\')) + 1)
    }

    val refinedPath = input.removeSurrounding("\"")
    if (refinedPath.endsWith(".sgf") || refinedPath.endsWith(".sgfs")) {
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

    if (input.elementAtOrNull(notWhitespaceCharIndex).let { it == FIRST_PLAYER_MARKER || it == SECOND_PLAYER_MARKER || it == EMPTY_POSITION_MARKER }
    ) {
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

fun splitByUppercase(input: String): String {
    var prevUpperCase = false
    var currentWordIndex = 0

    return buildString {
        for ((index, char) in input.withIndex()) {
            prevUpperCase = if (char.isUpperCase()) {
                if (!prevUpperCase) {
                    if (index != 0) {
                        append(input.subSequence(currentWordIndex, index))
                        append(' ')
                    }
                    currentWordIndex = index
                }
                true
            } else {
                false
            }
        }

        append(input.subSequence(currentWordIndex, input.length))
    }
}
