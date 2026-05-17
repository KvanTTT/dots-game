package org.dots.game.field

import org.dots.game.core.Field
import org.dots.game.dump.DumpFormat
import org.dots.game.dump.DumpParameters
import org.dots.game.dump.FieldParser
import org.dots.game.dump.render
import kotlin.Int
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldRenderTests : FieldTests() {
    val sampleField: Field = FieldParser.parseAndConvertWithNoInitialMoves("""
.   .   .   .   .   .   .   .   .   .   .
.   .   x3  x4  x5  .   x   x   x   .   .
.   x14 o1  .   .   x6  .   .   .   x   .
.   x13 .   x0  .   x7  .   x2  .   x   .
.   x12 .   .   .   x8  .   .   .   x   .
.   .   x11 x10 x9  .   x   x   x   .   .
.   .   .   .   .   .   .   .   .   .   .
""".trimIndent())

    @Test
    fun maxPadding() {
        assertEquals(
            """
. . . . . . . . . . .
. . x x x . x x x . .
. x o . . x . . . x .
. x . x . x . x . x .
. x . . . x . . . x .
. . x x x . x x x . .
. . . . . . . . . . .
""".trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = false, printBorders = false, debugInfo = false, format = DumpFormat.Plain))
        )
    }

    @Test
    fun borders() {
        assertEquals(
            """
┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ 
│ . . . . . . . . . . . 
│ . . x x x . x x x . . 
│ . x o . . x . . . x . 
│ . x . x . x . x . x . 
│ . x . . . x . . . x . 
│ . . x x x . x x x . . 
│ . . . . . . . . . . . 
└ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
""".trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = false, printBorders = true, debugInfo = false, format = DumpFormat.Plain))
        )
    }

    @Test
    fun coordinatesWithBorders() {
        assertEquals(
            """
\  0  1  2  3  4  5  6  7  8  9  10 11 12
0  ┌  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ┐  
1  │  .  .  .  .  .  .  .  .  .  .  .  
2  │  .  .  x  x  x  .  x  x  x  .  .  
3  │  .  x  o  .  .  x  .  .  .  x  .  
4  │  .  x  .  x  .  x  .  x  .  x  .  
5  │  .  x  .  .  .  x  .  .  .  x  .  
6  │  .  .  x  x  x  .  x  x  x  .  .  
7  │  .  .  .  .  .  .  .  .  .  .  .  
8  └  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ─  ┘
""".trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = true, printBorders = true, debugInfo = false, format = DumpFormat.Plain))
        )
    }

    @Test
    fun coordinatesOnly() {
        assertEquals(
            """
\  1  2  3  4  5  6  7  8  9  10 11
1  .  .  .  .  .  .  .  .  .  .  .
2  .  .  x  x  x  .  x  x  x  .  .
3  .  x  o  .  .  x  .  .  .  x  .
4  .  x  .  x  .  x  .  x  .  x  .
5  .  x  .  .  .  x  .  .  .  x  .
6  .  .  x  x  x  .  x  x  x  .  .
7  .  .  .  .  .  .  .  .  .  .  .
""".trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = Int.MAX_VALUE, printCoordinates = true, printBorders = false, debugInfo = false, format = DumpFormat.Plain))
        )
    }

    @Test
    fun numbers() {
        assertEquals(
            """
.   x3  x4  x5  .   x15 x16 x17 .
x14 o1  .   .   x6  .   .   .   x18
x13 .   x0  .   x7  .   x2  .   x19
x12 .   .   .   x8  .   .   .   x20
.   x11 x10 x9  .   x21 x22 x23 .
""".trimIndent(),
            sampleField.render(DumpParameters(printNumbers = true, padding = 0, printCoordinates = false, debugInfo = false, format = DumpFormat.Plain))
        )
    }

    @Test
    fun debugInfo() {
        assertEquals(
            """
.  x  x  x  .  x  x  x  .
x  xo x^ x^ x  `x `x `x x
x  x^ x  x^ x  `x x  `x x
x  x^ x^ x^ x  `x `x `x x
.  x  x  x  .  x  x  x  .
""".trimIndent(),
            sampleField.render(DumpParameters(printNumbers = false, padding = 0, printCoordinates = false, debugInfo = true, format = DumpFormat.Plain))
        )
    }
}