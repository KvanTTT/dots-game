package org.dots.game.field

import DumpParameters
import org.dots.game.core.Field
import org.dots.game.core.InitialPositionType
import org.dots.game.core.Rules
import org.dots.game.core.TransformType
import org.dots.game.core.generateDefaultInitialPositions
import org.dots.game.dump.FieldParser
import render
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformOperations : FieldTests() {
    @Test
    fun testTransformation() {
        val originFieldData =
"""
. . . * +
. . . . *
. . . . .
. . . . +
"""
        val originField = FieldParser.parseAndConvert(originFieldData, initializeRules = { width, height ->
            Rules(width, height, captureByBorder, baseMode, suicideAllowed, initialMoves = InitialPositionType.Cross.generateDefaultInitialPositions(width, height)!!)
        })

        checkOperation(
            originField,
            """
.  .  .  *4 +5
.  .  *0 +1 *6
.  .  +3 *2 .
.  .  .  .  +7
""".trim(),
            transformType = null
        )

        checkOperation(
            originField,
            """
.  .  .  .
.  .  .  .
.  +3 *0 .
.  *2 +1 *4
+7 .  *6 +5
""".trim(),
            TransformType.RotateCw90
        )

        checkOperation(
            originField,
            """
+7 .  .  .  .
.  *2 +3 .  .
*6 +1 *0 .  .
+5 *4 .  .  .
""".trim(),
            TransformType.Rotate180
        )

        checkOperation(
            originField,
            """
+5 *6 .  +7
*4 +1 *2 .
.  *0 +3 .
.  .  .  .
.  .  .  .
""".trim(),
            TransformType.RotateCw270
        )

        checkOperation(
            originField,
            """
+5 *4 .  .  .
*6 +1 *0 .  .
.  *2 +3 .  .
+7 .  .  .  .
""".trim(),
            TransformType.FlipHorizontal
        )

        checkOperation(
            originField,
            """
.  .  .  .  +7
.  .  +3 *2 .
.  .  *0 +1 *6
.  .  .  *4 +5
""".trimIndent(),
            TransformType.FlipVertical
        )
    }

    private fun checkOperation(originField: Field, expectedData: String, transformType: TransformType?) {
        val transformedField = transformType?.let { originField.transform(it) } ?: originField
        assertEquals(expectedData, transformedField.render(DumpParameters(printCoordinates = false)))
        assertEquals(originField.initialMovesCount, transformedField.initialMovesCount)
        assertEquals(originField.player1Score, transformedField.player1Score)
        assertEquals(originField.player2Score, transformedField.player2Score)
        assertEquals(1, transformedField.player1Score)
        assertEquals(originField.gameResult, transformedField.gameResult)
        assertEquals(originField.numberOfLegalMoves, transformedField.numberOfLegalMoves)
    }
}