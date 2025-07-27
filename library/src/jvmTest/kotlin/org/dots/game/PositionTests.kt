package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.Position
import org.dots.game.core.clockwiseBigJumpWalk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PositionTests {
    @Test
    fun testClockwiseBigJumpWalk() {
        val x = 2
        val y = 2
        val fieldStride = Field.getStride(5)
        val position = Position(2, 2, fieldStride)
        var counter = 0

        val positionsToCheck = listOf(
            Position(x - 1, y - 1, fieldStride),
            Position(x, y - 1, fieldStride),
            Position(x + 1, y - 1, fieldStride),
            Position(x + 1, y, fieldStride),
            Position(x + 1, y + 1, fieldStride),
            Position(x, y + 1, fieldStride),
            Position(x - 1, y + 1, fieldStride),
            Position(x - 1, y, fieldStride),
        )

        for (positionToCheck in positionsToCheck) {
            position.clockwiseBigJumpWalk(positionToCheck, fieldStride) {
                val expectedPosition = when (counter) {
                    0 -> Position(x + 1, y - 1, fieldStride)
                    1 -> Position(x + 1, y + 1, fieldStride)
                    2 -> Position(x + 1, y + 1, fieldStride)
                    3 -> Position(x - 1, y + 1, fieldStride)
                    4 -> Position(x - 1, y + 1, fieldStride)
                    5 -> Position(x - 1, y - 1, fieldStride)
                    6 -> Position(x - 1, y - 1, fieldStride)
                    7 -> Position(x + 1, y - 1, fieldStride)
                    else -> assertFails { "Incorrect clockwiseBigJump walk" }
                }

                assertEquals(expectedPosition, it)

                counter++
                return@clockwiseBigJumpWalk false
            }
        }
    }
}