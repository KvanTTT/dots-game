package org.dots.game.sgf

import org.dots.game.DiagnosticSeverity
import org.dots.game.LineColumn
import org.dots.game.LineColumnDiagnostic
import org.dots.game.core.EndGameKind
import org.dots.game.core.GameResult
import org.dots.game.core.Label
import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.PositionXY
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SgfConverterMovesTests {
    @Test
    fun initialPositionsAreCorrect() {
        val game = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]AB[az][mm]AW[AF][])"
        ).single()
        val rules = game.rules
        assertEquals(4, rules.initialMoves.size)
        checkMoveDisregardExtraInfo(1, 26, Player.First, rules.initialMoves[0])
        checkMoveDisregardExtraInfo(13, 13, Player.First, rules.initialMoves[1])
        checkMoveDisregardExtraInfo(27, 32, Player.Second, rules.initialMoves[2])
        val groundingMove = rules.initialMoves[3]
        assertEquals(null, groundingMove.positionXY)
        assertEquals(Player.Second, groundingMove.player)
    }

    @Test
    fun initialPositionsAreIncorrect() {
        val rules = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]AB[a!]AW[-Z][1234])", listOf(
                LineColumnDiagnostic(
                    "Property AB (Player1 initial dots) has incorrect y coordinate `!`.",
                    LineColumn(1, 27),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property AW (Player2 initial dots) has incorrect x coordinate `-`.",
                    LineColumn(1, 32),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property AW (Player2 initial dots) has incorrect x coordinate `1`.",
                    LineColumn(1, 36),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property AW (Player2 initial dots) has incorrect y coordinate `2`.",
                    LineColumn(1, 37),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property AW (Player2 initial dots) has incorrect extra chars: `34`",
                    LineColumn(1, 38),
                    DiagnosticSeverity.Error
                ),
            )
        ).single().rules
        assertTrue(rules.initialMoves.isEmpty())
    }

    @Test
    fun initialPositionsOverwriting() {
        val game = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]AB[ab][mm][ab])", listOf(
                LineColumnDiagnostic(
                    "Property AB (Player1 initial dots) value `ab` overwrites one the previous position.",
                    LineColumn(1, 34),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single()
        val rules = game.rules
        assertEquals(2, rules.initialMoves.size)
        checkMoveDisregardExtraInfo(13, 13, Player.First,rules.initialMoves[0])
        checkMoveDisregardExtraInfo(1, 2, Player.First,rules.initialMoves[1])
    }

    @Test
    fun initialPositionsOfPlayer2OverwritesPlayer1() {
        val game = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]AB[ab]AW[ab])", listOf(
                LineColumnDiagnostic(
                    "Property AW (Player2 initial dots) value `ab` overwrites one the previous position of first player AB (Player1 initial dots).",
                    LineColumn(1, 32),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single()
        val rules = game.rules
        checkMoveDisregardExtraInfo(1, 2, Player.Second, rules.initialMoves.single())
    }

    @Test
    fun initialPositionsIncorrectBecauseOfPlacedToCapturedTerritory() {
        // . .  *1 *2 .
        // . *0 +6 +7 *3
        // . .  *5 *4 .
        checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]AB[bb][ca][da][eb][dc][cc]AW[cb][db])", listOf(
                LineColumnDiagnostic(
                    "Property AW (Player2 initial dots) has incorrect value `db`. The dot at position (4;2) is already placed or captured (move number: 8).",
                    LineColumn(1, 56),
                    DiagnosticSeverity.Error,
                )
            )
        )
    }

    @Test
    fun branches() {
        val gameTree = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32];B[bb](;B[bc];W[bd])(;B[cc];W[cd]))"
        ).single().gameTree
        val rootNode = gameTree.rootNode
        assertEquals(1, rootNode.children.size)
        val mainBranchNode = rootNode.getNextNode(2, 2, Player.First)!!
        assertEquals(2, mainBranchNode.children.size)

        var branch1Node = mainBranchNode.getNextNode(2, 3, Player.First)!!
        branch1Node = branch1Node.getNextNode(2, 4, Player.Second)!!
        assertTrue(branch1Node.children.isEmpty())

        var branch2Node = mainBranchNode.getNextNode(3, 3, Player.First)!!
        branch2Node = branch2Node.getNextNode(3, 4, Player.Second)!!
        assertTrue(branch2Node.children.isEmpty())
    }

    @Test
    fun incorrectMovesSequence() {
        val gameTree = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[10:10];B[bb];B[__];W[bb];W[c];W[ml])", listOf(
                LineColumnDiagnostic(
                    "Property B (Player1 move) has incorrect x coordinate `_`.",
                    LineColumn(1, 32),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property B (Player1 move) has incorrect y coordinate `_`.",
                    LineColumn(1, 33),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property W (Player2 move) has incorrect value `bb`. The dot at position (2;2) is already placed or captured (move number: 2).",
                    LineColumn(1, 38),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property W (Player2 move) has incorrect y coordinate ``.",
                    LineColumn(1, 45),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property W (Player2 move) has incorrect value `ml`. The position (13;12) is out of bounds 10:10 (move number: 2).",
                    LineColumn(1, 49),
                    DiagnosticSeverity.Error
                ),
            )
        ).single().gameTree
        val nextNode = gameTree.rootNode.getNextNode(2, 2, Player.First)!!
        assertEquals(1, nextNode.children.size)
    }

    @Disabled("Currently it's unclear how to handle moves in root scope properly and if it's even needed")
    @Test
    fun movesInRootNode() {
        val gameTree = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]B[cc]W[dd])",
            listOf(
                LineColumnDiagnostic(
                    "Property B (Player1 move) declared in Root scope, but should be declared in Move scope.",
                    LineColumn(1, 23),
                    DiagnosticSeverity.Warning
                ),
                LineColumnDiagnostic(
                    "Property W (Player2 move) declared in Root scope, but should be declared in Move scope.",
                    LineColumn(1, 28),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single().gameTree
        var nextNode = gameTree.rootNode.getNextNode(3, 3, Player.First)!!
        nextNode = nextNode.getNextNode(4, 4, Player.Second)!!
        assertTrue(nextNode.children.isEmpty())
    }

    @Test
    fun gameInfoInMoveNode() {
        val gameTree = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32];GN[Game name not in root]B[cc])",
            listOf(
                LineColumnDiagnostic(
                    "Property GN (Game Name) declared in Move scope, but should be declared in Root scope. The value is ignored.",
                    LineColumn(1, 24),
                    DiagnosticSeverity.Error
                ),
            )
        ).single().gameTree
        val nextNode = gameTree.rootNode.getNextNode(3, 3, Player.First)!!
        assertTrue(nextNode.children.isEmpty())
    }

    @Test
    fun capturingPositions() {
        // .  *2  .
        // *1 +0 *3
        // .  *4  .
        val gameTree = checkParseAndUnparse(
            "(;GM[40]FF[4]AP[zagram.org]SZ[39:32];B[bb];W[ab];W[ba];W[cb];W[bc.bccbbaabbc])", listOf(
                LineColumnDiagnostic(
                    "Property W (Player2 move) has capturing positions that are not yet supported: (2;3), (3;2), (2;1), (1;2), (2;3) (`bccbbaabbc`). The capturing is calculated automatically according game rules for this and next cases.",
                    LineColumn(1, 67),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single().gameTree

        val fieldStride = gameTree.field.realWidth
        var node = gameTree.rootNode.getNextNode(2, 2, Player.First)!!
        node = node.getNextNode(1, 2, Player.Second)!!
        node = node.getNextNode(2, 1, Player.Second)!!
        node = node.getNextNode(3, 2, Player.Second)!!
        node = node.getNextNode(2, 3, Player.Second)!!

        assertEquals(
            listOf(
                Position(2, 3, fieldStride),
                Position(3, 2, fieldStride),
                Position(2, 1, fieldStride),
                Position(1, 2, fieldStride),
            ),
            (node.moveResults.single() as LegalMove).bases.single().closurePositions.toList()
        )
    }

    @Test
    fun notaGoGrounding() {
        fun checkGrounding(player: Player) {
            val pla: Char
            val opp: Char
            if (player == Player.First) {
                pla = 'B'
                opp = 'W'
            } else {
                pla = 'W'
                opp = 'B'
            }

            // Player wins by property with its move
            checkParseAndUnparse("(;GM[40]FF[4]AP[NOTAGO:4.4.0]SZ[10:10]RE[$pla+G];$pla[ba];$pla[cb];$pla[bc];$pla[ab];$opp[bb])")

            // Player wins by property with opp move
            checkParseAndUnparse("(;GM[40]FF[4]AP[NOTAGO:4.4.0]SZ[10:10]RE[$pla+G];$opp[bb];$pla[aa])")

            // NotaGo erasures info about grounding in case of Draw

            // Draw by property with player's move
            checkParseAndUnparse("(;GM[40]FF[4]AP[NOTAGO:4.4.0]SZ[10:10]RE[Draw];$pla[aa];$opp[ba])")

            // Draw by property with opp player's move
            checkParseAndUnparse("(;GM[40]FF[4]AP[NOTAGO:4.4.0]SZ[10:10]RE[Draw];$pla[aa])")
        }

        checkGrounding(Player.First)
        checkGrounding(Player.Second)
    }

    @Test
    fun groundingWithExplicitMove() {
        val game =
            checkParseAndUnparse("(;FF[4]GM[40]CA[UTF-8]AP[katago]SZ[10:8]RE[B+1]AB[ed][fe]AW[ee][fd];B[ef];W[de];B[df];W[hd];B[ce];W[hf];B[cd];W[ff];B[dc];W[cf];B[hb];W[ic];B[db];W[gg];B[da];W[bg];B[])").single()

        game.gameTree.rewindToEnd()
        val gameResult = game.gameTree.field.gameResult as GameResult.ScoreWin
        assertEquals(Player.First, gameResult.winner)
        assertEquals(1.0, gameResult.score)
        assertEquals(EndGameKind.Grounding, gameResult.endGameKind)
    }

    @Test
    fun groundingWithMovesAfterIt() {
        checkParseAndUnparse("(;FF[4]GM[40]CA[UTF-8]SZ[10]RE[0];B[bb];W[];B[])",
            listOf(
                LineColumnDiagnostic("Property B (Player1 move) is defined (``), however the game is already over with the result: Draw (Grounding)",
                    LineColumn(1, 47),
                    DiagnosticSeverity.Error
                )
            )
        )
        checkParseAndUnparse("(;FF[4]GM[40]CA[UTF-8]SZ[10]RE[W+1];B[bb];W[cc];B[];W[cc])",
            listOf(
                LineColumnDiagnostic(
                    "Property W (Player2 move) is defined (`cc`), however the game is already over with the result: ScoreWin(winner: Second, 1.0, Grounding, player: First)",
                    LineColumn(1, 55),
                    DiagnosticSeverity.Error
                )
            )
        )
    }

    @Test
    fun groundingAndNoLegalMoves() {
        checkParseAndUnparse("(;FF[4]AP[katago]RU[sui1]GM[40]SZ[2]RE[0];B[aa];W[ab];B[bb];W[ba];B[])", listOf(
            LineColumnDiagnostic("Property B (Player1 move) is defined (``), however the game is already over with the result: Draw (NoLegalMoves)",
                LineColumn(1, 69), DiagnosticSeverity.Error)
        ))

        // Allow last grounding move because there is no simple way to update the number of legal moves iteratively
        // if suicidal moves are allowed
        checkParseAndUnparse("(;FF[4]AP[katago]RU[sui0]GM[40]SZ[2]RE[0];B[aa];W[ab];B[bb];W[ba];B[])")
    }

    @Test
    fun noLegalMovesAndDefinedGameResult() {
        checkParseAndUnparse("(;GM[40]FF[4]SZ[2]RE[0];B[aa];B[ab];W[ba];W[bb])")
        checkParseAndUnparse("(;GM[40]FF[4]SZ[3]RE[Draw];W[aa];W[ca];W[bb];W[ac];W[cc];B[ba];B[ab];B[cb];B[bc])",
            listOf(
                LineColumnDiagnostic(
                    "Property RE (Result) has Draw value but the result of the game from field: First wins.",
                    LineColumn(1, 82),
                    DiagnosticSeverity.Warning
                )
            )
        )

        checkParseAndUnparse("(;GM[40]FF[4]SZ[3]RE[B+1];W[aa];W[ca];W[bb];W[ac];W[cc];B[ba];B[ab];B[cb];B[bc])")
        checkParseAndUnparse("(;GM[40]FF[4]SZ[3]RE[W+1];W[aa];W[ca];W[bb];W[ac];W[cc];B[ba];B[ab];B[cb];B[bc])",
            listOf(
                LineColumnDiagnostic(
                    "Property RE (Result) has `Second` player as winner but the result of the game from field: First wins.",
                    LineColumn(1, 81),
                    DiagnosticSeverity.Warning
                )
            )
        )
    }

    @Test
    fun definedWinGameResultByRePropertyDoesntMatchResultFromField() {
        checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]RE[W+2];B[bb];W[ab];W[ba];W[cb];W[bc])", listOf(
                LineColumnDiagnostic(
                    "Property RE (Result) has value `2` that doesn't match score from game field `1`.",
                    LineColumn(1, 61),
                    DiagnosticSeverity.Warning
                )
            )
        )
    }

    @Test
    fun definedWinGameResultByRePropertyDoesntMatchResultFromFieldInSubBranch() {
        // TODO: the last branch should be preserved
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[39:32]RE[W+2];B[bb];W[ab];W[ba];W[cb];W[bc](;B[dd]))", listOf(
                LineColumnDiagnostic(
                    "Property RE (Result) has value `2` that doesn't match score from game field `1`.",
                    LineColumn(1, 68),
                    DiagnosticSeverity.Warning
                )
            )
        )
    }

    @Test
    fun ignoreFieldResultScoreValidationInSecondaryBranches() {
        checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]RE[W+1];B[bb];W[ab];W[ba];W[cb];W[bc](;B[dd])(;B[cc];W[dc];W[cd]))"
        )
    }

    @Test
    fun movesComment() {
        val firstComment = "Player 1 starts the game"
        val secondComment = "Player 2 surrounds a dot"

        val gameTree = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32];B[bb]C[$firstComment];W[ab];W[ba];W[cb];W[bc]C[$secondComment])"
        ).single().gameTree

        gameTree.next()
        assertEquals(firstComment, gameTree.currentNode.comment)

        gameTree.rewindToEnd()
        assertEquals(secondComment, gameTree.currentNode.comment)
    }

    @Test
    fun labelsIncorrect() {
        checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];LB[aa])",
            listOf(
                LineColumnDiagnostic("Property LB (Label) has unexpected separator ``", LineColumn(1, 29))
            )).single().gameTree
        checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];LB[aa.])",
            listOf(
                LineColumnDiagnostic("Property LB (Label) has unexpected separator `.`", LineColumn(1, 29))
            )).single().gameTree
    }

    @Test
    fun labels() {
        val gameTree = checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];W[aa]LB[aa:])").single().gameTree
        gameTree.next()
        val label = gameTree.currentNode.labels!!.single()
        assertEquals(PositionXY(1, 1), label.positionXY)
        assertEquals("", label.text)

        val gameTree2 = checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];W[cc]LB[aa:label][bb:label2])").single().gameTree
        gameTree2.next()
        val labels = gameTree2.currentNode.labels
        assertEquals(listOf(Label(PositionXY(1, 1), "label"), Label(PositionXY(2, 2), "label2")), labels)
    }

    @Test
    fun circles() {
        val gameTree = checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];B[bb]CR[ba][cb][bc][ab])").single().gameTree
        gameTree.next()
        val circles = gameTree.currentNode.circles
        assertEquals(
            listOf(
                PositionXY(2, 1),
                PositionXY(3, 2),
                PositionXY(2, 3),
                PositionXY(1, 2)
            ), circles
        )
    }

    @Test
    fun circlesIncorrect() {
        val gameTree = checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];B[bb]CR[ba.cd])", listOf(
            LineColumnDiagnostic("Property CR (Circle) has incorrect extra chars: `.cd`", LineColumn(1, 34))
        )).single().gameTree
        gameTree.next()
        val circles = gameTree.currentNode.circles
        assertEquals(listOf(PositionXY(2, 1)), circles)
    }

    @Test
    fun squares() {
        val gameTree = checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32];B[bb]SQ[ba][cb][bc][ab])").single().gameTree
        gameTree.next()
        val squares = gameTree.currentNode.squares
        assertEquals(
            listOf(
                PositionXY(2, 1),
                PositionXY(3, 2),
                PositionXY(2, 3),
                PositionXY(1, 2)
            ), squares
        )
    }

    @Test
    fun warnAboutCapturingPositionsThatAreNotYetSupportedOnlyOnce() {
        checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[5];B[cc]W[cb][dc][cd][bc.cbdccdbc];B[ca][db][ec][dd][ce][bd][ac][bb.cadbecddcebdac])",
            listOf(
                LineColumnDiagnostic(
                    "Property W (Player2 move) has capturing positions that are not yet supported: (3;2), (4;3), (3;4), (2;3) (`cbdccdbc`). The capturing is calculated automatically according game rules for this and next cases.",
                    LineColumn(1, 42),
                    DiagnosticSeverity.Warning
                ),
            )
        )
    }

    @Test
    fun timeLeft() {
        val game = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]BL[315]WL[312];B[bc]BL[308.3];W[de]WL[294.23])",
        ).single()
        assertEquals(game.player1TimeLeft, 315.0)
        assertEquals(game.player2TimeLeft, 312.0)
        var nextNode = game.gameTree.rootNode.getNextNode(2, 3, Player.First)!!
        assertEquals(308.3, nextNode.player1TimeLeft)
        nextNode = nextNode.getNextNode(4, 5, Player.Second)!!
        assertEquals(294.23, nextNode.player2TimeLeft)
    }
}