package org.dots.game.field

import org.dots.game.core.Player
import org.dots.game.core.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StandardFieldWithDisallowedSuicideTests : FieldTests() {
    override val suicideAllowed = false

    @Test
    fun failedSuicide() {
        testFieldWithRollback("""
            . * .
            * . *
            . * .
        """) {
            assertNull(it.makeMove(2, 2, Player.Second))
            assertNotNull(it.makeMove(2, 2, Player.First))
            assertNull(it.makeMove(2, 2, Player.First))
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
    fun tryPutDotToEmptyBaseWithinEmptyBase() {
        testFieldWithRollback(
            """
            ... ... +04 +05 +06
            ... +07 ... ... ... +08
            +09 ... ... +00 ... ... +10
            +11 ... +01 ... +02 ... +12
            +13 ... ... +03 ... ... +14
            ... +15 ... ... ... +16
            ... ... +17 +18 +19
        """
        ) {
            assertNull(it.makeMove(4, 4, Player.First))
            assertNull(it.makeMove(2, 4, Player.First))
            assertNotNull(it.makeMove(4, 4, Player.Second))
            assertNotNull(it.makeMove(2, 4, Player.Second))
        }
    }
}