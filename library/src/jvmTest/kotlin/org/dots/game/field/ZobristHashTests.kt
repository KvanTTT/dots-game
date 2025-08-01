package org.dots.game.field

import org.dots.game.core.InitialPositionType
import org.dots.game.core.Rules
import org.dots.game.core.TransformType
import org.dots.game.core.generateDefaultInitialPositions
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
        . * .
        . . .
        ""","""
        . . .
        . + .
        . . .
        """,
            isEqual = false
        )
    }

    @Test
    fun sameWithDifferentMovesOrder() {
        checkHashesOfFields(
            """
        . .  .  .  .
        . *0 +1 *2 .
        . .  .  .  .
        ""","""
        . .  .  .  .
        . *2 +1 *0 .
        . .  .  .  .
        """,
            isEqual = true
        )
    }

    @Test
    fun basePositionsAreErasured() {
        checkHashesOfFields(
            """
.   +2  +3  +4  .
+13 .   .   .   +5
+12 *1  +0  .   +6
+11 .   .   .   +7
.   +10 +9  +8  .
        ""","""
.   +0  +1  +2  .
+3  +4  +5  +6  +7
+8  +9  +10 +11 +12
+13 +14 +15 +16 +17
.   +18 +19 +20 .
        """,
            isEqual = true
        )
    }

    @Test
    fun baseWithInternalBasesPositionsAreErasured() {
        checkHashesOfFields(
            """
.   .   *13 *14 *15 *16 *17 *18 .   .
.   *12 .   .   .   .   .   .   *19 .
*11 .   .   *1  .   .   +6  .   .   *20
*10 .   *0  +4  *2  +5  *3  +7  .   *21
*31 .   .   *9  .   .   +8  .   .   *22
.   *30 .   .   .   .   .   .   *23 .
.   .   *29 *28 *27 *26 *25 *24 .   .
        ""","""
.   .   *0  *1  *2  *3  *4  *5  .   .
.   *6  *7  *8  *9  *10 *11 *12 *13 .
*14 *15 *16 *17 *18 *19 *20 *21 *22 *23
*24 *25 *26 *27 *28 *29 *30 *31 *32 *33
*34 *35 *36 *37 *38 *39 *40 *41 *42 *43
.   *44 *45 *46 *47 *48 *49 *50 *51 .
.   .   *52 *53 *54 *55 *56 *57 .   .
        """,
            isEqual = true
        )
    }

    @Test
    fun emptyBaseThatBecomesRealAndRealBase() {
        checkHashesOfFields(
            """
                .  *0 .
                *3 +4 *1
                .  *2 .
            """,
            """
                .  *1 .
                *4 +0 *2
                .  *3 .
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
                Rules(
                    width,
                    height,
                    initialMoves = InitialPositionType.Cross.generateDefaultInitialPositions(width, height)!!
                )
            }
        )

        val fieldWithManuallyPlacedCross = FieldParser.parseAndConvertWithNoInitialMoves(
"""
. . . .
. + * .
. * + .
. . . .
""",
        )

        assertEquals(fieldWithInitialCross.positionHash, fieldWithManuallyPlacedCross.positionHash)
    }

    @Test
    fun transformation() {
        val transformField = FieldParser.parseAndConvertWithNoInitialMoves(
            """
                .  *1 .  .
                *4 +0 *2 .
                .  *3 .  .
            """,
        ).transform(TransformType.RotateCw90)

        val alreadyRotatedField = FieldParser.parseAndConvertWithNoInitialMoves(
            """
                .  *4 .
                *3 +0 *1
                .  *2 .
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