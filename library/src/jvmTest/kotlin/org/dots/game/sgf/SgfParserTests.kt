package org.dots.game.sgf

import org.dots.game.LineColumn
import org.dots.game.LineColumnDiagnostic
import org.dots.game.buildLineOffsets
import org.dots.game.toLineColumnDiagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SgfParserTests {
    @Test
    fun emptyOrWhitespace() {
        val emptySgf = parseAndCheck("")
        assertEquals(TextSpan(0, 0), emptySgf.textSpan)

        val whitespaceSgf = parseAndCheck("    ")
        assertEquals(TextSpan(4, 0), whitespaceSgf.textSpan)

        val incorrectSgf = parseAndCheck("  ---",
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 3))
        )
        assertEquals(TextSpan(2, 3), incorrectSgf.textSpan)
    }

    @Test
    fun tokens() {
        val gameTree = parseAndCheck("(;GC[info])").gameTree.single()
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
        fun check(expectedToken: UnparsedTextToken, text: String, vararg diagnostics: LineColumnDiagnostic = arrayOf()) {
            val actualToken = parseAndCheck(text, *diagnostics).unparsedText!!
            checkTokens(expectedToken, actualToken)
        }

        check(UnparsedTextToken("---", TextSpan(0, 3)), "---",
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 1))
        )
        check(UnparsedTextToken("---", TextSpan(1, 3)), "(---",
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 2)),
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 2)),
        )
        check(UnparsedTextToken("---", TextSpan(2, 3)), "(;---",
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 3)),
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 3)),
        )
        check(UnparsedTextToken("---", TextSpan(4, 3)), "(;GM---",
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 5)),
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 5)),
        )
        check(UnparsedTextToken("---", TextSpan(2, 3)), "  ---",
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 3)),
        )
    }

    @Test
    fun missingTokens() {
        checkTokens(
            RParenToken(TextSpan(10, 0)),
            parseAndCheck("(;GC[info]",
                LineColumnDiagnostic("Missing `)`", LineColumn(1, 11))
                ).gameTree.single().rParen
        )

        val gameTreeWithMissingOuterRParen = parseAndCheck("(;GC[info](;B[ee])",
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 19))
            ).gameTree.single()
        checkTokens(
            RParenToken(TextSpan(17, 1)),
            gameTreeWithMissingOuterRParen.childrenGameTrees.single().rParen
        )
        checkTokens(
            RParenToken(TextSpan(18, 0)),
            gameTreeWithMissingOuterRParen.rParen
        )

        val missingRSquare = parseAndCheck("(;GC[",
            LineColumnDiagnostic("Missing `]`", LineColumn(1, 6)),
            LineColumnDiagnostic(
                "Missing `)`", LineColumn(1, 6)
            )
        ).gameTree.single().nodes.single().properties.single().value.single().rSquareBracket
        checkTokens(RSquareBracketToken(TextSpan(5, 0)), missingRSquare)
    }

    @Test
    fun propertyValue() {
        fun checkValue(expectedToken: SgfToken, input: String, vararg diagnostics: LineColumnDiagnostic) {
            val root = parseAndCheck(input, *diagnostics)
            val propertyValueType =
                root.gameTree.single().nodes.single().properties.single().value.single().propertyValueToken
            checkTokens(expectedToken, propertyValueType)
        }

        checkValue(PropertyValueToken("", TextSpan(5, 0)), "(;GC[])")
        checkValue(PropertyValueToken("text", TextSpan(5, 4)), """(;GC[text])""")
        checkValue(PropertyValueToken("a:b", TextSpan(5, 3)), """(;GC[a:b])""")
        checkValue(PropertyValueToken("a\nb", TextSpan(5, 3)), "(;GC[a\nb])")
        checkValue(PropertyValueToken("""\]""", TextSpan(5, 2)), """(;GC[\]])""")
        checkValue(PropertyValueToken("""\\""", TextSpan(5, 2)), """(;GC[\\])""")

        // Check escaping at the end
        checkValue(
            PropertyValueToken("""\""", TextSpan(5, 1)), """(;GC[\""",
            LineColumnDiagnostic("Missing `]`", LineColumn(1, 7)),
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 7))
        )
    }

    @Test
    fun diagnosticReporter() {
        parseAndCheck("(;GC[info]---",
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 11)),
            LineColumnDiagnostic("Unrecognized text `---`", LineColumn(1, 11))
        )
        parseAndCheck("""(;GC[\""",
            LineColumnDiagnostic("Missing `]`", LineColumn(1, 7)),
            LineColumnDiagnostic("Missing `)`", LineColumn(1, 7)),
        )
    }

    @Test
    fun whitespaces() {
        val sgf = parseAndCheck(" ( ;\nGC [info1 info2 ] ) --- ",
            LineColumnDiagnostic("Unrecognized text `--- `", LineColumn(2, 21)),
        )
        assertEquals(TextSpan(25, 4), sgf.unparsedText!!.textSpan)
        assertEquals(TextSpan(24, 1), sgf.unparsedText.leadingWs!!.textSpan)

        val gameTree = sgf.gameTree.single()
        assertEquals(TextSpan(1, 23), gameTree.textSpan)
        assertEquals(TextSpan(0, 1), gameTree.lParen.leadingWs!!.textSpan)
        assertEquals(TextSpan(22, 1), gameTree.rParen.leadingWs!!.textSpan)

        val node = gameTree.nodes.single()
        assertEquals(TextSpan(3, 19), node.textSpan)
        assertEquals(TextSpan(2, 1), node.semicolon.leadingWs!!.textSpan)

        val property = node.properties.single()
        assertEquals(TextSpan(5, 17), property.textSpan)
        assertEquals(TextSpan(4, 1), property.identifier.leadingWs!!.textSpan)
        assertEquals("\n", property.identifier.leadingWs.value)

        val propertyValue = property.value.single()
        assertEquals(TextSpan(8, 14), propertyValue.textSpan)
        assertEquals(TextSpan(7, 1), propertyValue.lSquareBracket.leadingWs!!.textSpan)
        assertNull(propertyValue.rSquareBracket.leadingWs)

        val propertyValueToken = propertyValue.propertyValueToken
        checkTokens(PropertyValueToken("info1 info2 ", TextSpan(9, 12)), propertyValueToken)
        assertNull(propertyValueToken.leadingWs)
    }

    @Test
    fun differentCharsInProperties() {
        parseAndCheck("(;CoPyright[Copyright (c)]Свойство[Свойство]0Prop[0Prop]財產[財產])")
    }

    private fun parseAndCheck(input: String, vararg expectedDiagnostics: LineColumnDiagnostic): SgfRoot {
        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { input.buildLineOffsets() }
        val actualDiagnostics = mutableListOf<LineColumnDiagnostic>()
        val sgfRoot = SgfParser.parse(input) {
            actualDiagnostics.add(it.toLineColumnDiagnostic(lineOffsets))
        }
        assertEquals(expectedDiagnostics.toList(), actualDiagnostics)
        return sgfRoot
    }

    private fun checkTokens(expectedToken: SgfToken, actualToken: SgfToken) {
        assertEquals(expectedToken::class, actualToken::class)
        assertEquals(expectedToken.value, actualToken.value)
        assertEquals(expectedToken.textSpan, actualToken.textSpan)
    }
}