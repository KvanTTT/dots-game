package org.dots.game.field

import org.dots.game.core.BaseMode
import org.dots.game.core.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
            assertNull(it.makeMove(2, 2, Player.First))
            assertNull(it.makeMove(2, 2, Player.Second))
        }
    }
}