package org.dots.game

import org.dots.game.core.InitialPositionType
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.dots.game.core.generateDefaultInitialPositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InitialPositionTests {
    @Test
    fun crossGeneration() {
        assertNull(InitialPositionType.Cross.generateDefaultInitialPositions(1, 1))

        with(InitialPositionType.Cross.generateDefaultInitialPositions(2, 2)!!) {
            checkCross(1, 1)
        }

        with(InitialPositionType.Cross.generateDefaultInitialPositions(8, 8)!!) {
            checkCross(4, 4)
        }

        with(InitialPositionType.Cross.generateDefaultInitialPositions(9, 9)!!) {
            checkCross(5, 4)
        }

        with(InitialPositionType.Cross.generateDefaultInitialPositions(19, 19)!!) {
            checkCross(10, 9)
        }

        with(InitialPositionType.Cross.generateDefaultInitialPositions(39, 32)!!) {
            checkCross(20, 16)
        }

        with(InitialPositionType.Cross.generateDefaultInitialPositions(36, 36)!!) {
            checkCross(18, 18)
        }
    }

    @Test
    fun singlePositionGeneration() {
        assertNull(InitialPositionType.Single.generateDefaultInitialPositions(0, 0))
        assertEquals(MoveInfo(PositionXY(1, 1), Player.First), InitialPositionType.Single.generateDefaultInitialPositions(1, 1)!!.single())
        assertEquals(MoveInfo(PositionXY(2, 2), Player.First), InitialPositionType.Single.generateDefaultInitialPositions(2, 2)!!.single())
        assertEquals(MoveInfo(PositionXY(10, 10), Player.First), InitialPositionType.Single.generateDefaultInitialPositions(19, 19)!!.single())
        assertEquals(MoveInfo(PositionXY(20, 17), Player.First), InitialPositionType.Single.generateDefaultInitialPositions(39, 32)!!.single())
    }

    @Test
    fun doubleCrossGeneration() {
        assertNull(InitialPositionType.DoubleCross.generateDefaultInitialPositions(3, 2))

        with(InitialPositionType.DoubleCross.generateDefaultInitialPositions(4, 2)!!) {
            take(4).checkCross(1, 1)
            drop(4).checkCross(3, 1, startPlayer = Player.First)
        }

        with(InitialPositionType.DoubleCross.generateDefaultInitialPositions(5, 2)!!) {
            take(4).checkCross(2, 1)
            drop(4).checkCross(4, 1, startPlayer = Player.First)
        }

        with(InitialPositionType.DoubleCross.generateDefaultInitialPositions(19, 19)!!) {
            take(4).checkCross(9, 9)
            drop(4).checkCross(11, 9, startPlayer = Player.First)
        }

        with(InitialPositionType.DoubleCross.generateDefaultInitialPositions(39, 32)!!) {
            take(4).checkCross(19, 16)
            drop(4).checkCross(21, 16, startPlayer = Player.First)
        }

        with(InitialPositionType.DoubleCross.generateDefaultInitialPositions(36, 36)!!) {
            take(4).checkCross(17, 18)
            drop(4).checkCross(19, 18, startPlayer = Player.First)
        }
    }

    @Test
    fun quadrupleCrossGeneration() {
        assertNull(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(3, 3))

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(4, 4)!!) {
            take(4).checkCross(1, 1)
            drop(4).take(4).checkCross(3, 1)
            drop(8).take(4).checkCross(3, 3)
            drop(12).take(4).checkCross(1, 3)
        }

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(5, 4)!!) {
            take(4).checkCross(1, 1)
            drop(4).take(4).checkCross(4, 1)
            drop(8).take(4).checkCross(4, 3)
            drop(12).take(4).checkCross(1, 3)
        }

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(6, 4)!!) {
            take(4).checkCross(2, 1)
            drop(4).take(4).checkCross(4, 1)
            drop(8).take(4).checkCross(4, 3)
            drop(12).take(4).checkCross(2, 3)
        }

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(7, 4)!!) {
            take(4).checkCross(2, 1)
            drop(4).take(4).checkCross(5, 1)
            drop(8).take(4).checkCross(5, 3)
            drop(12).take(4).checkCross(2, 3)
        }

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(24, 24)!!) {
            take(4).checkCross(8, 8)
            drop(4).take(4).checkCross(16, 8)
            drop(8).take(4).checkCross(16, 16)
            drop(12).take(4).checkCross(8, 16)
        }

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(39, 32)!!) {
            take(4).checkCross(12, 11)
            drop(4).take(4).checkCross(27, 11)
            drop(8).take(4).checkCross(27, 21)
            drop(12).take(4).checkCross(12, 21)
        }

        with(InitialPositionType.QuadrupleCross.generateDefaultInitialPositions(36, 36)!!) {
            take(4).checkCross(12, 12)
            drop(4).take(4).checkCross(24, 12)
            drop(8).take(4).checkCross(24, 24)
            drop(12).take(4).checkCross(12, 24)
        }
    }

    @Test
    fun initialPositionTypeRecognition() {
        checkRecognition(InitialPositionType.Empty)

        checkRecognition(InitialPositionType.Single, MoveInfo(PositionXY(19, 19), Player.First))
        checkRecognition(InitialPositionType.Single, MoveInfo(PositionXY(19, 19), Player.Second))

        checkRecognition(InitialPositionType.Cross,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(20, 20), Player.First),
            MoveInfo(PositionXY(19, 20), Player.Second),
        )

        checkRecognition(InitialPositionType.Cross,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 20), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(19, 20), Player.Second),
        )

        checkRecognition(InitialPositionType.Cross,
            MoveInfo(PositionXY(19, 19), Player.Second),
            MoveInfo(PositionXY(20, 19), Player.First),
            MoveInfo(PositionXY(20, 20), Player.Second),
            MoveInfo(PositionXY(19, 20), Player.First),
        )

        checkRecognition(InitialPositionType.Custom,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(20, 20), Player.First),
            MoveInfo(PositionXY(19, 20), Player.First),
        )

        checkRecognition(InitialPositionType.Custom,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(21, 21), Player.First),
            MoveInfo(PositionXY(19, 20), Player.Second),
        )

        checkRecognition(InitialPositionType.DoubleCross,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(20, 20), Player.First),
            MoveInfo(PositionXY(19, 20), Player.Second),

            MoveInfo(PositionXY(21, 19), Player.Second),
            MoveInfo(PositionXY(22, 19), Player.First),
            MoveInfo(PositionXY(22, 20), Player.Second),
            MoveInfo(PositionXY(21, 20), Player.First),
        )

        checkRecognition(InitialPositionType.QuadrupleCross,
            MoveInfo(PositionXY(19, 19), Player.First),
            MoveInfo(PositionXY(20, 19), Player.Second),
            MoveInfo(PositionXY(20, 20), Player.First),
            MoveInfo(PositionXY(19, 20), Player.Second),

            MoveInfo(PositionXY(22, 19), Player.First),
            MoveInfo(PositionXY(23, 19), Player.Second),
            MoveInfo(PositionXY(23, 20), Player.First),
            MoveInfo(PositionXY(22, 20), Player.Second),

            MoveInfo(PositionXY(22, 22), Player.First),
            MoveInfo(PositionXY(23, 22), Player.Second),
            MoveInfo(PositionXY(23, 23), Player.First),
            MoveInfo(PositionXY(22, 23), Player.Second),

            MoveInfo(PositionXY(19, 22), Player.First),
            MoveInfo(PositionXY(20, 22), Player.Second),
            MoveInfo(PositionXY(20, 23), Player.First),
            MoveInfo(PositionXY(19, 23), Player.Second),
        )
    }

    private fun checkRecognition(expectedInitialPositionType: InitialPositionType, vararg actualMoveInfos: MoveInfo) {
        assertEquals(expectedInitialPositionType, Rules(initialMoves = actualMoveInfos.toList()).initialPositionType)
    }

    private fun List<MoveInfo>.checkCross(x: Int, y: Int, startPlayer: Player = Player.Second) {
        assertEquals(4, this.size)
        val oppositePlayer = startPlayer.opposite()
        assertEquals(MoveInfo(PositionXY(x, y), startPlayer), this[0])
        assertEquals(MoveInfo(PositionXY(x + 1, y), oppositePlayer), this[1])
        assertEquals(MoveInfo(PositionXY(x + 1, y + 1), startPlayer), this[2])
        assertEquals(MoveInfo(PositionXY(x, y + 1), oppositePlayer), this[3])
    }
}