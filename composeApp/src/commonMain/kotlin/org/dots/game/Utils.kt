package org.dots.game

import org.dots.game.core.EMPTY_POSITION
import org.dots.game.core.FIRST_PLAYER_MARKER
import org.dots.game.core.Game
import org.dots.game.core.GameInfo
import org.dots.game.core.GameTree
import org.dots.game.core.SECOND_PLAYER_MARKER
import org.dots.game.dump.FieldParser
import org.dots.game.sgf.Sgf
import org.dots.game.sgf.SgfParser

suspend fun openOrLoad(pathOrContent: String, diagnosticReporter: ((Diagnostic) -> Unit) = { }): Pair<InputType, Game?> {
    try {
        val inputType = getInputType(pathOrContent)
        var sgf: String?

        when (inputType) {
            InputType.FieldContent -> {
                val field = FieldParser.parseAndConvertWithNoInitialMoves(pathOrContent, diagnosticReporter)
                val gameTree = GameTree(field).apply {
                    for (move in field.moveSequence) {
                        add(move)
                    }
                }
                return inputType to Game(GameInfo.Empty, gameTree)
            }

            InputType.SgfContent -> {
                sgf = pathOrContent
            }

            is InputType.SgfFile -> {
                if (inputType.isIncorrect) {
                    diagnosticReporter(Diagnostic("Incorrect file `${inputType.name}`. The only .sgf files are supported", textSpan = null))
                    return inputType to null
                }

                sgf = readFileText(inputType.refinedPath)
            }

            is InputType.SgfUrl -> {
                if (inputType.isIncorrect) {
                    diagnosticReporter(Diagnostic("Incorrect url. The only `$zagramLinkPrefix` is supported", textSpan = null))
                    return inputType to null
                }

                sgf = downloadFileText(inputType.refinedPath)
            }

            is InputType.Other -> {
                diagnosticReporter(Diagnostic("Unrecognized input type. Insert a path to .sgf file or a link to zagram.org game", textSpan = null))
                sgf = null
            }
        }

        return inputType to sgf?.let { Sgf.parseAndConvert(it, onlySingleGameSupported = true, diagnosticReporter).firstOrNull() }
    } catch (e: Exception) {
        diagnosticReporter(Diagnostic(e.message ?: e.toString(), textSpan = null, DiagnosticSeverity.Critical))
    }
    return InputType.Other to null
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
    if (refinedPath.endsWith(".sgf")) {
        return InputType.SgfFile(refinedPath, extractFileName(refinedPath))
    }

    if (fileExists(refinedPath) || filePathRegex.matches(refinedPath)) {
        return InputType.SgfFile(refinedPath, extractFileName(refinedPath), isIncorrect = true)
    }

    if (tryParseField(input)) return InputType.FieldContent
    if (tryParseSgf(input)) return InputType.SgfContent

    var notWhitespaceCharIndex = 0
    while (notWhitespaceCharIndex < input.length && input[notWhitespaceCharIndex].isWhitespace()) {
        notWhitespaceCharIndex++
    }

    if (input.elementAtOrNull(notWhitespaceCharIndex).let { it == FIRST_PLAYER_MARKER || it == SECOND_PLAYER_MARKER || it == EMPTY_POSITION }
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
