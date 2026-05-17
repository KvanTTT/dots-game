package org.dots.game.field.features

import org.dots.game.core.features.getOneMoveCapturingAndBasePositions
import org.dots.game.dump.FieldParser
import org.junit.jupiter.api.assertAll

import kotlin.test.Test

class FieldOneMovePositionsTests {
    @Test
    fun twoCapturing() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  x  .  .  .  o  .
x  o  x  .  o  x  o
.  .  .  .  .  .  .
""",
            expectedCapturingPositionsData = """
.  .  .  .  .  .  .
.  .  .  .  .  .  .
.  x  .  .  .  o  .
""",
            expectedSurroundingPositionsData = """
.  .  .  .  .  .  .
.  x  .  .  .  o  .
.  .  .  .  .  .  .
""",
        )
    }

    @Test
    fun twoBases() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  x  .  .  .  o  .
x  .  x  .  o  .  o
.  .  .  .  .  .  .
""",
            expectedCapturingPositionsData = null,
            expectedSurroundingPositionsData = """
.  .  .  .  .  .  .
.  x  .  .  .  o  .
.  .  .  .  .  .  .
"""
        )
    }

    @Test
    fun emptyBasePosition() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  x  .
x  .  x
.  x  .
""",
            expectedCapturingPositionsData = null,
            expectedSurroundingPositionsData = """
.  .  .
.  x  .
.  .  .
"""
        )
    }

    @Test
    fun emptyBasePosition2() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  o  .
o  .  o
.  o  .
""",
            expectedCapturingPositionsData = null,
            expectedSurroundingPositionsData = """
.  .  .
.  o  .
.  .  .
"""
        )
    }

    @Test
    fun emptyBaseWithCapturing() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  x  .
x  o  x
o  .  o
.  o  .
""",
            expectedCapturingPositionsData = """
.  .  .
.  .  .
.  x  .
.  .  .
""",
            expectedSurroundingPositionsData = """
.  .  .
.  x  .
.  o  .
.  .  .
""",
        )
    }

    @Test
    fun twoCapturingOnTheSamePosition() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  x  .
x  o  x
.  .  .
o  x  o
.  o  .
""",
            expectedCapturingPositionsData = """
.   .   .
.   .   .
.   xo  .
.   .   .
.   .   .
""",
            expectedSurroundingPositionsData = """
.  .  .
.  x  .
.  .  .
.  o  .
.  .  .
""",
        )
    }

    @Test
    fun twoBasesOnTheSamePosition() {
        checkOneMoveCapturingAndSurroundPositions(
            fieldData = """
.  x  x  x  .
x  .  o  .  x
x  o  .  o  x
x  .  .  .  x
.  x  .  x  .
""",
            expectedCapturingPositionsData = """
.  .  .  .  .
.  .  .  .  .
.  .  .  .  .
.  .  x  .  .
.  .  x  .  .
""",
            expectedSurroundingPositionsData = """
.   .   .   .   .
.   x   x   x   .
.   x   xo  x   .
.   x   x   x   .
.   .   .   .   .
""".trimIndent(),
        )
    }

    @Test
    fun complexExampleWithMultipleStates() {
        checkOneMoveCapturingAndSurroundPositions(fieldData = """
.  o  o  x  x  .
o  .  x  o  .  x
o  x  .  o  x  .
o  x  .  o  x  .
.  o  .  x  .  .
""",
            expectedCapturingPositionsData = """
.  .  .  .  .  .
.  .  .  .  .  .
.  .  .  .  .  .
.  .  xo .  .  .
.  .  xo .  .  .
""",
            expectedSurroundingPositionsData = """
.  .  .  .  .  .
.  o  o  x  x  .
.  o  xo x  .  .
.  o  xo x  .  .
.  .  .  .  .  .
""")
    }

    private fun checkOneMoveCapturingAndSurroundPositions(
        fieldData: String,
        expectedCapturingPositionsData: String? = null,
        expectedSurroundingPositionsData: String? = null,
    ) {
        val field = FieldParser.parseAndConvertWithNoInitialMoves(fieldData)
        val (capturingPositions, basePositions) = field.getOneMoveCapturingAndBasePositions()

        val capturingFailure = checkFeatures(
            field,
            expectedCapturingPositionsData,
            capturingPositions,
            "Different capturing positions"
        )
        val surroundingFailure = checkFeatures(
            field,
            expectedSurroundingPositionsData,
            basePositions,
            "Different surrounding positions"
        )

        assertAll(listOfNotNull(capturingFailure, surroundingFailure))
    }
}