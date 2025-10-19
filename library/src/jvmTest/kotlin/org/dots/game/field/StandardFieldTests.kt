package org.dots.game.field

import org.dots.game.core.PosIsOccupiedIllegalMove
import org.dots.game.core.DotState
import org.dots.game.core.EndGameKind
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
            . * .
            . . .
        """) {
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 2, Player.First))
        }
    }

    @Test
    fun checkCapturing() {
        testFieldWithRollback(
            """
            . * .
            * + *
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
            . * .
            * + *
            . * .
        """) {
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun simpleCapture2() {
        testFieldWithRollback("""
            .  *2 .
            *0 +1 *3
            .  *5 *4
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun simpleCaptureForOppositePlayer() {
        testFieldWithRollback("""
            .  +4 .
            +3 *0 +1
            .  +2 .
        """) {
            assertEquals(0, it.player1Score)
            assertEquals(1, it.player2Score)
        }
    }

    @Test
    fun tripleCapture() {
        testFieldWithRollback("""
            . * . * .
            * + . + *
            . * + * .
            . . * . .
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
            . * * .
            * + . *
            . * * .
        """) {
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 2, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 2, Player.Second))
        }
    }

    @Test
    fun capturedDotIsNotActive() {
        testFieldWithRollback("""
            . * + .
            * + * +7
            . * + .
        """) {
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun emptyBase() {
        testFieldWithRollback("""
            . *  .
            * +4 *
            . *  .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun outerEmptyBaseSurroundsNonEmptyBase() {
        testFieldWithRollback(
            """
            ... ... +04 +05 +06
            ... +07 ... ... ... +08
            +09 ... ... +00 ... ... +10
            +11 ... +01 *20 +02 ... +12
            +13 ... ... +03 ... ... +14
            ... +15 ... ... ... +16
            ... ... +17 +18 +19
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
            ... ... +00 +01 +02
            ... +03 ... ... ... +04
            +05 ... ... +16 ... ... +06
            +07 ... +17 *20 +18 ... +08
            +09 ... ... +19 ... ... +10
            ... +11 ... ... ... +12
            ... ... +13 +14 +15
        """) {
            assertEquals(1, it.player2Score)

            assertIs<LegalMove>(it.makeMove(4, 2, Player.First))
            assertEquals(2, it.player2Score)
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase3() {
        testFieldWithRollback("""
            ... ... +20 +05 +06
            ... +07 ... ... ... +08
            +09 ... ... +00 ... ... +10
            +11 ... +01 *04 +02 ... +12
            +13 ... ... +03 ... ... +14
            ... +15 ... ... ... +16
            ... ... +17 +18 +19
        """) {
            assertEquals(1, it.player2Score)

            assertIs<LegalMove>(it.makeMove(4, 2, Player.First))
            assertEquals(2, it.player2Score)
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase4() {
        testFieldWithRollback("""
            ... ... +20 +05 +06
            ... +07 ... ... ... +08 +21 +22
            +09 ... ... +00 ... ... ... ... +10
            +11 ... +01 *04 +02 ... *25 ... +12
            +13 ... ... +03 ... ... ... ... +14
            ... +15 ... ... ... +16 +23 +24
            ... ... +17 +18 +19
        """) {
            assertEquals(2, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(8, 4, Player.First))
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase5() {
        testFieldWithRollback("""
            ... ... +04 +05 +06
            ... +07 ... ... ... +08 +20 +21
            +09 ... ... +00 ... ... ... ... +10
            +11 ... +01 ... +02 ... *24 ... +12
            +13 ... ... +03 ... ... ... ... +14
            ... +15 ... ... ... +16 +22 +23
            ... ... +17 +18 +19
        """) {
            assertEquals(1, it.player2Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(8, 4, Player.First))
        }
    }

    @Test
    fun emptyBaseWithinEmptyBase6() {
        testFieldWithRollback("""
            ... ... ... +04 +05 +06 +07 +08 ... ... ...
            ... ... +31 ... ... ... ... ... +09 ... ...
            ... +30 ... ... +32 +33 +34 ... ... +10 ...
            +29 ... ... +47 ... ... ... +35 ... ... +11
            +28 ... +46 ... ... +00 ... ... +36 ... +12
            +27 ... +45 ... +03 ... +01 ... +37 ... +13
            +26 ... +44 ... ... +02 ... ... +38 ... +14
            +25 ... ... +43 ... ... ... +39 ... ... +15
            ... +24 ... ... +42 +41 +40 ... ... +16 ...
            ... ... +23 ... ... ... ... ... +17 ... ...
            ... ... ... +22 +21 +20 +19 +18 ... ... ...
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
            . + +   + +
            + . .   . . +
            + . +14 . . *15 +
            + . .   . . +
            . + +   + +
        """) {
            assertEquals(1, it.player2Score)
        }
    }

    @Test
    fun player1CapturesByPlacingInsideEmptyBase() {
        testFieldWithRollback("""
            . *  +  .
            * +6 *7 +
            . *  +  .
        """) {
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun baseInsideBase() {
        testFieldWithRollback("""
            .. ..  * .. ..
            ..  *  +1 * ..
             *  +2 *0 +3 * 
            ..  *  +4 * ..
            .. ..  * .. ..
        """) {
            assertEquals(4, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun baseInsideBaseInsideBase() {
        testFieldWithRollback("""
            .. ..  ..  +
            .. ..  +   *05 +
            .. +   *06 +01 *07 +
             + *08 +02 *00 +03 *09 +
            .. +   *10 +04 *11 +
            .. ..  +   *12 +
            .. .. ..   +
        """) {
            assertEquals(0, it.player1Score)
            assertEquals(9, it.player2Score)
        }
    }

    @Test
    fun enemyEmptyBaseInsideBase() {
        testFieldWithRollback("""
            ..   ..  *5  .. ..
            ..   *4  +0 *6  ..
             *11 +3  .. +1 *7
            ..   *10 +2 *8  ..
            ..   ..  *9  .. ..
        """) {
            assertEquals(4, it.player1Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 3, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(3, 3, Player.Second))
        }
    }

    @Test
    fun enemyEmptyBasesInsideBase() {
        testFieldWithRollback("""
            ... ... ... *45 *46 *20 *21 *22 ... ... ...
            ... ... ... ... ... ... ... ... *23 ... ...
            ... *44 ... ... +19 +04 +05 ... ... *24 ...
            *43 ... ... +18 ... ... ... +06 ... ... *25
            *42 ... +17 ... ... +00 ... ... +07 ... *26
            *41 ... +16 ... +03 ... +01 ... +08 ... *27
            *40 ... +15 ... ... +02 ... ... +09 ... *28
            *39 ... ... +14 ... ... ... +10 ... ... *29
            ... *38 ... ... +13 +12 +11 ... ... *30 ...
            ... ... *37 ... ... ... ... ... *31 ... ...
            ... ... ... *36 *35 *34 *33 *32 ... ... ...
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
            .  *0 .
            *3 +  *1
            .  *2 .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkRightEdge() {
        testFieldWithRollback("""
            .  *3 .
            *2 +  *0
            .  *1 .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkBottomEdge() {
        testFieldWithRollback("""
            .  *2 .
            *1 + *3
            .  *0 .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkLeftEdge() {
        testFieldWithRollback("""
            .  *1 .
            *0 + *2
            .  *3 .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun dontProceedWithWalkIfEncounterABorder() {
        testFieldWithRollback("""
            .  .  *1 .
            *0 *5 +4 *2
            .  .  *3 .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun invalidateEmptyTerritoryWhenItsBorderCaptured() {
        testFieldWithRollback("""
            .  +0 +1 *6
            +5 .  *9 +2 *7
            .  +4 +3 *8
        """) {
            assertEquals(DotState.Empty, with (it) { Position(2, 2, it.realWidth).getState() })
        }
    }

    @Test
    fun invalidateEmptyTerritoryWhenItsBorderCaptured2() {
        testFieldWithRollback(
            """
            ... ... +04 +05 +06
            ... +07 ... ... ... +08
            +09 ... ... +00 ... ... +10 *20
            +11 ... +01 ... +02 ... ... +12 *21
            +13 ... ... +03 ... ... +14 *22
            ... +15 ... ... ... +16
            ... ... +17 +18 +19
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
            .  *1 *2 .
            *0 +6 .  *3
            .  *5 *4 .
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
            .  .  *2 .  .
            .  *1 .  *3 .
            .  *6 *7 *4 .
            .  .  *5 .  .
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
            .   .   *2  *3  .
            .   *12 .   .   *4
            *11 .   *1  .   *5
            *10 .   .   .   *6
            .   *9  *8  *7  .
        """) {
            assertIs<LegalMove>(it.makeMove(3, 2, Player.Second))
        }
    }

    @Test
    fun singularConnectionForEmptyBase() {
        testFieldWithRollback("""
            .  *3 *6 .
            *2 *1 .  *7
            .  *4 *5 .
        """) {
            assertIs<LegalMove>(it.makeMove(3, 2, Player.Second))
        }
    }

    @Test
    fun gameFinishedWithNoLegalMoves() {
        testFieldWithRollback("""
            * * *
            * * *
            * * *
        """.trimIndent()) {
            assertEquals(EndGameKind.NoLegalMoves, (it.gameResult as GameResult.Draw).endGameKind)
        }
    }

    @Test
    fun gameFinishedWithNoLegalMovesAndBase() {
        testFieldWithRollback("""
            * * * *
            * + . *
            * * * *
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
            * * * *
            * . . *
            * * * *
        """.trimIndent()) {
            assertNull(it.gameResult)
            it.makeMove(2, 2, Player.Second)
            val gameResult = it.gameResult as GameResult.ScoreWin
            assertEquals(EndGameKind.NoLegalMoves, gameResult.endGameKind)
            assertEquals(1.0, gameResult.score)
            assertEquals(Player.First, gameResult.winner)
        }
    }

    @Test
    fun gameFinishedWithNoLegalMovesAndBaseInsideBase() {
        testFieldWithRollback("""
            * * * * * *
            * . + + . *
            * + * . + *
            * . + + . *
            * * * * * *
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
. + + . + + .
+ . . . * . +
+ . + . + . +
+ . . + . . +
. + . . . + .
. . + + + . .
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
. . + + + . .
. + . . . + .
+ . . + . . +
+ . + * + . +
+ . . . . . +
. + + . + + .
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
. * * . * * .
* . . * + . *
* . * . * . *
* . . * . . *
. * . . . * .
. . * . * . .
""") { field, transformFunc ->
            assertIs<LegalMove>(field.makeMove(transformFunc(4, 6), Player.First))
            assertEquals(1, field.player1Score)
            assertIs<PosIsOccupiedIllegalMove>(field.makeMove(transformFunc(4, 3), Player.Second))
            assertIs<PosIsOccupiedIllegalMove>(field.makeMove(transformFunc(4, 3), Player.First))
        }
    }
}
