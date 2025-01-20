package org.dots.game.sgf

abstract class SgfNode(val textSpan: TextSpan)

sealed class SgfToken(textSpan: TextSpan, val value: String) : SgfNode(textSpan) {
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
    val gameTree: List<GameTree>,
    val unparsedText: UnparsedText?,
    val text: CharSequence,
    textSpan: TextSpan,
) : SgfNode(textSpan)

class GameTree(
    val lParen: LParenToken,
    val nodes: List<Node>,
    val childrenGameTrees: List<GameTree>,
    val rParen: RParenToken,
    textSpan: TextSpan,
) : SgfNode(textSpan)

class Node(
    val semicolon: SemicolonToken,
    val properties: List<Property>,
    textSpan: TextSpan,
) : SgfNode(textSpan)

class Property(
    val identifier: IdentifierToken,
    val value: List<PropertyValue>,
    textSpan: TextSpan,
) : SgfNode(textSpan)

class PropertyValue(
    val lSquareBracket: LSquareBracketToken,
    val propertyValueToken: PropertyValueToken,
    val rSquareBracket: RSquareBracketToken,
    textSpan: TextSpan,
) : SgfNode(textSpan)

class LParenToken(textSpan: TextSpan) : SgfToken(textSpan, "(")

class RParenToken(textSpan: TextSpan) : SgfToken(textSpan, ")")

class SemicolonToken(textSpan: TextSpan) : SgfToken(textSpan, ";")

class IdentifierToken(value: String, textSpan: TextSpan) : SgfToken(textSpan, value)

class LSquareBracketToken(textSpan: TextSpan) : SgfToken(textSpan, "[")

class RSquareBracketToken(textSpan: TextSpan) : SgfToken(textSpan, "]")

class PropertyValueToken(value: String, textSpan: TextSpan) : SgfToken(textSpan, value)

class UnparsedText(value: String, textSpan: TextSpan) : SgfToken(textSpan, value) {
    override val isError: Boolean = true
}