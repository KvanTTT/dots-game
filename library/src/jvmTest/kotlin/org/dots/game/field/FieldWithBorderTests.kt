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
x o x
. x .
. . .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkTopLeftCorner() {
        testFieldWithRollback("""
o x .
x . .
. . .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkTopRightCorner() {
        testFieldWithRollback("""
. x o
. . x
. . .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkBottomRightCorner() {
        testFieldWithRollback("""
. . .
. . x
. x o
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun checkBottomLeftCorner() {
        testFieldWithRollback("""
. . .
x . .
o x .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun captureByDotsAndBorder() {
        testFieldWithRollback("""
x  o  x  .
.  x7  .  .
x  o  x  .
.  x  .  .
.  .  .  .
""") {
            assertEquals(2, it.player1Score)
            assertIs<LegalMove>(it.makeMove(4, 2, Player.Second))
            assertEquals(2, it.player1Score)
        }
    }

    @Test
    fun captureHalfLeftField() {
        testFieldWithRollback("""
. . x . .
o . x . .
. . x . .
. . x . .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun captureHalfTopField() {
        testFieldWithRollback("""
. . o . .
. . . . .
x x x x x
. . . . .
. . . . .
""") {
            assertEquals(1, it.player1Score)
        }
    }

    @Test
    fun captureDiagonalField() {
        testFieldWithRollback("""
x . . .
. x . .
. . x .
o . . x
""") {
            assertEquals(1, it.player1Score)
        }
    }
}