package org.dots.game

import org.dots.game.core.LegalMove
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.dump.FieldParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class FieldParserTests {
    @Test
    fun empty() {
        val parsedField = FieldParser.parseAndConvertWithNoInitialMoves("""
            . .
            . .
            . .
        """)

        assertEquals(2, parsedField.width)
        assertEquals(3, parsedField.height)
        assertTrue(parsedField.moveSequence.isEmpty())
    }

    @Test
    fun simple() {
        val parsedField = FieldParser.parseAndConvertWithNoInitialMoves("""
            . . . .
            . * + .
            . + * .
            . . . .
        """)

        assertEquals(4, parsedField.width)
        assertEquals(4, parsedField.height)
        val moveSequence = parsedField.moveSequence
        moveSequence[0].checkPositionAndPlayer(2, 2, Player.First, parsedField.realWidth)
        moveSequence[1].checkPositionAndPlayer(3, 2, Player.Second, parsedField.realWidth)
        moveSequence[2].checkPositionAndPlayer(2, 3, Player.Second, parsedField.realWidth)
        moveSequence[3].checkPositionAndPlayer(3, 3, Player.First, parsedField.realWidth)
    }

    @Test
    fun simpleWithNumbers() {
        val parsedField = FieldParser.parseAndConvertWithNoInitialMoves("""
            .  .  . .
            . *0 +3 .
            . +1 *2 .
            .  .  . .
        """)

        val moveSequence = parsedField.moveSequence
        moveSequence[0].checkPositionAndPlayer(2, 2, Player.First, parsedField.realWidth)
        moveSequence[1].checkPositionAndPlayer(2, 3, Player.Second, parsedField.realWidth)
        moveSequence[2].checkPositionAndPlayer(3, 3, Player.First, parsedField.realWidth)
        moveSequence[3].checkPositionAndPlayer(3, 2, Player.Second, parsedField.realWidth)
    }

    @Test
    fun moveNumbersStartWithOne() {
        val parsedField = FieldParser.parseAndConvertWithNoInitialMoves("""
            .  .  . .
            . *1 +4 .
            . +2 *3 .
            .  .  . .
        """)

        val moveSequence = parsedField.moveSequence
        moveSequence[0].checkPositionAndPlayer(2, 2, Player.First, parsedField.realWidth)
        moveSequence[1].checkPositionAndPlayer(2, 3, Player.Second, parsedField.realWidth)
        moveSequence[2].checkPositionAndPlayer(3, 3, Player.First, parsedField.realWidth)
        moveSequence[3].checkPositionAndPlayer(3, 2, Player.Second, parsedField.realWidth)
    }

    @Test
    fun mixedNumberedAndUnnumberedMoves() {
        val parsedField = FieldParser.parseAndConvertWithNoInitialMoves("""
            . *0 .
            * +2 *
            . * .
        """)

        val moveSequence = parsedField.moveSequence
        moveSequence[0].checkPositionAndPlayer(2, 1, Player.First, parsedField.realWidth)
        moveSequence[1].checkPositionAndPlayer(1, 2, Player.First, parsedField.realWidth)
        moveSequence[2].checkPositionAndPlayer(2, 2, Player.Second, parsedField.realWidth)
        moveSequence[3].checkPositionAndPlayer(3, 2, Player.First, parsedField.realWidth)
        moveSequence[4].checkPositionAndPlayer(2, 3, Player.First, parsedField.realWidth)
    }

    @Test
    fun lastNumbered() {
        val parsedField = FieldParser.parseAndConvertWithNoInitialMoves("""
            * +
            + *3
        """)
        val moveSequence = parsedField.moveSequence
        assertEquals(4, moveSequence.size)
        moveSequence[3].checkPositionAndPlayer(2, 2, Player.First, parsedField.realWidth)
    }

    @Test
    fun incorrectMarker() {
        assertEquals(
            "Error at [0..1): The marker should be either `*` (first player), `+` (second player) or `.`.",
        assertFails { FieldParser.parseAndConvertWithNoInitialMoves("x") }.message
        )
    }

    @Test
    fun incorrectMoveNumber() {
        assertEquals(
            "Error at [1..13): Incorrect cell move's number.",
        assertFails { FieldParser.parseAndConvertWithNoInitialMoves("*999999999999") }.message
        )
    }

    @Test
    fun clashingMoveNumbers() {
        val field = """
            *0 +1
            +1 *2
        """
        assertEquals(
            "Error at [32..33): The move with number 1 is already in use.",
            assertFails { FieldParser.parseAndConvertWithNoInitialMoves(field) }.message
        )
    }

    @Test
    fun missingMoveNumbers() {
        val field = """
            *0 +1
            +4 *5
        """
        assertEquals(
            "Error: The following moves are missing: 2..3",
            assertFails { FieldParser.parseAndConvertWithNoInitialMoves(field) }.message
        )
    }

    private fun LegalMove.checkPositionAndPlayer(x: Int, y: Int, expectedPlayer: Player, fieldStride: Int) {
        assertEquals(PositionXY(x, y), position.toXY(fieldStride))
        assertEquals(expectedPlayer, player)
    }
}