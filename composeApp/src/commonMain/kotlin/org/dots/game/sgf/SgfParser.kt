package org.dots.game.sgf

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
class SgfParser private constructor(val text: CharSequence, val errorReporter: (SgfToken) -> Unit) {
    companion object {
        val whitespaceChars = setOf(' ', '\n', '\r', '\t')

        fun parse(sgfText: String, errorReporter: (SgfToken) -> Unit = {}): SgfRoot {
            return SgfParser(sgfText, errorReporter).parse()
        }
    }

    private var currentIndex = 0

    private fun parse(): SgfRoot {
        skipWhitespaces()

        val gameTrees = buildList {
            while (expect('(')) {
                add(parseGameTree())
            }
        }

        val unparsedText = if (currentIndex < text.length) {
            UnparsedText(
                text.substring(currentIndex),
                TextSpan(currentIndex, text.length - currentIndex)
            ).also { it.reportIfError() }
        } else {
            null
        }

        val textSpan = TextSpan.fromBounds(
            (gameTrees.firstOrNull() ?: unparsedText)?.textSpan?.start ?: text.length,
            (unparsedText ?: gameTrees.lastOrNull())?.textSpan?.end ?: text.length,
        )

        return SgfRoot(gameTrees, unparsedText, text, textSpan)
    }

    private fun parseGameTree(): GameTree {
        val lParen = LParenToken(matchChar('('))

        skipWhitespaces()

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

        val rParen = RParenToken(tryMatchChar(')')).also { it.reportIfError() }

        skipWhitespaces()

        return GameTree(lParen, nodes, childrenGameTrees, rParen, textSpanFromNodes(lParen, rParen))
    }

    private fun parseNode(): Node {
        val semicolon = SemicolonToken(matchChar(';'))

        skipWhitespaces()

        val properties = buildList {
            while (checkBounds() && text[currentIndex].checkIdentifierChar()) {
                add(parseProperty())
            }
        }

        return Node(semicolon, properties, textSpanFromNodes(semicolon, properties.lastOrNull() ?: semicolon))
    }

    private fun parseProperty(): Property {
        val initialIndex = currentIndex
        currentIndex++

        while (checkBounds() && text[currentIndex].checkIdentifierChar()) {
            currentIndex++
        }

        val identifier = IdentifierToken(text.substring(initialIndex, currentIndex), getCurrentTextSpan(initialIndex))

        skipWhitespaces()

        val values = buildList {
            while (expect('[')) {
                add(parsePropertyValue())
            }
        }

        return Property(identifier, values, textSpanFromNodes(identifier, values.lastOrNull() ?: identifier))
    }

    private fun parsePropertyValue(): PropertyValue {
        val lSquareBracket = LSquareBracketToken(matchChar('['))

        val propertyValueToken = parsePropertyValueToken()

        val rSquareBracket = RSquareBracketToken(tryMatchChar(']')).also { it.reportIfError() }

        skipWhitespaces()

        return PropertyValue(lSquareBracket, propertyValueToken, rSquareBracket, textSpanFromNodes(lSquareBracket, rSquareBracket))
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

    private fun Char.checkIdentifierChar(): Boolean = this >= 'A' && this <= 'Z'

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
            errorReporter(this)
        }
    }

    private fun skipWhitespaces() {
        while (checkBounds() && text[currentIndex] in whitespaceChars) {
            currentIndex++
        }
    }

    private fun textSpanFromNodes(startNode: SgfNode, endNode: SgfNode): TextSpan {
        return TextSpan.fromBounds(startNode.textSpan.start, endNode.textSpan.end)
    }

    private fun getCurrentTextSpan(initialIndex: Int): TextSpan = TextSpan(initialIndex, currentIndex - initialIndex)
}