package org.dots.game

import org.dots.game.core.InitPosType
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.dots.game.core.recognizeInitPosType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InitPosesTests {
    @Test
    fun crossGeneration() {
        assertNull(InitPosType.Cross.generateMoves(1, 1))

        with(InitPosType.Cross.generateMoves(2, 2)!!) {
            checkCross(1, 1)
        }

        with(InitPosType.Cross.generateMoves(8, 8)!!) {
            checkCross(4, 4)
        }

        with(InitPosType.Cross.generateMoves(9, 9)!!) {
            checkCross(5, 4)
        }

        with(InitPosType.Cross.generateMoves(19, 19)!!) {
            checkCross(10, 9)
        }

        with(InitPosType.Cross.generateMoves(39, 32)!!) {
            checkCross(20, 16)
        }

        with(InitPosType.Cross.generateMoves(36, 36)!!) {
            checkCross(18, 18)
        }
    }

    @Test
    fun singleDotGeneration() {
        assertNull(InitPosType.Single.generateMoves(0, 0))
        assertEquals(MoveInfo(PositionXY(1, 1), Player.First), InitPosType.Single.generateMoves(1, 1)!!.single())
        assertEquals(MoveInfo(PositionXY(2, 2), Player.First), InitPosType.Single.generateMoves(2, 2)!!.single())
        assertEquals(MoveInfo(PositionXY(10, 10), Player.First), InitPosType.Single.generateMoves(19, 19)!!.single())
        assertEquals(MoveInfo(PositionXY(20, 17), Player.First), InitPosType.Single.generateMoves(39, 32)!!.single())
    }

    @Test
    fun doubleCrossGeneration() {
        assertNull(InitPosType.DoubleCross.generateMoves(3, 2))

        with(InitPosType.DoubleCross.generateMoves(4, 2)!!) {
            take(4).checkCross(1, 1)
            drop(4).checkCross(3, 1, startPlayer = Player.Second)
        }

        with(InitPosType.DoubleCross.generateMoves(5, 2)!!) {
            take(4).checkCross(2, 1)
            drop(4).checkCross(4, 1, startPlayer = Player.Second)
        }

        with(InitPosType.DoubleCross.generateMoves(19, 19)!!) {
            take(4).checkCross(9, 9)
            drop(4).checkCross(11, 9, startPlayer = Player.Second)
        }

        with(InitPosType.DoubleCross.generateMoves(39, 32)!!) {
            take(4).checkCross(19, 16)
            drop(4).checkCross(21, 16, startPlayer = Player.Second)
        }

        with(InitPosType.DoubleCross.generateMoves(36, 36)!!) {
            take(4).checkCross(17, 18)
            drop(4).checkCross(19, 18, startPlayer = Player.Second)
        }
    }

    @Test
    fun quadrupleCrossGeneration() {
        assertNull(InitPosType.QuadrupleCross.generateMoves(3, 3))

        with(InitPosType.QuadrupleCross.generateMoves(4, 4)!!) {
            take(4).checkCross(1, 1)
            drop(4).take(4).checkCross(3, 1)
            drop(8).take(4).checkCross(3, 3)
            drop(12).take(4).checkCross(1, 3)
        }

        with(InitPosType.QuadrupleCross.generateMoves(5, 4)!!) {
            take(4).checkCross(1, 1)
            drop(4).take(4).checkCross(4, 1)
            drop(8).take(4).checkCross(4, 3)
            drop(12).take(4).checkCross(1, 3)
        }

        with(InitPosType.QuadrupleCross.generateMoves(6, 4)!!) {
            take(4).checkCross(2, 1)
            drop(4).take(4).checkCross(4, 1)
            drop(8).take(4).checkCross(4, 3)
            drop(12).take(4).checkCross(2, 3)
        }

        with(InitPosType.QuadrupleCross.generateMoves(7, 4)!!) {
            take(4).checkCross(2, 1)
            drop(4).take(4).checkCross(5, 1)
            drop(8).take(4).checkCross(5, 3)
            drop(12).take(4).checkCross(2, 3)
        }

        with(InitPosType.QuadrupleCross.generateMoves(24, 24)!!) {
            take(4).checkCross(8, 8)
            drop(4).take(4).checkCross(16, 8)
            drop(8).take(4).checkCross(16, 16)
            drop(12).take(4).checkCross(8, 16)
        }

        with(InitPosType.QuadrupleCross.generateMoves(39, 32)!!) {
            take(4).checkCross(12, 11)
            drop(4).take(4).checkCross(27, 11)
            drop(8).take(4).checkCross(27, 21)
            drop(12).take(4).checkCross(12, 21)
        }

        with(InitPosType.QuadrupleCross.generateMoves(36, 36)!!) {
            take(4).checkCross(12, 12)
            drop(4).take(4).checkCross(24, 12)
            drop(8).take(4).checkCross(24, 24)
            drop(12).take(4).checkCross(12, 24)
        }
    }

    @Test
    fun quadrupleRandomCrossGeneration() {
        val randomCrossesOnSmallField = InitPosType.QuadrupleCross.generateMoves(5, 5, random = Random.Default)!!
        assertEquals(16, randomCrossesOnSmallField.map { it.positionXY!! }.toSet().size)
    }

    @Test
    fun recognizeEmpty() {
        checkRecognition(InitPosType.Empty, isRandom = false)
    }

    @Test
    fun recognizeSingle() {
        checkRecognition(InitPosType.Single, isRandom = false, MoveInfo(PositionXY( 20, 17), Player.First))
        checkRecognition(InitPosType.Single, isRandom = true, MoveInfo(PositionXY(20, 17), Player.Second))

        checkRecognition(InitPosType.Single, isRandom = true, MoveInfo(PositionXY(21, 17), Player.First))
        checkRecognition(InitPosType.Single, isRandom = true, MoveInfo(PositionXY(21, 17), Player.Second))
    }

    @Test
    fun recognizeCross() {
        val standardCrossX = 20
        val standardCrossY = 16

        // Standard
        checkRecognition(InitPosType.Cross,
            isRandom = false,
            MoveInfo(PositionXY(standardCrossX, standardCrossY), Player.First),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY), Player.Second),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY + 1), Player.First),
            MoveInfo(PositionXY(standardCrossX, standardCrossY + 1), Player.Second),
        )

        // Different order doesn't affect recognition
        checkRecognition(InitPosType.Cross,
            isRandom = false,
            MoveInfo(PositionXY(standardCrossX, standardCrossY), Player.First),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY + 1), Player.First),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY), Player.Second),
            MoveInfo(PositionXY(standardCrossX, standardCrossY + 1), Player.Second),
        )

        // Reversed colors of crosses affect recognition
        checkRecognition(InitPosType.Cross,
            isRandom = true,
            MoveInfo(PositionXY(standardCrossX, standardCrossY), Player.Second),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY), Player.First),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY + 1), Player.Second),
            MoveInfo(PositionXY(standardCrossX, standardCrossY + 1), Player.First),
        )
    }

    @Test
    fun recognizeDoubleCross() {
        val standardDoubleCrossX = 19
        val standardDoubleCrossY = 16

        checkRecognition(InitPosType.DoubleCross,
            isRandom = false,
            MoveInfo(PositionXY(standardDoubleCrossX, standardDoubleCrossY), Player.First),
            MoveInfo(PositionXY(standardDoubleCrossX + 1, standardDoubleCrossY), Player.Second),
            MoveInfo(PositionXY(standardDoubleCrossX + 1, standardDoubleCrossY + 1), Player.First),
            MoveInfo(PositionXY(standardDoubleCrossX, standardDoubleCrossY + 1), Player.Second),

            MoveInfo(PositionXY(standardDoubleCrossX + 2, standardDoubleCrossY), Player.Second),
            MoveInfo(PositionXY(standardDoubleCrossX + 3, standardDoubleCrossY), Player.First),
            MoveInfo(PositionXY(standardDoubleCrossX + 3, standardDoubleCrossY + 1), Player.Second),
            MoveInfo(PositionXY(standardDoubleCrossX + 2, standardDoubleCrossY + 1), Player.First),
        )

        val xOffset = 1

        checkRecognition(InitPosType.DoubleCross,
            isRandom = true,
            MoveInfo(PositionXY(standardDoubleCrossX + xOffset, standardDoubleCrossY), Player.First),
            MoveInfo(PositionXY(standardDoubleCrossX + 1 + xOffset, standardDoubleCrossY), Player.Second),
            MoveInfo(PositionXY(standardDoubleCrossX + 1 + xOffset, standardDoubleCrossY + 1), Player.First),
            MoveInfo(PositionXY(standardDoubleCrossX + xOffset, standardDoubleCrossY + 1), Player.Second),

            MoveInfo(PositionXY(standardDoubleCrossX + 2 + xOffset, standardDoubleCrossY), Player.Second),
            MoveInfo(PositionXY(standardDoubleCrossX + 3 + xOffset, standardDoubleCrossY), Player.First),
            MoveInfo(PositionXY(standardDoubleCrossX + 3 + xOffset, standardDoubleCrossY + 1), Player.Second),
            MoveInfo(PositionXY(standardDoubleCrossX + 2 + xOffset, standardDoubleCrossY + 1), Player.First),
        )
    }

    @Test
    fun recognizeQuadrupleCrosses() {
        val standardQuadrupleCrossXLeft = 12
        val standardQuadrupleCrossXRight = 27
        val standardQuadrupleCrossYTop = 11
        val standardQuadrupleCrossYBottom = 21

        checkRecognition(InitPosType.QuadrupleCross,
            isRandom = false,
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft, standardQuadrupleCrossYTop), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft + 1, standardQuadrupleCrossYTop), Player.Second),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft + 1, standardQuadrupleCrossYTop + 1), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft, standardQuadrupleCrossYTop + 1), Player.Second),

            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYTop), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYTop), Player.Second),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYTop + 1), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYTop + 1), Player.Second),

            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYBottom), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYBottom), Player.Second),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYBottom + 1), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYBottom + 1), Player.Second),

            MoveInfo(PositionXY(standardQuadrupleCrossXLeft, standardQuadrupleCrossYBottom), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft + 1, standardQuadrupleCrossYBottom), Player.Second),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft + 1, standardQuadrupleCrossYBottom + 1), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeft, standardQuadrupleCrossYBottom + 1), Player.Second),
        )

        val standardQuadrupleCrossXLeftWithOffset = standardQuadrupleCrossXLeft + 1

        checkRecognition(InitPosType.QuadrupleCross,
            isRandom = true,
            MoveInfo(PositionXY(standardQuadrupleCrossXLeftWithOffset, standardQuadrupleCrossYTop), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeftWithOffset + 1, standardQuadrupleCrossYTop), Player.Second),
            MoveInfo(
                PositionXY(standardQuadrupleCrossXLeftWithOffset + 1, standardQuadrupleCrossYTop + 1),
                Player.First
            ),
            MoveInfo(PositionXY(standardQuadrupleCrossXLeftWithOffset, standardQuadrupleCrossYTop + 1), Player.Second),

            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYTop), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYTop), Player.Second),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYTop + 1), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYTop + 1), Player.Second),

            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYBottom), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYBottom), Player.Second),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight + 1, standardQuadrupleCrossYBottom + 1), Player.First),
            MoveInfo(PositionXY(standardQuadrupleCrossXRight, standardQuadrupleCrossYBottom + 1), Player.Second),

            MoveInfo(PositionXY(standardQuadrupleCrossXLeftWithOffset, standardQuadrupleCrossYBottom), Player.First),
            MoveInfo(
                PositionXY(standardQuadrupleCrossXLeftWithOffset + 1, standardQuadrupleCrossYBottom),
                Player.Second
            ),
            MoveInfo(
                PositionXY(standardQuadrupleCrossXLeftWithOffset + 1, standardQuadrupleCrossYBottom + 1),
                Player.First
            ),
            MoveInfo(
                PositionXY(standardQuadrupleCrossXLeftWithOffset, standardQuadrupleCrossYBottom + 1),
                Player.Second
            ),
        )
    }

    @Test
    fun recognizeCustom() {
        val standardCrossX = 20
        val standardCrossY = 16

        // Invalid players for cross
        checkRecognition(InitPosType.Custom,
            isRandom = false,
            MoveInfo(PositionXY(standardCrossX, standardCrossY), Player.First),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY), Player.Second),
            MoveInfo(PositionXY(standardCrossX + 1, standardCrossY + 1), Player.First),
            MoveInfo(PositionXY(standardCrossX, standardCrossY + 1), Player.First),
        )

        // Invalid poses for cross
        checkRecognition(InitPosType.Custom,
            isRandom = false,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(21, 21), Player.First),
            MoveInfo(PositionXY(19, 20), Player.Second),
        )
    }

    private fun checkRecognition(expectInitPosType: InitPosType, isRandom: Boolean, vararg actualMoveInfos: MoveInfo) {
        val (actualInitPosType, actualIsRandom) = recognizeInitPosType(actualMoveInfos.toList(), Rules.Standard.width, Rules.Standard.height)
        assertEquals(expectInitPosType, actualInitPosType)
        assertEquals(isRandom, actualIsRandom)
    }

    private fun List<MoveInfo>.checkCross(x: Int, y: Int, startPlayer: Player = Player.First) {
        assertEquals(4, this.size)
        val oppositePlayer = startPlayer.opposite()
        assertEquals(MoveInfo(PositionXY(x, y), startPlayer), this[0])
        assertEquals(MoveInfo(PositionXY(x + 1, y), oppositePlayer), this[1])
        assertEquals(MoveInfo(PositionXY(x + 1, y + 1), startPlayer), this[2])
        assertEquals(MoveInfo(PositionXY(x, y + 1), oppositePlayer), this[3])
    }
}