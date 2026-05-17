package org.dots.game.field

import org.dots.game.core.InitPosType
import org.dots.game.core.TransformType
import org.dots.game.createStandardRules
import org.dots.game.dump.FieldParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ZobristHashTests {
    @Test
    fun emptyEquivalentFields() {
        checkHashesOfFields(
            """
        . . .
        . . .
        . . .
        ""","""
        . . .
        . . .
        . . .
        """,
            isEqual = true
        )
    }

    @Test
    fun differentFieldSizes() {
        checkHashesOfFields(
            """
        . . .
        . . .
        . . .
        ""","""
        . . . .
        . . . .
        . . . .
        """,
            isEqual = false
        )
    }

    @Test
    fun differentMoves() {
        checkHashesOfFields(
            """
. . .
. x .
. . .
""","""
. . .
. o .
. . .
""",
            isEqual = false
        )
    }

    @Test
    fun sameWithDifferentMovesOrder() {
        checkHashesOfFields(
            """
.  .  .  .  .
.  x0 o1 x2 .
.  .  .  .  .
""","""
.  .  .  .  .
.  x2 o1 x0 .
.  .  .  .  .
""",
            isEqual = true
        )
    }

    @Test
    fun basePositionsAreErasured() {
        checkHashesOfFields(
            """
.   o2  o3  o4  .
o13 .   .   .   o5
o12 x1  o0  .   o6
o11 .   .   .   o7
.   o10 o9  o8  .
""","""
.   o0  o1  o2  .
o3  o4  o5  o6  o7
o8  o9  o10 o11 o12
o13 o14 o15 o16 o17
.   o18 o19 o20 .
""",
            isEqual = true
        )
    }

    @Test
    fun baseWithInternalBasesPositionsAreErasured() {
        checkHashesOfFields(
            """
.   .   x13 x14 x15 x16 x17 x18 .   .
.   x12 .   .   .   .   .   .   x19 .
x11 .   .   x1  .   .   o6  .   .   x20
x10 .   x0  o4  x2  o5  x3  o7  .   x21
x31 .   .   x9  .   .   o8  .   .   x22
.   x30 .   .   .   .   .   .   x23 .
.   .   x29 x28 x27 x26 x25 x24 .   .
""","""
.   .   x0  x1  x2  x3  x4  x5  .   .
.   x6  x7  x8  x9  x10 x11 x12 x13 .
x14 x15 x16 x17 x18 x19 x20 x21 x22 x23
x24 x25 x26 x27 x28 x29 x30 x31 x32 x33
x34 x35 x36 x37 x38 x39 x40 x41 x42 x43
.   x44 x45 x46 x47 x48 x49 x50 x51 .
.   .   x52 x53 x54 x55 x56 x57 .   .
""",
            isEqual = true
        )
    }

    @Test
    fun emptyBaseThatBecomesRealAndRealBase() {
        checkHashesOfFields(
            """
.  x0 .
x3 o4 x1
.  x2 .
""",
            """
.  x1 .
x4 o0 x2
.  x3 .
""",
            isEqual = true,
        )
    }

    @Test
    fun initialAndManuallyPlacedCross() {
        val fieldWithInitialCross = FieldParser.parseAndConvert(
            """
. . . .
. . . .
. . . .
. . . .
""",
            initializeRules = { width, height ->
                createStandardRules(width, height, initPosType = InitPosType.Cross)
            }
        )

        val fieldWithManuallyPlacedCross = FieldParser.parseAndConvertWithNoInitialMoves(
"""
. . . .
. x o .
. o x .
. . . .
""",
        )

        assertEquals(fieldWithInitialCross.positionHash, fieldWithManuallyPlacedCross.positionHash)
    }

    @Test
    fun transformation() {
        val transformField = FieldParser.parseAndConvertWithNoInitialMoves(
            """
.  x1 .  .
x4 o0 x2 .
.  x3 .  .
""",
        ).transform(TransformType.RotateCw90)

        val alreadyRotatedField = FieldParser.parseAndConvertWithNoInitialMoves(
            """
.  x4 .
x3 o0 x1
.  x2 .
.  .  .
""",
        )

        assertEquals(alreadyRotatedField.positionHash, transformField.positionHash)
    }

    private fun checkHashesOfFields(fieldData1: String, fieldData2: String, isEqual: Boolean) {
        val field1 = FieldParser.parseAndConvertWithNoInitialMoves(fieldData1)
        val field2 = FieldParser.parseAndConvertWithNoInitialMoves(fieldData2)
        if (isEqual) {
            assertEquals(field1.positionHash, field2.positionHash)
        } else {
            assertNotEquals(field1.positionHash, field2.positionHash)
        }
    }
}