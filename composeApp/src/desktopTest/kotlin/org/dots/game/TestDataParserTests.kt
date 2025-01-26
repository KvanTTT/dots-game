package org.dots.game

import org.dots.game.core.Player
import org.dots.game.infrastructure.TestDataParser
import org.dots.game.infrastructure.TestMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestDataParserTests {
    @Test
    fun empty() {
        val testDataField = TestDataParser.parse("""
            . .
            . .
            . .
        """)

        assertEquals(2, testDataField.width)
        assertEquals(3, testDataField.height)
        assertEquals(0, testDataField.moves.size)
    }

    @Test
    fun simple() {
        val testDataField = TestDataParser.parse("""
            . . . .
            . * + .
            . + * .
            . . . .
        """)

        assertEquals(4, testDataField.width)
        assertEquals(4, testDataField.height)
        assertEquals(
            listOf(
                TestMove(2, 2, Player.First),
                TestMove(3, 2, Player.Second),
                TestMove(2, 3, Player.Second),
                TestMove(3, 3, Player.First),
            ),
            testDataField.moves
        )
    }

    @Test
    fun simpleWithNumbers() {
        val testDataField = TestDataParser.parse("""
            .  .  . .
            . *0 +3 .
            . +1 *2 .
            .  .  . .
        """)

        assertEquals(
            listOf(
                TestMove(2, 2, Player.First),
                TestMove(2, 3, Player.Second),
                TestMove(3, 3, Player.First),
                TestMove(3, 2, Player.Second),
            ),
            testDataField.moves
        )
    }

    @Test
    fun incorrectMarker() {
        assertFailsWith<Exception> { TestDataParser.parse("x") }
    }

    @Test
    fun incorrectMoveNumber() {
        assertFailsWith<Exception> { TestDataParser.parse("*-5") }
    }

    @Test
    fun clashingMoveNumbers() {
        val field = """
            *0 +1
            +1 *2
        """
        assertFailsWith<Exception> { TestDataParser.parse(field) }
    }

    @Test
    fun missingMoveNumbers() {
        val field = """
            *0 +1
            +3 *4
        """
        assertFailsWith<Exception> { TestDataParser.parse(field) }
    }
}