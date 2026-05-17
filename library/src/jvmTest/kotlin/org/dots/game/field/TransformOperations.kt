package org.dots.game.field

import org.dots.game.dump.DumpParameters
import org.dots.game.core.Field
import org.dots.game.core.InitPosType
import org.dots.game.core.Rules
import org.dots.game.core.TransformType
import org.dots.game.dump.DumpFormat
import org.dots.game.dump.FieldParser
import org.dots.game.dump.render
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformOperations : FieldTests() {
    @Test
    fun testTransformation() {
        val originFieldData =
"""
. . . x o
. . . . x
. . . . .
. . . . o
"""
        val originField = FieldParser.parseAndConvert(originFieldData, initializeRules = { width, height ->
            Rules.create(
                width,
                height,
                captureByBorder,
                baseMode,
                suicideAllowed,
                InitPosType.Cross,
                random = Rules.Standard.random,
                initPosGenType = Rules.Standard.initPosGenType,
                komi = Rules.Standard.komi,
            )
        })

        checkOperation(
            originField,
            """
.  .  .  x4 o5
.  .  x0 o1 x6
.  .  o3 x2 .
.  .  .  .  o7
""".trim(),
            transformType = null
        )

        checkOperation(
            originField,
            """
.  .  .  .
.  .  .  .
.  o3 x0 .
.  x2 o1 x4
o7 .  x6 o5
""".trim(),
            TransformType.RotateCw90
        )

        checkOperation(
            originField,
            """
o7 .  .  .  .
.  x2 o3 .  .
x6 o1 x0 .  .
o5 x4 .  .  .
""".trim(),
            TransformType.Rotate180
        )

        checkOperation(
            originField,
            """
o5 x6 .  o7
x4 o1 x2 .
.  x0 o3 .
.  .  .  .
.  .  .  .
""".trim(),
            TransformType.RotateCw270
        )

        checkOperation(
            originField,
            """
o5 x4 .  .  .
x6 o1 x0 .  .
.  x2 o3 .  .
o7 .  .  .  .
""".trim(),
            TransformType.FlipHorizontal
        )

        checkOperation(
            originField,
            """
.  .  .  .  o7
.  .  o3 x2 .
.  .  x0 o1 x6
.  .  .  x4 o5
""".trimIndent(),
            TransformType.FlipVertical
        )
    }

    private fun checkOperation(originField: Field, expectedData: String, transformType: TransformType?) {
        val transformedField = transformType?.let { originField.transform(it) } ?: originField
        assertEquals(expectedData, transformedField.render(DumpParameters(printCoordinates = false, format = DumpFormat.Plain)))
        assertEquals(originField.initialMovesCount, transformedField.initialMovesCount)
        assertEquals(originField.player1Score, transformedField.player1Score)
        assertEquals(originField.player2Score, transformedField.player2Score)
        assertEquals(1, transformedField.player1Score)
        assertEquals(originField.gameResult, transformedField.gameResult)
        assertEquals(originField.numberOfLegalMovesIfSuicideAllowed, transformedField.numberOfLegalMovesIfSuicideAllowed)
    }
}