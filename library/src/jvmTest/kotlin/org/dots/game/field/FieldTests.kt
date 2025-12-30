package org.dots.game.field

import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.InitPosGenType
import org.dots.game.core.InitPosType
import org.dots.game.core.PositionXY
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

    fun initRules(width: Int, height: Int): Rules = Rules.create(
        width,
        height,
        captureByBorder,
        baseMode,
        suicideAllowed,
        InitPosType.Empty,
        random = Rules.Standard.random,
        initPosGenType = InitPosGenType.Static,
        komi = Rules.Standard.komi
    )

    fun testFieldWithTransformsAndRollback(fieldData: String, check: (field: Field, transformFunc: (x: Int, y: Int) -> PositionXY) -> Unit) {
        val originalField = FieldParser.parseAndConvert(fieldData, initializeRules = { width, height -> initRules(width, height) })

        fun check(transformType: TransformType?) {
            val field = transformType?.let { originalField.transform(transformType) } ?: originalField.clone()
            check(field) { x, y ->
                val positionXY = PositionXY(x, y)
                if (transformType != null) {
                    positionXY.transform(transformType, originalField.width, originalField.height)
                } else {
                    positionXY
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