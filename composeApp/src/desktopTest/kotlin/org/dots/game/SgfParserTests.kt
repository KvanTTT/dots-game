package org.dots.game

import org.dots.game.sgf.IdentifierToken
import org.dots.game.sgf.LParenToken
import org.dots.game.sgf.LSquareBracketToken
import org.dots.game.sgf.SgfPropertyValueNode
import org.dots.game.sgf.UnparsedTextToken
import org.dots.game.sgf.RParenToken
import org.dots.game.sgf.RSquareBracketToken
import org.dots.game.sgf.SgfParser
import org.dots.game.sgf.SgfToken
import org.dots.game.sgf.TextSpan
import org.dots.game.sgf.PropertyValueToken
import org.dots.game.sgf.SemicolonToken
import kotlin.test.Test
import kotlin.test.assertEquals

class SgfParserTests {
    @Test
    fun emptyOrWhitespace() {
        val emptySgf = SgfParser.parse("")
        assertEquals(TextSpan(0, 0), emptySgf.textSpan)

        val whitespaceSgf = SgfParser.parse("    ")
        assertEquals(TextSpan(4, 0), whitespaceSgf.textSpan)

        val incorrectSgf = SgfParser.parse("  ---")
        assertEquals(TextSpan(2, 3), incorrectSgf.textSpan)
    }

    @Test
    fun tokens() {
        val gameTree = SgfParser.parse("(;GC[info])").gameTree.single()
        checkTokens(LParenToken(TextSpan(0, 1)), gameTree.lParen)
        checkTokens(RParenToken(TextSpan(10, 1)), gameTree.rParen)

        val node = gameTree.nodes.single()
        checkTokens(SemicolonToken(TextSpan(1, 1)), node.semicolon)

        val property = node.properties.single()
        checkTokens(IdentifierToken("GC", TextSpan(2, 2)), property.identifier)

        val propertyValue = property.value.single()
        checkTokens(LSquareBracketToken(TextSpan(4, 1)), propertyValue.lSquareBracket)
        checkTokens(RSquareBracketToken(TextSpan(9, 1)), propertyValue.rSquareBracket)
    }

    @Test
    fun unparsedText() {
        fun check(expectedToken: UnparsedTextToken, text: String) {
            val actualToken = SgfParser.parse(text).unparsedText!!
            checkTokens(expectedToken, actualToken)
        }

        check(UnparsedTextToken("---", TextSpan(0, 3)), "---")
        check(UnparsedTextToken("---", TextSpan(1, 3)), "(---")
        check(UnparsedTextToken("---", TextSpan(2, 3)), "(;---")
        check(UnparsedTextToken("---", TextSpan(4, 3)), "(;GM---")

        check(UnparsedTextToken("---", TextSpan(2, 3)), "  ---")
    }

    @Test
    fun missingTokens() {
        checkTokens(
            RParenToken(TextSpan(10, 0)),
            SgfParser.parse("(;GC[info]").gameTree.single().rParen
        )

        val gameTreeWithMissingOuterRParen = SgfParser.parse("(;GC[info](;B[ee])").gameTree.single()
        checkTokens(
            RParenToken(TextSpan(17, 1)),
            gameTreeWithMissingOuterRParen.childrenGameTrees.single().rParen
        )
        checkTokens(
            RParenToken(TextSpan(18, 0)),
            gameTreeWithMissingOuterRParen.rParen
        )

        val missingRSquare = SgfParser.parse("(;GC[").gameTree.single().nodes.single().properties.single().value.single().rSquareBracket
        checkTokens(RSquareBracketToken(TextSpan(5, 0)), missingRSquare)
    }

    @Test
    fun propertyValue() {
        fun checkValue(expectedToken: SgfToken, input: String) {
            val propertyValueType = parseAndGetPropertyValue(input).propertyValueToken
            checkTokens(expectedToken, propertyValueType)
        }

        checkValue(PropertyValueToken("", TextSpan(5, 0)), "(;GC[])")
        checkValue(PropertyValueToken("text", TextSpan(5, 4)), """(;GC[text])""")
        checkValue(PropertyValueToken("a:b", TextSpan(5, 3)), """(;GC[a:b])""")
        checkValue(PropertyValueToken("a\nb", TextSpan(5, 3)), "(;GC[a\nb])")
        checkValue(PropertyValueToken("""\]""", TextSpan(5, 2)), """(;GC[\]])""")
        checkValue(PropertyValueToken("""\\""", TextSpan(5, 2)), """(;GC[\\])""")

        // Check escaping at the end
        checkValue(PropertyValueToken("""\""", TextSpan(5, 1)), """(;GC[\""")
    }

    @Test
    fun errorReporter() {
        fun checkErrors(expectedErrorTokens: List<SgfToken>, input: String) {
            var currentErrorIndex = 0
            SgfParser.parse(input) {
                checkTokens(expectedErrorTokens[currentErrorIndex++], it)
            }
            assertEquals(currentErrorIndex, expectedErrorTokens.size)
        }

        checkErrors(
            listOf(
                RParenToken(TextSpan(10, 0)),
                UnparsedTextToken("---", TextSpan(10, 3))),
            "(;GC[info]---"
        )

        checkErrors(
            listOf(
                RSquareBracketToken(TextSpan(6, 0)),
                RParenToken(TextSpan(6, 0))
            ),
            """(;GC[\"""
        )
    }

    @Test
    fun whitespaces() {
        val sgf = SgfParser.parse(" ( ;\nGC [info1 info2 ] ) ---")
        assertEquals(TextSpan(25, 3), sgf.unparsedText!!.textSpan)

        val gameTree = sgf.gameTree.single()
        assertEquals(TextSpan(1, 23), gameTree.textSpan)

        val node = gameTree.nodes.single()
        assertEquals(TextSpan(3, 19), node.textSpan)

        val property = node.properties.single()
        assertEquals(TextSpan(5, 17), property.textSpan)

        val propertyValue = property.value.single()
        assertEquals(TextSpan(8, 14), propertyValue.textSpan)

        checkTokens(PropertyValueToken("info1 info2 ", TextSpan(9, 12)), propertyValue.propertyValueToken)
    }

    private fun parseAndGetPropertyValue(input: String): SgfPropertyValueNode {
        val root = SgfParser.parse(input)
        return root.gameTree.single().nodes.single().properties.single().value.single()
    }

    private fun checkTokens(expectedToken: SgfToken, actualToken: SgfToken) {
        assertEquals(expectedToken::class, actualToken::class)
        assertEquals(expectedToken.value, actualToken.value)
        assertEquals(expectedToken.textSpan, actualToken.textSpan)
    }
}