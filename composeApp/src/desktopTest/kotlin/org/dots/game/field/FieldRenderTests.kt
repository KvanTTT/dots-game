package org.dots.game.field

import DumpParameters
import org.dots.game.core.Field
import org.dots.game.core.Position
import org.dots.game.dump.FieldParser
import render
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldRenderTests : FieldTests() {
    val sampleField = FieldParser.parseAndConvertWithNoInitialMoves("""
            .   *3  *4  *5 .  * * *
            *14 +1  .   .  *6 . . . *
            *13 .   *0  .  *7 . *2 . *
            *12 .   .   .  *8 . . . *
            .   *11 *10 *9 .  * * *
        """.trimIndent())

    @Test
    fun borders() {
        val field = Field(initRules(1, 1))
        field.makeMove(Position(1, 1))
        assertEquals("""
            ┌ ─ ┐
            │ * │
            └ ─ ┘
        """.trimIndent(),
            field.render(DumpParameters(printNumbers = false, padding = 1, printCoordinates = false, debugInfo = false))
        )
    }

    @Test
    fun maxPadding() {
        val field = Field(initRules(5, 5))
        field.makeMove(Position(3, 3))
        assertEquals(
            """
                ┌ ─ ─ ─ ─ ─ ┐
                │ . . . . . │
                │ . . . . . │
                │ . . * . . │
                │ . . . . . │
                │ . . . . . │
                └ ─ ─ ─ ─ ─ ┘
            """.trimIndent(),
            field.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = false, debugInfo = false))
        )
    }

    @Test
    fun coordinates() {
        assertEquals(
            """
            \  0  1  2  3  4  5  6  7  8  9  10
            0  ┌  ─  ─  ─  ─  ─  ─  ─  ─  ─  ┐
            1  │  .  *  *  *  .  *  *  *  .  │
            2  │  *  +  .  .  *  .  .  .  *  │
            3  │  *  .  *  .  *  .  *  .  *  │
            4  │  *  .  .  .  *  .  .  .  *  │
            5  │  .  *  *  *  .  *  *  *  .  │
            6  └  ─  ─  ─  ─  ─  ─  ─  ─  ─  ┘
        """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = 1, printCoordinates = true, debugInfo = false))
        )
    }

    @Test
    fun numbers() {
        assertEquals(
            """
            .   *4  *5  *6  .   *16 *17 *18 .
            *15 +2  .   .   *7  .   .   .   *19
            *14 .   *1  .   *8  .   *3  .   *20
            *13 .   .   .   *9  .   .   .   *21
            .   *12 *11 *10 .   *22 *23 *24 .
        """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = true, padding = 0, printCoordinates = false, debugInfo = false))
        )
    }

    @Test
    fun debugInfo() {
        assertEquals(
            """
                .  *  *  *  .  *  *  *  .
                *  *+ *^ *^ *  `* `* `* *
                *  *^ ** *^ *  `* *  `* *
                *  *^ *^ *^ *  `* `* `* *
                .  *  *  *  .  *  *  *  .
            """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = 0, printCoordinates = false, debugInfo = true))
        )
    }
}