package org.dots.game

import org.dots.game.core.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FieldWithBorderTests() : FieldTests() {
    override val captureByBorder = true

    @Test
    fun captureByBorder() {
        val field = initialize("""
            * + *
            . * .
            . . .
        """)

        assertEquals(1, field.player1Score)
    }

    @Test
    fun checkCorners() {
        val corner0 = initialize("""
            + * .
            * . .
            . . .
        """)
        assertEquals(1, corner0.player1Score)

        val corner1 = initialize("""
            . * +
            . . *
            . . .
        """)
        assertEquals(1, corner1.player1Score)

        val corner2 = initialize("""
            . . .
            . . *
            . * +
        """)
        assertEquals(1, corner2.player1Score)

        val corner3 = initialize("""
            . . .
            * . .
            + * .
        """)
        assertEquals(1, corner3.player1Score)
    }

    @Test
    fun captureByDotsAndBorder() {
        val field = initialize("""
            * +  * .
            . *7 . .
            * +  * .
            . *  . .
            . .  . .
        """)

        assertEquals(2, field.player1Score)
        assertNotNull(field.makeMove(4, 2, Player.Second))
        assertEquals(2, field.player1Score)
    }

    @Test
    fun captureHalfLeftField() {
        val field = initialize("""
            . . * . .
            + . * . .
            . . * . .
            . . * . .
        """)

        assertEquals(1, field.player1Score)
    }

    @Test
    fun captureHalfTopField() {
        val field = initialize("""
            . . + . .
            . . . . .
            * * * * *
            . . . . .
            . . . . .
        """)

        assertEquals(1, field.player1Score)
    }

    @Test
    fun captureDiagonalField() {
        val field = initialize("""
            * . . .
            . * . .
            . . * .
            + . . *
        """)

        assertEquals(1, field.player1Score)
    }
}