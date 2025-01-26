package org.dots.game

import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.x
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StandardFieldTests : FieldTests() {
    @Test
    fun invalidMoveOnThePlacedPot() {
        val field  = initialize("""
            . . .
            . * .
            . . .
        """)

        assertNull(field.makeMove(2 x 2, Player.First))
    }

    @Test
    fun connectionWithoutBase() {
        val field  = initialize("""
            * *  *
            * *4 .
            * .  .
        """)
    }

    @Test
    fun checkCapturing() {
        val field  = initialize("""
            . * .
            * + *
            . . .
        """)

        val moveResult = field.makeMove(2 x 3, Player.First)!!
        val base = moveResult.bases.single()
        assertEquals(
            listOf(Position(2, 3), Position(3, 2), Position(2, 1), Position(1, 2)),
            base.closurePositions
        )
        assertEquals(setOf(Position(2, 2)), base.territoryPreviousStates.keys)
    }

    @Test
    fun simpleCapture() {
        val field = initialize("""
            . * .
            * + *
            . * .
        """)

        assertEquals(1, field.player1Score)
        assertEquals(0, field.player2Score)
    }

    @Test
    fun simpleCapture2() {
        val field = initialize("""
            .  *2 .
            *0 +1 *3
            .  *5 *4
        """)

        assertEquals(1, field.player1Score)
    }

    @Test
    fun simpleCaptureForOppositePlayer() {
        val field = initialize("""
            . + .
            + * +
            . + .
        """)

        assertEquals(0, field.player1Score)
        assertEquals(1, field.player2Score)
    }

    @Test
    fun tripleCapture() {
        val field = initialize("""
            . * . * .
            * + . + *
            . * + * .
            . . * . .
        """)

        val moveResult = field.makeMove(3 x 2, Player.First)!!
        val bases = moveResult.bases
        assertEquals(3, bases.size)
        assertEquals(3, field.player1Score)
    }

    @Test
    fun invalidMoveWithinBase(){
        val field = initialize("""
            . * * .
            * + . *
            . * * .
        """)

        assertNull(field.makeMove(3 x 2, Player.First))
        assertNull(field.makeMove(3 x 2, Player.Second))
    }

    @Test
    fun capturedDotIsNotActive() {
        val field = initialize("""
            . * + .
            * + * +7
            . * + .
        """)

        assertEquals(1, field.player1Score)
        assertEquals(0, field.player2Score)
    }

    @Test
    fun emptyBase() {
        val field = initialize("""
            . *  .
            * +4 *
            . *  .
        """)

        assertEquals(1, field.player1Score)
    }

    @Test
    fun emptyBaseWithinEmptyBase() {
        val field = initialize("""
            ... ... +04 +05 +06
            ... +07 ... ... ... +08
            +09 ... ... +00 ... ... +10
            +11 ... +01 *20 +02 ... +12
            +13 ... ... +03 ... ... +14
            ... +15 ... ... ... +16
            ... ... +17 +18 +19
        """)

        assertEquals(1, field.player2Score)

        assertNotNull(field.makeMove(4 x 2, Player.First))
        assertEquals(2, field.player2Score)
    }

    @Test
    fun emptyBaseWithinEmptyBase2() {
        val field = initialize("""
            ... ... +00 +01 +02
            ... +03 ... ... ... +04
            +05 ... ... +16 ... ... +06
            +07 ... +17 *20 +18 ... +08
            +09 ... ... +19 ... ... +10
            ... +11 ... ... ... +12
            ... ... +13 +14 +15
        """)
        assertEquals(1, field.player2Score)

        assertNotNull(field.makeMove(4 x 2, Player.First))
        assertEquals(2, field.player2Score)
    }

    @Test
    fun emptyBaseWithinEmptyBase3() {
        val field = initialize("""
            ... ... +20 +05 +06
            ... +07 ... ... ... +08
            +09 ... ... +00 ... ... +10
            +11 ... +01 *04 +02 ... +12
            +13 ... ... +03 ... ... +14
            ... +15 ... ... ... +16
            ... ... +17 +18 +19
        """)

        assertEquals(1, field.player2Score)

        assertNotNull(field.makeMove(4 x 2, Player.First))
        assertEquals(2, field.player2Score)
    }

    @Test
    fun emptyBaseWithinEmptyBase4() {
        val field = initialize("""
            ... ... +20 +05 +06
            ... +07 ... ... ... +08 +21 +22
            +09 ... ... +00 ... ... ... ... +10
            +11 ... +01 *04 +02 ... *25 ... +12
            +13 ... ... +03 ... ... ... ... +14
            ... +15 ... ... ... +16 +23 +24
            ... ... +17 +18 +19
        """)

        assertEquals(2, field.player2Score)
        assertNull(field.makeMove(8 x 4, Player.First))
    }

    @Test
    fun emptyBaseWithinEmptyBase5() {
        val field = initialize("""
            ... ... +04 +05 +06
            ... +07 ... ... ... +08 +20 +21
            +09 ... ... +00 ... ... ... ... +10
            +11 ... +01 ... +02 ... *24 ... +12
            +13 ... ... +03 ... ... ... ... +14
            ... +15 ... ... ... +16 +22 +23
            ... ... +17 +18 +19
        """)

        assertEquals(1, field.player2Score)
        assertNull(field.makeMove(8 x 4, Player.First))
        assertNull(field.makeMove(4 x 4, Player.First))
    }

    @Test
    fun preventEndlessLoopOnCaptureChecking() {
        val field = initialize("""
            . + +   + +
            + . .   . . +
            + . +14 . . *15 +
            + . .   . . +
            . + +   + +
        """)

        assertEquals(1, field.player2Score)
    }

    @Test
    fun player1CapturesByPlacingInsideEmptyBase() {
        val field = initialize("""
            . *  +  .
            * +6 *7 +
            . *  +  .
        """)

        assertEquals(1, field.player1Score)
        assertEquals(0, field.player2Score)
    }

    @Test
    fun baseInsideBase() {
        val field = initialize("""
            .. ..  * .. ..
            ..  *  +1 * ..
             *  +2 *0 +3 * 
            ..  *  +4 * ..
            .. ..  * .. ..
        """)

        assertEquals(4, field.player1Score)
        assertEquals(0, field.player2Score)
    }

    @Test
    fun baseInsideBaseInsideBase() {
        val field = initialize("""
            .. ..  ..  +
            .. ..  +   *05 +
            .. +   *06 +01 *07 +
             + *08 +02 *00 +03 *09 +
            .. +   *10 +04 *11 +
            .. ..  +   *12 +
            .. .. ..   +
        """)

        assertEquals(0, field.player1Score)
        assertEquals(9, field.player2Score)
    }

    @Test
    fun checkEdges() {
        val leftEdge = initialize("""
            .  *0 .
            *3 +  *1
            .  *2 .
        """)
        assertEquals(1, leftEdge.player1Score)

        val topEdge = initialize("""
            .  *3 .
            *2 +  *0
            .  *1 .
        """)
        assertEquals(1, topEdge.player1Score)

        val rightEdge = initialize("""
            .  *2 .
            *1 + *3
            .  *0 .
        """)
        assertEquals(1, rightEdge.player1Score)

        val bottomEdge = initialize("""
            .  *1 .
            *0 + *2
            .  *3 .
        """)
        assertEquals(1, bottomEdge.player1Score)
    }

    @Test
    fun dontProceedWithWalkIfEncounterABorder() {
        val field = initialize("""
            .  .  *1 .
            *0 *5 +4 *2
            .  .  *3 .
        """)
        assertEquals(1, field.player1Score)
    }
}
