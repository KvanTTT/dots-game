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
+
"""
            ),
            Arguments.of(
                1,
"""
. * .
* * *
. * .
"""
            ),
            Arguments.of(
                2,
"""
+ . +
. + .
+ . +
"""
            ),
            Arguments.of(
                3,
"""
. . * . .
. . . . .
* . * . *
. . . . .
. . * . .
"""
            ),
            Arguments.of(
                4,
"""
. + . + .
+ . . . +
. . + . .
+ . . . +
. + . + .
"""
            ),
            Arguments.of(5,
"""
* . . . *
. . . . .
. . * . .
. . . . .
* . . . *
"""
            ),
            Arguments.of(6,
"""
. . . + . . .
. . . . . . .
. . . . . . .
+ . . + . . +
. . . . . . .
. . . . . . .
. . . + . . .
"""
            ),
            Arguments.of(7,
"""
. . * . * . .
. . . . . . .
* . . . . . *
. . . * . . .
* . . . . . *
. . . . . . .
. . * . * . .
"""
            ),
        )
    }

    @Test
    fun simple() {
        val fieldData =
"""
. * + .
. * + .
. + * .
. + * .
"""
        checkDistance(
            1,
            fieldData,
            expectedDistanceData = """
. * + .
. * + .
. + * .
. + * .
"""
        )
        checkDistance(
            2,
            fieldData = fieldData,
            expectedDistanceData = """
. . . .
. * + .
. + * .
. . . .
"""
        )
    }

    @Test
    fun base() {
        val fieldData = """
+ * * + + . .
* + . * . . .
. * * . * . *
"""
        checkDistance(
            1,
            fieldData = fieldData,
            expectedDistanceData = """
. * * + + . .
* * * * . . .
. * * . . . .
""",
        )
        checkDistance(
            2,
            fieldData = fieldData,
            expectedDistanceData = """
. * * . . . .
* * * * . . .
. * * . * . .
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