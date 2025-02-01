package org.dots.game.sgf

abstract class SgfParsedNode(val textSpan: TextSpan)

sealed class SgfToken(textSpan: TextSpan, val value: String) : SgfParsedNode(textSpan) {
    open val isError: Boolean
        get() = textSpan.size == 0
}

data class TextSpan(val start: Int, val size: Int) {
    companion object {
        val Empty = TextSpan(0, 0)

        fun fromBounds(start: Int, end: Int): TextSpan = TextSpan(start, end - start)
    }

    val end
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

class LParenToken(textSpan: TextSpan) : SgfToken(textSpan, "(")

class RParenToken(textSpan: TextSpan) : SgfToken(textSpan, ")")

class SemicolonToken(textSpan: TextSpan) : SgfToken(textSpan, ";")

class IdentifierToken(value: String, textSpan: TextSpan) : SgfToken(textSpan, value)

class LSquareBracketToken(textSpan: TextSpan) : SgfToken(textSpan, "[")

class RSquareBracketToken(textSpan: TextSpan) : SgfToken(textSpan, "]")

class PropertyValueToken(value: String, textSpan: TextSpan) : SgfToken(textSpan, value)

class UnparsedTextToken(value: String, textSpan: TextSpan) : SgfToken(textSpan, value) {
    override val isError: Boolean = true
}