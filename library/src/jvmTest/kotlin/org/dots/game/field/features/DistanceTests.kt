package org.dots.game.field.features

import org.dots.game.core.features.getPositionsAtDistance
import org.dots.game.core.features.squareDistances
import org.dots.game.dump.FieldParser
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test

class DistanceTests {
    companion object {
        @JvmStatic
        fun provideTestData(): Stream<Arguments> = Stream.of(
            Arguments.of(
                0,
"""
o
"""
            ),
            Arguments.of(
                1,
"""
. x .
x x x
. x .
"""
            ),
            Arguments.of(
                2,
"""
o . o
. o .
o . o
"""
            ),
            Arguments.of(
                3,
"""
. . x . .
. . . . .
x . x . x
. . . . .
. . x . .
"""
            ),
            Arguments.of(
                4,
"""
. o . o .
o . . . o
. . o . .
o . . . o
. o . o .
"""
            ),
            Arguments.of(5,
"""
x . . . x
. . . . .
. . x . .
. . . . .
x . . . x
"""
            ),
            Arguments.of(6,
"""
. . . o . . .
. . . . . . .
. . . . . . .
o . . o . . o
. . . . . . .
. . . . . . .
. . . o . . .
"""
            ),
            Arguments.of(7,
"""
. . x . x . .
. . . . . . .
x . . . . . x
. . . x . . .
x . . . . . x
. . . . . . .
. . x . x . .
"""
            ),
        )
    }

    @Test
    fun simple() {
        val fieldData =
"""
. x o .
. x o .
. o x .
. o x .
"""
        checkDistance(
            1,
            fieldData,
            expectedDistanceData = """
. x o .
. x o .
. o x .
. o x .
"""
        )
        checkDistance(
            2,
            fieldData = fieldData,
            expectedDistanceData = """
. . . .
. x o .
. o x .
. . . .
"""
        )
    }

    @Test
    fun base() {
        val fieldData = """
o x x o o . .
x o . x . . .
. x x . x . x
"""
        checkDistance(
            1,
            fieldData = fieldData,
            expectedDistanceData = """
. x x o o . .
x x x x . . .
. x x . . . .
""",
        )
        checkDistance(
            2,
            fieldData = fieldData,
            expectedDistanceData = """
. x x . . . .
x x x x . . .
. x x . x . .
""",
        )
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    fun testDistance(distanceId: Int, fieldData: String) {
        println("Squared distance: ${squareDistances[distanceId]}")
        checkDistance(distanceId, fieldData, fieldData)
    }

    private fun checkDistance(
        distance: Int,
        fieldData: String,
        expectedDistanceData: String?,
    ) {
        val field = FieldParser.parseAndConvertWithNoInitialMoves(fieldData)
        val distantPositions = field.getPositionsAtDistance(distance)

        with(field) {
            checkFeatures(
                field,
                expectedDistanceData,
                distantPositions.associateWith { it.getState().getActivePlayer() },
                "Different $distance square distance positions"
            )?.let {
                assertAll(it)
            }
        }
    }
}