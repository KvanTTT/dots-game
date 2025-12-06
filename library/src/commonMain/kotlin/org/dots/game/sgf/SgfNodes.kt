package org.dots.game.sgf

import org.dots.game.ParsedNode

abstract class SgfParsedNode(textSpan: TextSpan) : ParsedNode(textSpan) {
    override fun toString(): String = textSpan.toString()
}

sealed class SgfToken(textSpan: TextSpan, val value: String, val leadingWs: WhitespaceToken?) : SgfParsedNode(textSpan) {
    open val isError: Boolean
        get() = textSpan.size == 0
}

data class TextSpan(val start: Int, val size: Int) {
    companion object {
        val Empty: TextSpan = TextSpan(0, 0)

        fun fromBounds(start: Int, end: Int): TextSpan = TextSpan(start, end - start)
    }

    val end: Int
        get() = start + size

    override fun toString(): String = "[$start..$end)"
}

class SgfRoot(
    val gameTree: List<SgfGameTree>,
    val unparsedText: UnparsedTextToken?,
    val text: CharSequence,
    textSpan: TextSpan,
) : SgfParsedNode(textSpan)

class SgfGameTree(
    val lParen: LParenToken,
    val nodes: List<SgfNode>,
    val childrenGameTrees: List<SgfGameTree>,
    val rParen: RParenToken,
    textSpan: TextSpan,
) : SgfParsedNode(textSpan)

class SgfNode(
    val semicolon: SemicolonToken,
    val properties: List<SgfPropertyNode>,
    textSpan: TextSpan,
) : SgfParsedNode(textSpan)

class SgfPropertyNode(
    val identifier: IdentifierToken,
    val value: List<SgfPropertyValueNode>,
    textSpan: TextSpan,
) : SgfParsedNode(textSpan)

class SgfPropertyValueNode(
    val lSquareBracket: LSquareBracketToken,
    val propertyValueToken: PropertyValueToken,
    val rSquareBracket: RSquareBracketToken,
    textSpan: TextSpan,
) : SgfParsedNode(textSpan)

class LParenToken(textSpan: TextSpan, leadingWs: WhitespaceToken? = null) : SgfToken(textSpan, "(", leadingWs)

class RParenToken(textSpan: TextSpan, leadingWs: WhitespaceToken? = null) : SgfToken(textSpan, ")", leadingWs)

class SemicolonToken(textSpan: TextSpan, leadingWs: WhitespaceToken? = null) : SgfToken(textSpan, ";", leadingWs)

class IdentifierToken(value: String, textSpan: TextSpan, leadingWs: WhitespaceToken? = null) : SgfToken(textSpan, value, leadingWs)

class LSquareBracketToken(textSpan: TextSpan, leadingWs: WhitespaceToken? = null) : SgfToken(textSpan, "[", leadingWs)

class RSquareBracketToken(textSpan: TextSpan) : SgfToken(textSpan, "]", null)

class PropertyValueToken(value: String, textSpan: TextSpan) : SgfToken(textSpan, value, null)

class UnparsedTextToken(value: String, textSpan: TextSpan, leadingWs: WhitespaceToken? = null) : SgfToken(textSpan, value, leadingWs) {
    override val isError: Boolean = true
}

class WhitespaceToken(value: String, textSpan: TextSpan): SgfToken(textSpan, value, null)