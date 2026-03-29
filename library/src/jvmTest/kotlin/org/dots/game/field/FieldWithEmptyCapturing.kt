package org.dots.game.field

import org.dots.game.core.BaseMode
import org.dots.game.core.PosIsOccupiedIllegalMove
import org.dots.game.core.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FieldWithEmptyCapturing() : FieldTests() {
    override val baseMode: BaseMode = BaseMode.AnySurrounding

    @Test
    fun checkCapturing() {
        testFieldWithRollback("""
            . * .
            * . *
            . * .
        """) {
            assertEquals(0, it.player1Score)
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 2, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 2, Player.Second))
        }
    }
}