package org.dots.game.sgf

import org.dots.game.DiagnosticSeverity
import org.dots.game.LineColumn
import org.dots.game.LineColumnDiagnostic
import org.dots.game.core.*
import org.dots.game.core.GameTree.NodeKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SgfWriterTests {
    @Test
    fun gameInfo() {
        val games = parseConvertAndCheck(sgfTestDataWithFullInfo)
        assertEquals(sgfTestDataWithFullInfo, SgfWriter.write(games))
    }

    @Test
    fun change() {
        val games = parseConvertAndCheck(sgfTestDataWithFullInfo)
        val game = games.single()
        with(game) {
            gameName = "Changed Game"
            player1Name = "New Player1"
            player1Rating = 230.0
            player1Team = "New Player1's Team"
            player2Name = "New Player2"
            player2Rating = 500.0
            player2Team = "New Player2's Team"
            komi = 0.0
            date = "2025-09-13"
            description = "A new game for SGF parser testing"
            comment = "New comment to node"
            place = "Rotterdam, Netherlands"
            event = "New test event"
            opening = "Unknown"
            annotator = "Me"
            copyright = "No copyright"
            source = "https://notago.ru/"
            time = 400.0
            overtime = "0+100"
            appInfo = AppInfo("https://notago.ru/", "2")
            round = "2 (final)"
        }
        assertEquals("(;GM[40]FF[4]CA[UTF-8]SZ[17:21]RU[russian]GN[Changed Game]PB[New Player1]BR[230]BT[New Player1's Team]PW[New Player2]WR[500]WT[New Player2's Team]KM[0]DT[2025-09-13]GC[A new game for SGF parser testing]C[New comment to node]PC[Rotterdam, Netherlands]EV[New test event]ON[Unknown]AN[Me]CP[No copyright]SO[https://notago.ru/]TM[400]OT[0+100]AP[https\\://notago.ru/:2]RO[2 (final)])", SgfWriter.write(games))
    }

    @Test
    fun whitespacesAndErrors() {
        val input = " ( ; GM[40] FF[4] SZ [39:32] KM[X] UP1[1] UP2[2] ) --- error "
        val games = parseConvertAndCheck(input, listOf(
            LineColumnDiagnostic("Unrecognized text `--- error `", LineColumn(1, 52), DiagnosticSeverity.Error),
            LineColumnDiagnostic("Property KM (Komi) has incorrect format: `X`. Expected: Real Number.", LineColumn(1, 33), DiagnosticSeverity.Warning),
            LineColumnDiagnostic("Property UP1 is unknown.", LineColumn(1, 36), DiagnosticSeverity.Warning),
            LineColumnDiagnostic("Property UP2 is unknown.", LineColumn(1, 43), DiagnosticSeverity.Warning),
        ))
        assertEquals(input, SgfWriter.write(games))
    }

    @Test
    fun noSgfMoveMoveProperties() {
        val field = Field.create(Rules.Standard)
        val games = Games.fromField(field)
        val gameTree = games.single().gameTree
        gameTree.addChild(mutableMapOf(
            GameTreeNode::player1Moves to GameProperty(listOf(MoveInfo(PositionXY(5, 6), Player.First))),
            GameTreeNode::player2Moves to GameProperty(listOf(MoveInfo(PositionXY(7, 7), Player.Second))),
            PropertiesHolder::player1TimeLeft to GameProperty(300),
            PropertiesHolder::player2TimeLeft to GameProperty(200),
            PropertiesHolder::comment to GameProperty("Some comment"),
            GameTreeNode::labels to GameProperty(
                listOf(
                    Label(PositionXY(10, 10), "label"),
                    Label(PositionXY(11, 10), ""),
                )
            ),
            GameTreeNode::circles to GameProperty(listOf(PositionXY(15, 10))),
            GameTreeNode::squares to GameProperty(listOf(PositionXY(15, 15))),
        ))

        checkOutput(
            "(;GM[40]FF[4]AP[DotsGame]SZ[39:32]AB[tp][uq]AW[up][tq];B[ef]W[gg]BL[300]WL[200]C[Some comment]LB[jj:label][kj:]CR[oj]SQ[oo])",
            games
        )
    }

    @Test
    fun noSgfWithGameResults() {
        val field = Field.create(Rules.Standard)
        val games = Games.fromField(field)
        val gameTree = games.single().gameTree
        gameTree.addChild(MoveInfo(PositionXY(10, 10), Player.First))

        // Add a final move
        assertEquals(NodeKind.New,gameTree.addChild(MoveInfo.createFinishingMove(Player.Second, ExternalFinishReason.Grounding)))

        checkOutput(
            "(;GM[40]FF[4]AP[DotsGame]SZ[39:32]AB[tp][uq]AW[up][tq]RE[B+2];B[jj];W[])",
            games
        )

        gameTree.back()

        assertEquals(NodeKind.New, gameTree.addChild(MoveInfo(PositionXY(20, 20), Player.Second)))
        assertEquals(NodeKind.New, gameTree.addChild(MoveInfo.createFinishingMove(Player.First, ExternalFinishReason.Resign)))

        // Check that secondary branches don't affect the result of the game
        checkOutput(
            "(;GM[40]FF[4]AP[DotsGame]SZ[39:32]AB[tp][uq]AW[up][tq]RE[B+2];B[jj](;W[])(;W[tt];B[resign]))",
            games
        )
    }

    private fun checkOutput(output: String, games: Games) {
        val sgf = SgfWriter.write(games)
        parseConvertAndCheck(sgf)
        assertEquals(output, sgf)
    }
}