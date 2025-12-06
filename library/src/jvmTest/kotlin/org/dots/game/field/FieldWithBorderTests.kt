@file:Suppress("RETURN_VALUE_NOT_USED") // TODO: remove after switching to a newer Kotlin version (KT-82363)

package org.dots.game.field

import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FieldWithBorderTests() : FieldTests() {
    override val captureByBorder: Boolean = true

    @Test
    fun captureByBorder() {
        testFieldWithRollback("""
            * + *
            . * .
            . . .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkTopLeftCorner() {
        testFieldWithRollback("""
            + * .
            * . .
            . . .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkTopRightCorner() {
        testFieldWithRollback("""
            . * +
            . . *
            . . .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkBottomRightCorner() {
        testFieldWithRollback("""
            . . .
            . . *
            . * +
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkBottomLeftCorner() {
        testFieldWithRollback("""
            . . .
            * . .
            + * .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun captureByDotsAndBorder() {
        testFieldWithRollback("""
            * +  * .
            . *7 . .
            * +  * .
            . *  . .
            . .  . .
        """) {
            assertEquals(2, it.player1Score)
            assertIs<LegalMove>(it.makeMove(4, 2, Player.Second))
            assertEquals(2, it.player1Score)
        }
    }

    @Test
    fun captureHalfLeftField() {
        testFieldWithRollback("""
            . . * . .
            + . * . .
            . . * . .
            . . * . .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun captureHalfTopField() {
        testFieldWithRollback("""
            . . + . .
            . . . . .
            * * * * *
            . . . . .
            . . . . .
        """) {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun captureDiagonalField() {
        testFieldWithRollback("""
            * . . .
            . * . .
            . . * .
            + . . *
        """) {
            assertEquals(1, it.player1Score)
        }
    }
}