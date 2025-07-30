package org.dots.game.field

import DumpParameters
import org.dots.game.dump.FieldParser
import render
import kotlin.Int
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldRenderTests : FieldTests() {
    val sampleField = FieldParser.parseAndConvertWithNoInitialMoves("""
            . .   .   .   .  .  . .  . . .
            . .   *3  *4  *5 .  * *  * . .
            . *14 +1  .   .  *6 . .  . * .
            . *13 .   *0  .  *7 . *2 . * .
            . *12 .   .   .  *8 . .  . * .
            . .   *11 *10 *9 .  * *  * . .
            . .   .   .   .  .  . .  . . .
        """.trimIndent())

    @Test
    fun maxPadding() {
        assertEquals(
            """
            . . . . . . . . . . .
            . . * * * . * * * . .
            . * + . . * . . . * .
            . * . * . * . * . * .
            . * . . . * . . . * .
            . . * * * . * * * . .
            . . . . . . . . . . .
        """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = false, printBorders = false, debugInfo = false))
        )
    }

    @Test
    fun borders() {
        assertEquals(
            """
            ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ 
            │ . . . . . . . . . . . 
            │ . . * * * . * * * . . 
            │ . * + . . * . . . * . 
            │ . * . * . * . * . * . 
            │ . * . . . * . . . * . 
            │ . . * * * . * * * . . 
            │ . . . . . . . . . . . 
            └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
        """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = false, printBorders = true, debugInfo = false))
        )
    }

    @Test
    fun coordinatesWithBorders() {
        assertEquals(
            """
            \  0  1  2  3  4  5  6  7  8  9  10 11 12
            0  ┌  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ┐  
            1  │  .  .  .  .  .  .  .  .  .  .  .  
            2  │  .  .  *  *  *  .  *  *  *  .  .  
            3  │  .  *  +  .  .  *  .  .  .  *  .  
            4  │  .  *  .  *  .  *  .  *  .  *  .  
            5  │  .  *  .  .  .  *  .  .  .  *  .  
            6  │  .  .  *  *  *  .  *  *  *  .  .  
            7  │  .  .  .  .  .  .  .  .  .  .  .  
            8  └  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ┘
        """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = true, printBorders = true, debugInfo = false))
        )
    }

    @Test
    fun coordinatesOnly() {
        assertEquals(
            """
            \  1  2  3  4  5  6  7  8  9  10 11
            1  .  .  .  .  .  .  .  .  .  .  .
            2  .  .  *  *  *  .  *  *  *  .  .
            3  .  *  +  .  .  *  .  .  .  *  .
            4  .  *  .  *  .  *  .  *  .  *  .
            5  .  *  .  .  .  *  .  .  .  *  .
            6  .  .  *  *  *  .  *  *  *  .  .
            7  .  .  .  .  .  .  .  .  .  .  .
        """.trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = true, printBorders = false, debugInfo = false))
        )
    }

    @Test
    fun numbers() {
        assertEquals(
            """
            .   *3  *4  *5  .   *15 *16 *17 .
            *14 +1  .   .   *6  .   .   .   *18
            *13 .   *0  .   *7  .   *2  .   *19
            *12 .   .   .   *8  .   .   .   *20
            .   *11 *10 *9  .   *21 *22 *23 .
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