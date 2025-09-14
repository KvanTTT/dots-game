package org.dots.game.sgf

import org.dots.game.Diagnostic
import org.dots.game.DiagnosticSeverity

/**
 * Reference: https://homepages.cwi.nl/~aeb/go/misc/sgf.html
 *
 * Grammar:
 *
 * ```
 * Collection = GameTree+
 * GameTree   = '(' Node+ GameTree* ')'
 * Node       = ';' Property*
 * Property   = UcLetter+ PropertyValue+
 * PropertyValue  = '[' PropertyValueType? ']'
 * PropertyValueType = '~[:\\\]]+'
 * UcLetter   = 'A'..'Z'
 * ```
 */
class SgfParser private constructor(val text: CharSequence, val diagnosticReporter: (Diagnostic) -> Unit) {
    companion object {
        fun parse(sgfText: String, diagnosticReporter: (Diagnostic) -> Unit): SgfRoot {
            return SgfParser(sgfText, diagnosticReporter).parse()
        }
    }

    private var currentIndex = 0
    private var lastWhitespace: WhitespaceToken? = null

    private fun parse(): SgfRoot {
        parseWhitespaces()

        val gameTrees = buildList {
            while (expect('(')) {
                add(parseGameTree())
            }
        }

        val unparsedTextToken = if (currentIndex < text.length) {
            UnparsedTextToken(
                text.substring(currentIndex),
                TextSpan(currentIndex, text.length - currentIndex),
                extractLeadingWhitespaces()
            ).also { it.reportIfError() }
        } else {
            null
        }

        val textSpan = TextSpan.fromBounds(
            (gameTrees.firstOrNull() ?: unparsedTextToken)?.textSpan?.start ?: text.length,
            (unparsedTextToken ?: gameTrees.lastOrNull())?.textSpan?.end ?: text.length,
        )

        return SgfRoot(gameTrees, unparsedTextToken, text, textSpan)
    }

    private fun parseGameTree(): SgfGameTree {
        val lParen = LParenToken(matchChar('('), extractLeadingWhitespaces())

        parseWhitespaces()

        val nodes = buildList {
            while (expect(';')) {
                add(parseNode())
            }
        }

        val childrenGameTrees = buildList {
            while (expect('(')) {
                add(parseGameTree())
            }
        }

        val rParen = RParenToken(tryMatchChar(')'), extractLeadingWhitespaces()).also { it.reportIfError() }

        parseWhitespaces()

        return SgfGameTree(lParen, nodes, childrenGameTrees, rParen, textSpanFromNodes(lParen, rParen))
    }

    private fun parseNode(): SgfNode {
        val semicolon = SemicolonToken(matchChar(';'), extractLeadingWhitespaces())

        parseWhitespaces()

        val properties = buildList {
            while (checkBounds() && text[currentIndex].checkIdentifierHeadChar()) {
                add(parseProperty())
            }
        }

        return SgfNode(semicolon, properties, textSpanFromNodes(semicolon, properties.lastOrNull() ?: semicolon))
    }

    private fun parseProperty(): SgfPropertyNode {
        val initialIndex = currentIndex
        currentIndex++

        while (checkBounds() && text[currentIndex].checkIdentifierTailChar()) {
            currentIndex++
        }

        val identifier = IdentifierToken(text.substring(initialIndex, currentIndex), getCurrentTextSpan(initialIndex), extractLeadingWhitespaces())

        parseWhitespaces()

        val values = buildList {
            while (expect('[')) {
                add(parsePropertyValue())
            }
        }

        return SgfPropertyNode(identifier, values, textSpanFromNodes(identifier, values.lastOrNull() ?: identifier))
    }

    private fun parsePropertyValue(): SgfPropertyValueNode {
        val lSquareBracket = LSquareBracketToken(matchChar('['), extractLeadingWhitespaces())

        val propertyValueToken = parsePropertyValueToken()

        val rSquareBracket = RSquareBracketToken(tryMatchChar(']')).also { it.reportIfError() }

        parseWhitespaces()

        return SgfPropertyValueNode(lSquareBracket, propertyValueToken, rSquareBracket, textSpanFromNodes(lSquareBracket, rSquareBracket))
    }

    private fun parsePropertyValueToken(): PropertyValueToken {
        val initialIndex = currentIndex

        while (checkBounds()) {
            when (text[currentIndex]) {
                ']' -> break
                '\\' -> {
                    currentIndex++
                    if (checkBounds()) {
                        currentIndex++
                    }
                }
                else -> currentIndex++
            }
        }

        return PropertyValueToken(text.substring(initialIndex, currentIndex), getCurrentTextSpan(initialIndex))
    }

    private fun Char.checkIdentifierTailChar(): Boolean = checkIdentifierHeadChar() || this in '0'..'9'

    private fun Char.checkIdentifierHeadChar(): Boolean = this in 'A'..'Z'

    private fun matchChar(char: Char): TextSpan {
        require(text[currentIndex] == char)
        return TextSpan(currentIndex, 1).also { currentIndex++ }
    }

    private fun tryMatchChar(char: Char): TextSpan {
        val initialIndex = currentIndex
        val length = if (expect(char)) {
            currentIndex++
            1
        } else {
            0
        }
        return TextSpan(initialIndex, length)
    }

    private fun expect(char: Char): Boolean = checkBounds() && text[currentIndex] == char

    private fun checkBounds(): Boolean = currentIndex >= 0 && currentIndex < text.length

    private fun SgfToken.reportIfError() {
        if (isError) {
            val errorText = when {
                textSpan.size == 0 -> "Missing `${value}`"
                this is UnparsedTextToken -> "Unrecognized text `${value}`"
                else -> error("Unexpected error token")
            }

            diagnosticReporter(Diagnostic(errorText, textSpan, DiagnosticSeverity.Error))
        }
    }

    private fun parseWhitespaces() {
        val initialIndex = currentIndex
        while (checkBounds() && text[currentIndex].isWhitespace()) {
            currentIndex++
        }
        lastWhitespace = if (currentIndex > initialIndex)
            WhitespaceToken(text.substring(initialIndex, currentIndex), getCurrentTextSpan(initialIndex))
        else
            null
    }

    private fun textSpanFromNodes(startNode: SgfParsedNode, endNode: SgfParsedNode): TextSpan {
        return TextSpan.fromBounds(startNode.textSpan.start, endNode.textSpan.end)
    }

    private fun getCurrentTextSpan(initialIndex: Int): TextSpan = TextSpan(initialIndex, currentIndex - initialIndex)

    private fun extractLeadingWhitespaces(): WhitespaceToken? = lastWhitespace.also { lastWhitespace = null }
}