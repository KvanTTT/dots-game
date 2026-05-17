package org.dots.game.field

import org.dots.game.core.PosIsOccupiedIllegalMove
import org.dots.game.core.DotState
import org.dots.game.core.EndGameKind
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.GameResult
import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import org.dots.game.core.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

import kotlin.test.assertNull

class StandardFieldTests : FieldTests() {
    @Test
    fun testInvalidMoveOnThePlacedDot() {
        testFieldWithRollback("""
. . .
. x .
. . .
""") {
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 2, Player.First))
        }
    }

    @Test
    fun checkCapturing() {
        testFieldWithRollback(
            """
. x .
x o x
. . .
"""
        ) {
            val legalMove = assertIs<LegalMove>(it.makeMove(2, 3, Player.First))
            val base = legalMove.bases.single()
            assertEquals(
                listOf(
                    Position(2, 3, it.realWidth),
                    Position(3, 2, it.realWidth),
                    Position(2, 1, it.realWidth),
                    Position(1, 2, it.realWidth),
                ),
                base.closurePositions.toList()
            )
            assertEquals(Position(2, 2, it.realWidth), base.rollbackPositions.toList().single())
        }
    }

    @Test
    fun simpleCapture() {
        testFieldWithRollback("""
. x .
x o x
. x .
""") {
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun simpleCapture2() {
        testFieldWithRollback("""
.  x2 .
x0 o1 x3
.  x5 x4
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun simpleCaptureForOppositePlayer() {
        testFieldWithRollback("""
.  o4 .
o3 x0 o1
.  o2 .
""") {
            assertEquals(0, it.player1Score)
            assertEquals(1, it.player2Score)
        }
    }

    @Test
    fun tripleCapture() {
        testFieldWithRollback("""
. x . x .
x o . o x
. x o x .
. . x . .
""") {
            val legalMove = assertIs<LegalMove>(it.makeMove(3, 2, Player.First))
            val bases = legalMove.bases
            assertEquals(3, bases.size)
            assertEquals(3, it.player1Score)
        }
    }

    @Test
    fun invalidMoveWithinBase(){
        testFieldWithRollback("""
. x x .
x o . x
. x x .
""") {
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 2, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 2, Player.Second))
        }
    }

    @Test
    fun capturedDotIsNotActive() {
        testFieldWithRollback("""
.  x  o  .
x  o  x  o7
.  x  o   .
""") {
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun emptyBase() {
        testFieldWithRollback("""
.  x  .
x  o4 x
.  x  .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun outerEmptyBaseSurroundsNonEmptyBase() {
        testFieldWithRollback(
            """
.   .   o04 o05 o06
.   o07 .   .   .   o08
o09 .   .   o00 .   .   o10
o11 .   o01 x20 o02 .   o12
o13 .   .   o03 .   .   o14
.   o15 .   .   .   o16
.   .   o17 o18 o19
"""
        ) {
            assertEquals(1, it.player2Score)

            assertIs<LegalMove>(it.makeMove(6, 4, Player.Second))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(6, 4, Player.Second))

            assertIs<LegalMove>(it.makeMove(4, 2, Player.First))
            assertEquals(2, it.player2Score)
        }
    }

    @Test
    fun newNonEmptyBaseWithinOuterEmptyBase() {
        testFieldWithRollback("""
.   .   o00 o01 o02
.   o03 .   .   .   o04
o05 .   .   o16 .   .   o06
o07 .   o17 x20 o18 .   o08
o09 .   .   o19 .   .   o10
.   o11 .   .   .   o12
.   .   o13 o14 o15
""") {
            assertEquals(1, it.player2Score)

            assertIs<LegalMove>(it.makeMove(4, 2, Player.First))
            assertEquals(2, it.player2Score)
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase3() {
        testFieldWithRollback("""
.   .   o20 o05 o06
.   o07 .   .   .   o08
o09 .   .   o00 .   .   o10
o11 .   o01 x04 o02 .   o12
o13 .   .   o03 .   .   o14
.   o15 .   .   .   o16
.   .   o17 o18 o19
""") {
            assertEquals(1, it.player2Score)

            assertIs<LegalMove>(it.makeMove(4, 2, Player.First))
            assertEquals(2, it.player2Score)
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase4() {
        testFieldWithRollback("""
.   .   o20 o05 o06
.   o07 .   .   .   o08 o21 o22
o09 .   .   o00 .   .   .   .   o10
o11 .   o01 x04 o02 .   x25 .   o12
o13 .   .   o03 .   .   .   .   o14
.   o15 .   .   .   o16 o23 o24
.   .   o17 o18 o19
""") {
            assertEquals(2, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(8, 4, Player.First))
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase5() {
        testFieldWithRollback("""
.    .    o04  o05  o06
.    o07  .    .    .    o08  o20  o21
o09  .    .    o00  .    .    .    .    o10
o11  .    o01  .    o02  .    x24  .    o12
o13  .    .    o03  .    .    .    .    o14
.    o15  .    .    .    o16  o22  o23
.    .    o17  o18  o19
""") {
            assertEquals(1, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(8, 4, Player.First))
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase6() {
        testFieldWithRollback("""
.    .    .    o04  o05  o06  o07  o08  .    .    .
.    .    o31  .    .    .    .    .    o09  .    .
.    o30  .    .    o32  o33  o34  .    .    o10  .
o29  .    .    o47  .    .    .    o35  .    .    o11
o28  .    o46  .    .    o00  .    .    o36  .    o12
o27  .    o45  .    o03  .    o01  .    o37  .    o13
o26  .    o44  .    .    o02  .    .    o38  .    o14
o25  .    .    o43  .    .    .    o39  .    .    o15
.    o24  .    .    o42  o41  o40  .    .    o16  .
.    .    o23  .    .    .    .    .    o17  .    .
.    .    .    o22  o21  o20  o19  o18  .    .    .
""") {
            assertIs<LegalMove>(it.makeMove(6, 6, Player.First))
            assertEquals(1, it.player2Score)
            assertIs<LegalMove>(it.makeMove(4, 6, Player.First))
            assertEquals(2, it.player2Score)
            assertIs<LegalMove>(it.makeMove(2, 6, Player.First))
            assertEquals(3, it.player2Score)

            assertIs<LegalMove>(it.unmakeMove())
            assertIs<LegalMove>(it.unmakeMove())
            assertIs<LegalMove>(it.unmakeMove())

            assertIs<LegalMove>(it.makeMove(4, 6, Player.First))
            assertEquals(1, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(6, 6, Player.First))
            assertIs<LegalMove>(it.makeMove(2, 6, Player.First))
            assertEquals(2, it.player2Score)

            assertIs<LegalMove>(it.unmakeMove())
            assertIs<LegalMove>(it.unmakeMove())

            assertIs<LegalMove>(it.makeMove(2, 6, Player.First))
            assertEquals(1, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(6, 6, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(4, 6, Player.First))
            assertIs<LegalMove>(it.unmakeMove())
        }
    }

    @Test
    fun preventEndlessLoopOnCaptureChecking() {
        testFieldWithRollback("""
.    o    o    o    o
o    .    .    .    .    o
o    .    o14  .    .    x15  o
o    .    .    .    .    o
.    o    o    o    o
""") {
            assertEquals(1, it.player2Score)
        }
    }

    @Test
    fun player1CapturesByPlacingInsideEmptyBase() {
        testFieldWithRollback("""
.  x  o  .
x  o  .  o
.  x  o  .
""") {
            assertIs<LegalMove>(it.makeMove(3, 2, Player.First))
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun correctEmptyPlayerAfterGroundingAndRollback() {
        testFieldWithRollback("""
.  x  o  .
x  o  .  o
.  x  o  .
""") {
            val secondPlayerEmptyBasePos = Position(3, 2, it.realWidth)
            assertEquals(Player.Second, with(it) { secondPlayerEmptyBasePos.getState().getEmptyTerritoryPlayer() })

            assertIs<LegalMove>(it.makeMove(positionXY = null, Player.Second, ExternalFinishReason.Grounding))
            assertIs<LegalMove>(it.unmakeMove())

            assertEquals(Player.Second, with(it) { secondPlayerEmptyBasePos.getState().getEmptyTerritoryPlayer() })
        }
    }

    @Test
    fun baseInsideBase() {
        testFieldWithRollback("""
.  .  x  .  .
.  x  o1 x  .
x  o2 x0 o3 x
.  x  o4 x  .
.  .  x  .  .
""") {
            assertEquals(4, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun baseInsideBaseInsideBase() {
        testFieldWithRollback("""
.   .   .   o
.   .   o   x05 o
.   o   x06 o01 x07 o
o   x08 o02 x00 o03 x09 o
.   o   x10 o04 x11 o
.   .   o   x12 o
.   .   .   o
""") {
            assertEquals(0, it.player1Score)
            assertEquals(9, it.player2Score)
        }
    }

    @Test
    fun enemyEmptyBaseInsideBase() {
        testFieldWithRollback("""
.   .   x5  .   .
.   x4  o0  x6  .
x11 o3  .   o1  x7
.   x10 o2  x8  .
.   .   x9  .   .
""") {
            assertEquals(4, it.player1Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 3, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 3, Player.Second))
        }
    }

    @Test
    fun enemyEmptyBasesInsideBase() {
        testFieldWithRollback("""
.   .   .   x45 x46 x20 x21 x22 .   .   .
.   .   .   .   .   .   .   .   x23 .   .
.   x44 .   .   o19 o04 o05 .   .   x24 .
x43 .   .   o18 .   .   .   o06 .   .   x25
x42 .   o17 .   .   o00 .   .   o07 .   x26
x41 .   o16 .   o03 .   o01 .   o08 .   x27
x40 .   o15 .   .   o02 .   .   o09 .   x28
x39 .   .   o14 .   .   .   o10 .   .   x29
.   x38 .   .   o13 o12 o11 .   .   x30 .
.   .   x37 .   .   .   .   .   x31 .   .
.   .   .   x36 x35 x34 x33 x32 .   .   .
""") {
            assertIs<LegalMove>(it.makeMove(3, 2, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(6, 6, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(6, 6, Player.Second))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(4, 6, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 6, Player.First))
            assertEquals(20, it.player1Score)
            assertIs<LegalMove>(it.unmakeMove())

            assertIs<LegalMove>(it.makeMove(6, 6, Player.First))
            assertEquals(1, it.player2Score)
            assertIs<LegalMove>(it.makeMove(3, 2, Player.First))
            assertEquals(20, it.player1Score)
            assertEquals(0, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(4, 6, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 6, Player.First))
            assertIs<LegalMove>(it.unmakeMove())
        }
    }

    @Test
    fun checkTopEdge() {
        testFieldWithRollback("""
.  x0 .
x3 o  x1
.  x2 .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkRightEdge() {
        testFieldWithRollback("""
.  x3 .
x2 o  x0
.  x1 .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkBottomEdge() {
        testFieldWithRollback("""
.  x2 .
x1 o  x3
.  x0 .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkLeftEdge() {
        testFieldWithRollback("""
.  x1 .
x0 o  x2
.  x3 .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun dontProceedWithWalkIfEncounterABorder() {
        testFieldWithRollback("""
.  .  x1 .
x0 x5 o4 x2
.  .  x3 .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun invalidateEmptyTerritoryWhenItsBorderCaptured() {
        testFieldWithRollback("""
.  o0 o1 x6
o5 .  x9 o2 x7
.  o4 o3 x8
""") {
            assertEquals(DotState.Empty, with (it) { Position(2, 2, it.realWidth).getState() })
        }
    }

    @Test
    fun invalidateEmptyTerritoryWhenItsBorderCaptured2() {
        testFieldWithRollback(
            """
.   .   o04 o05 o06
.   o07 .   .   .   o08
o09 .   .   o00 .   .   o10 x20
o11 .   o01 .   o02 .   .   o12 x21
o13 .   .   o03 .   .   o14 x22
.   o15 .   .   .   o16
.   .   o17 o18 o19
"""
        ) {
            assertIs<LegalMove>(it.makeMove(7, 4, Player.First))
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)

            assertIs<LegalMove>(it.makeMove(2, 4, Player.First))
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)

            assertIs<LegalMove>(it.makeMove(4, 4, Player.First))
            assertEquals(1, it.player1Score)
            assertEquals(1, it.player2Score)
        }
    }

    @Test
    fun tryPlacingToTerritoryThatBecameCapturedAfterBeingEmpty() {
        testFieldWithRollback(
            """
.  x1 x2 .
x0 o6 .  x3
.  x5 x4 .
"""
        ) {
            assertEquals(1, it.player1Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 2, Player.First))
        }
    }

    @Test
    fun capturingAfterPlacingToEmptyTerritoryShouldBeMinimal() {
        testFieldWithRollback("""
.  .  .  .  .
.  .  x2 .  .
.  x1 .  x3 .
.  x6 x7 x4 .
.  .  x5 .  .
.  .  .  .  .
""") {
            val legalMove = assertIs<LegalMove>(it.makeMove(3, 3, Player.Second))
            val base = legalMove.bases.single()
            assertEquals(1, base.rollbackPositions.size)
        }
    }

    @Test
    fun complexEmptyBase() {
        testFieldWithRollback("""
.   .   x2  x3  .
.   x12 .   .   x4
x11 .   x1  .   x5
x10 .   .   .   x6
.   x9  x8  x7  .
""") {
            assertIs<LegalMove>(it.makeMove(3, 2, Player.Second))
        }
    }

    @Test
    fun singularConnectionForEmptyBase() {
        testFieldWithRollback("""
.  x3 x6 .
x2 x1 .  x7
.  x4 x5 .
""") {
            assertIs<LegalMove>(it.makeMove(3, 2, Player.Second))
        }
    }

    @Test
    fun gameFinishedWithNoLegalMoves() {
        testFieldWithRollback("""
x x x
x x x
x x x
""".trimIndent()) {
            assertEquals(EndGameKind.NoLegalMoves, (it.gameResult as GameResult.Draw).endGameKind)
        }
    }

    @Test
    fun gameFinishedWithNoLegalMovesAndBase() {
        testFieldWithRollback("""
x x x x
x o . x
x x x x
""".trimIndent()) {
            val gameResult = it.gameResult as GameResult.ScoreWin
            assertEquals(EndGameKind.NoLegalMoves, gameResult.endGameKind)
            assertEquals(1.0, gameResult.score)
            assertEquals(Player.First, gameResult.winner)
        }
    }

    @Test
    fun noGameFinishedIfSuicidalMoveToEmptyTerritoryRemains() {
        testFieldWithRollback("""
x x x x
x . . x
x x x x
""".trimIndent()) {
            assertNull(it.gameResult)
            assertIs<LegalMove>(it.makeMove(2, 2, Player.Second))
            val gameResult = it.gameResult as GameResult.ScoreWin
            assertEquals(EndGameKind.NoLegalMoves, gameResult.endGameKind)
            assertEquals(1.0, gameResult.score)
            assertEquals(Player.First, gameResult.winner)
        }
    }

    @Test
    fun gameFinishedWithNoLegalMovesAndBaseInsideBase() {
        testFieldWithRollback("""
x x x x x x
x . o o . x
x o x . o x
x . o o . x
x x x x x x
""".trimIndent()) {
            val gameResult = it.gameResult as GameResult.ScoreWin
            assertEquals(EndGameKind.NoLegalMoves, gameResult.endGameKind)
            assertEquals(6.0, gameResult.score)
            assertEquals(Player.First, gameResult.winner)
        }
    }

    @Test
    fun adjacentInnerEmptyBaseAndOuterNonEmptyBase() {
        testFieldWithTransformsAndRollback("""
. o o . o o .
o . . . x . o
o . o . o . o
o . . o . . o
. o . . . o .
. . o o o . .
""") { field, transformFunc ->
            assertIs<LegalMove>(field.makeMove(transformFunc(4, 2), Player.Second))
            assertEquals(1, field.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(field.makeMove(transformFunc(4, 5), Player.First))
            assertIs<PosIsOccupiedIllegalMove>(field.makeMove(transformFunc(4, 5), Player.Second))
        }
    }

    @Test
    fun adjacentInnerNonEmptyBaseAndOuterEmptyBase() {
        testFieldWithTransformsAndRollback("""
. . o o o . .
. o . . . o .
o . . o . . o
o . o x o . o
o . . . . . o
. o o . o o .
""") { field, transformFunc ->
            assertIs<LegalMove>(field.makeMove(transformFunc(4, 5), Player.Second))
            assertEquals(1, field.player2Score)
            assertIs<LegalMove>(field.makeMove(transformFunc(4, 2), Player.First))
            assertEquals(2, field.player2Score)
        }
    }

    @Test
    fun noDanglingSurrounding() {
        testFieldWithTransformsAndRollback("""
. x x . x x .
x . . x o . x
x . x . x . x
x . . x . . x
. x . . . x .
. . x . x . .
""") { field, transformFunc ->
            assertIs<LegalMove>(field.makeMove(transformFunc(4, 6), Player.First))
            assertEquals(1, field.player1Score)
            assertIs<PosIsOccupiedIllegalMove>(field.makeMove(transformFunc(4, 3), Player.Second))
            assertIs<PosIsOccupiedIllegalMove>(field.makeMove(transformFunc(4, 3), Player.First))
        }
    }
}
