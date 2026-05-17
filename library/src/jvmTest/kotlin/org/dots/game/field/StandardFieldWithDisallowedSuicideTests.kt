package org.dots.game.field

import org.dots.game.core.PosIsOccupiedIllegalMove
import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import org.dots.game.core.SuicidalIllegalMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StandardFieldWithDisallowedSuicideTests : FieldTests() {
    override val suicideAllowed: Boolean = false

    @Test
    fun failedSuicide() {
        testFieldWithRollback("""
. x .
x . x
. x .
""") {
            assertIs<SuicidalIllegalMove>(it.makeMove(2, 2, Player.Second))
            assertIs<LegalMove>(it.makeMove(2, 2, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 2, Player.First))
        }
    }

    @Test
    fun player1CapturesByPlacingInsideEmptyBase() {
        testFieldWithRollback("""
.  x  o  .
x  o6 x7 o
.  x  o  .
""") {
            assertEquals(1, it.player1Score)
            assertEquals(0, it.player2Score)
        }
    }

    @Test
    fun tryPutDotToEmptyBaseWithinEmptyBase() {
        testFieldWithRollback(
            """
.   .   o04 o05 o06
.   o07 .   .   .   o08
o09 .   .   o00 .   .   o10
o11 .   o01 .   o02 .   o12
o13 .   .   o03 .   .   o14
.   o15 .   .   .   o16
.   .   o17 o18 o19
"""
        ) {
            assertIs<SuicidalIllegalMove>(it.makeMove(4, 4, Player.First))
            assertIs<SuicidalIllegalMove>(it.makeMove(2, 4, Player.First))
            assertIs<LegalMove>(it.makeMove(4, 4, Player.Second))
            assertIs<LegalMove>(it.makeMove(2, 4, Player.Second))
        }
    }
}