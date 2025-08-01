package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class Player internal constructor(val value: Byte) {
    companion object {
        const val Count = 4
        val None = Player(0)
        val First = Player(1)
        val Second = Player(2)
        val WallOrBoth = Player(3)

        fun validateAndCreate(playerId: Int): Player {
            if (playerId in 0..3) {
                return Player(playerId.toByte())
            } else {
                error("The playerId mush be between 0 and 3, the passed value is $playerId")
            }
        }
    }

    fun opposite(): Player = Player((value.toInt() xor 3).toByte())

    operator fun plus(other: Player): Player = Player((value.toInt() or other.value.toInt()).toByte())

    override fun toString(): String {
        return when (value) {
            None.value -> ::None.name
            First.value -> ::First.name
            Second.value -> ::Second.name
            WallOrBoth.value -> ::WallOrBoth.name
            else -> error("Incorrect player $value")
        }
    }
}