package org.dots.game.field

import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.GameOverMoveIsProhibited
import org.dots.game.core.LegalMove
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PosOutOfBoundsIllegalMove
import org.dots.game.core.PositionXY
import kotlin.test.Test
import kotlin.test.assertIs

class StandardFieldWithEmptyStartPosTests : FieldTests() {
    override val firstMovesRestriction: Boolean = true

    @Test
    fun standardField() {
        val field = Field.create(initRules(39, 32))
        checkField(
            field = field,
            leftX = 17,
            rightX = 22,
            topY = 14,
            bottomY = 19,
        )
    }

    @Test
    fun field20x20() {
        val field = Field.create(initRules(20, 20))
        checkField(
            field = field,
            leftX = 8,
            rightX = 13,
            topY = 8,
            bottomY = 13,
        )
    }

    @Test
    fun field30x30() {
        val field = Field.create(initRules(30, 30))
        checkField(
            field = field,
            leftX = 13,
            rightX = 18,
            topY = 13,
            bottomY = 18,
        )
    }

    private fun checkField(field: Field, leftX: Int, rightX: Int, topY: Int, bottomY: Int) {
        fun checkCornerMove(left: Boolean, top: Boolean) {
            val x = if (left) leftX else rightX
            val y = if (top) topY else bottomY

            assertIs<LegalMove>(field.makeMove(x, y, Player.First))
            assertIs<LegalMove>(field.unmakeMove())

            val outX = x + if (left) -1 else +1
            val outY = y + if (top) -1 else +1

            assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(outX, y, Player.First))
            assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(x, outY, Player.First))
        }

        checkCornerMove(left = true, top = true)
        checkCornerMove(left = false, top = true)
        checkCornerMove(left = false, top = false)
        checkCornerMove(left = true, top = false)

        val (centerX, centerY) = field.width / 2 + 0.5 to field.height / 2 + 0.5
        val moveInfo1 = MoveInfo(PositionXY((centerX - 0.5).toInt(), centerY.toInt()), Player.First)
        val moveInfo2 = MoveInfo(PositionXY((centerX + 0.5).toInt(), centerY.toInt()), Player.Second)

        // Arbitrary positions are disallowed for the first two moves
        assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(2, 2, Player.First))
        assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(2, 2, Player.Second))
        assertIs<GameOverMoveIsProhibited>(field.makeMove(null, Player.First, ExternalFinishReason.Grounding))

        // Only the first player move is allowed
        assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(moveInfo2))
        assertIs<LegalMove>(field.makeMove(moveInfo1))

        // Arbitrary positions are still disallowed because a center dot of the second player is expected
        assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(2, 2, Player.First))
        assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(2, 2, Player.Second))
        assertIs<GameOverMoveIsProhibited>(field.makeMove(null, Player.Second, ExternalFinishReason.Grounding))

        // Only the second player move is allowed
        assertIs<PosOutOfBoundsIllegalMove>(field.makeMove(moveInfo1))
        assertIs<LegalMove>(field.makeMove(moveInfo2))

        // Finally, it's allowed to make move to any field position
        assertIs<LegalMove>(field.makeMove(2, 2, Player.First))
        assertIs<LegalMove>(field.makeMove(2, 3, Player.Second))
        assertIs<LegalMove>(field.makeMove(null, Player.First, ExternalFinishReason.Resign))
    }
}
