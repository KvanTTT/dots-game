package org.dots.game.field

import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.TransformType
import org.dots.game.core.transform
import org.dots.game.core.unmakeAllMovesAndCheck
import org.dots.game.dump.FieldParser
import kotlin.test.fail

abstract class FieldTests {
    open val captureByBorder: Boolean = Rules.Standard.captureByBorder
    open val baseMode: BaseMode = Rules.Standard.baseMode
    open val suicideAllowed: Boolean = Rules.Standard.suicideAllowed
    open val initialMoves: List<MoveInfo> = listOf()

    fun initRules(width: Int, height: Int): Rules =  Rules(width, height, captureByBorder, baseMode, suicideAllowed, initialMoves)

    fun testFieldWithTransformsAndRollback(fieldData: String, check: (field: Field, transformFunc: (Position) -> Position) -> Unit) {
        val originalField = FieldParser.parseAndConvert(fieldData, initializeRules = { width, height -> initRules(width, height) })

        fun check(transformType: TransformType?) {
            val field = transformType?.let { originalField.transform(transformType) } ?: originalField.clone()
            check(field) {
                if (transformType != null) {
                    it.transform(transformType, originalField.realWidth, originalField.realHeight)
                } else {
                    it
                }
            }
            field.unmakeAllMovesAndCheck { fail(it) }
        }

        check(transformType = null)
        check(transformType = TransformType.RotateCw90)
        check(transformType = TransformType.Rotate180)
        check(transformType = TransformType.RotateCw270)
        check(transformType = TransformType.FlipHorizontal)
        check(transformType = TransformType.FlipVertical)
    }

    fun testFieldWithRollback(fieldData: String, check: (Field) -> Unit) {
        with (FieldParser.parseAndConvert(fieldData, initializeRules = { width, height -> initRules(width, height) })) {
            check(this)
            unmakeAllMovesAndCheck { fail(it) }
        }
    }
}