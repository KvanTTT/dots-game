@file:Suppress("RETURN_VALUE_NOT_USED") // TODO: remove after switching to a newer Kotlin version (KT-82363)

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
            . * .
            * . *
            . * .
        """) {
            assertIs<SuicidalIllegalMove>(it.makeMove(2, 2, Player.Second))
            assertIs<LegalMove>(it.makeMove(2, 2, Player.First))
            assertIs<PosIsOccupiedIllegalMove>(it.makeMove(2, 2, Player.First))
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
            assertIs<SuicidalIllegalMove>(it.makeMove(4, 4, Player.First))
            assertIs<SuicidalIllegalMove>(it.makeMove(2, 4, Player.First))
            assertIs<LegalMove>(it.makeMove(4, 4, Player.Second))
            assertIs<LegalMove>(it.makeMove(2, 4, Player.Second))
        }
    }
}