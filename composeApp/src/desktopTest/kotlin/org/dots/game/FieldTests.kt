package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.Rules
import org.dots.game.infrastructure.TestDataParser
import kotlin.test.assertNotNull

abstract class FieldTests {
    open val captureByBorder: Boolean = Rules.Standard.captureByBorder
    open val captureEmptyBase: Boolean = Rules.Standard.captureEmptyBase

    fun initialize(fieldData: String): Field {
        val testDataFiled = TestDataParser.parse(fieldData)
        val rules = Rules(testDataFiled.width, testDataFiled.height, captureByBorder, captureEmptyBase)
        val field = Field(rules)
        for ((index, testMove) in testDataFiled.moves.withIndex()) {
            val position = testMove.position
            assertNotNull(field.makeMove(position, testMove.player), "Can't make move #$index to ($position.x,$position.y)")
        }
        return field
    }
}