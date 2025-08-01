package org.dots.game.core

import kotlin.random.Random

typealias Hash = Long

object ZobristHash {
    const val MAX_POSITIONS_SIZE = (Field.MAX_SIZE + 2) * (Field.MAX_SIZE + 2)
    val random = Random(1)

    val widthHash: LongArray = LongArray(Field.MAX_SIZE + 1) {
        random.nextLong()
    }

    val heightHash: LongArray = LongArray(Field.MAX_SIZE + 1) {
        random.nextLong()
    }

    val positionsHash: Array<LongArray> = Array(MAX_POSITIONS_SIZE) {
        LongArray(Player.Count) {
            if (it.toByte() == Player.None.value || it.toByte() == Player.WallOrBoth.value) {
                0L
            } else {
                random.nextLong()
            }
        }
    }

    fun getPositionsValue(position: Position, player: Player): Long {
        return positionsHash[position.value.toInt()][player.value.toInt()]
    }
}