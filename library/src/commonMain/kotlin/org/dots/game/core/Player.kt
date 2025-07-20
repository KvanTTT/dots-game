package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class Player internal constructor(val value: Int) {
    companion object {
        val None = Player(0)
        val First = Player(1)
        val Second = Player(2)
        val WallOrBoth = Player(3)

        fun validateAndCreate(playerId: Int): Player {
            if (playerId in 0..3) {
                return Player(playerId)
            } else {
                error("The playerId mush be between 0 and 3, the passed value is $playerId")
            }
        }
    }

    fun opposite(): Player = Player(value xor 3)

    operator fun plus(other: Player): Player = Player(value or other.value)

    override fun toString(): String {
        return when (value) {
            0 -> ::None.name
            1 -> ::First.name
            2 -> ::Second.name
            3 -> ::WallOrBoth.name
            else -> error("Incorrect player $value")
        }
    }
}