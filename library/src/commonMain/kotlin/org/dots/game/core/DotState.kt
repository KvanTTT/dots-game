package org.dots.game.core

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.jvm.JvmInline

@JvmInline
value class DotState internal constructor(val value: Byte) {
    companion object {
        private const val PLAYER_BITS_COUNT: Int = 2
        private const val ZERO: Byte = 0.toByte()

        private const val PLACED_PLAYER_SHIFT: Int = PLAYER_BITS_COUNT
        private const val EMPTY_TERRITORY_SHIFT: Int = PLACED_PLAYER_SHIFT + PLAYER_BITS_COUNT
        private const val TERRITORY_FLAG_SHIFT: Int = EMPTY_TERRITORY_SHIFT + PLAYER_BITS_COUNT
        private const val VISITED_FLAG_SHIFT: Int = TERRITORY_FLAG_SHIFT + 1

        private const val ACTIVE_MASK: Byte = ((1 shl PLAYER_BITS_COUNT) - 1).toByte()
        private const val TERRITORY_FLAG: Byte = (1 shl TERRITORY_FLAG_SHIFT).toByte()
        private const val VISITED_FLAG: Byte = (1 shl VISITED_FLAG_SHIFT).toByte()
        private const val ACTIVE_AND_TERRITORY_MASK: Byte = (ACTIVE_MASK.toInt() or TERRITORY_FLAG.toInt()).toByte()
        private const val INVALIDATE_TERRITORY_MASK: Byte = (ACTIVE_MASK.toInt() or (ACTIVE_MASK.toInt() shl EMPTY_TERRITORY_SHIFT)).inv().toByte()
        private const val INVALIDATE_VISITED_MASK: Byte = VISITED_FLAG.toInt().inv().toByte()

        val Empty: DotState = DotState(0)
        val Wall: DotState = DotState(Player.WallOrBoth.value)

        init {
            require(VISITED_FLAG_SHIFT <= Byte.SIZE_BITS - 1)
        }

        fun createPlaced(player: Player): DotState {
            return DotState(player.value or (player.value shl PLACED_PLAYER_SHIFT))
        }

        fun createEmptyTerritory(player: Player): DotState {
            return DotState(player.value shl EMPTY_TERRITORY_SHIFT)
        }

        private infix fun Byte.shl(other: Int): Byte = (toInt() shl other).toByte()
        private infix fun Byte.shr(other: Int): Byte = (toInt() shr other).toByte()
    }

    fun getActivePlayer(): Player {
        return Player(value and ACTIVE_MASK)
    }

    fun isActive(): Boolean {
        return value and ACTIVE_MASK != ZERO
    }

    fun isActive(player: Player): Boolean {
        return value and ACTIVE_MASK == player.value
    }

    fun isActiveAndTerritory(player: Player): Boolean {
        return value and ACTIVE_AND_TERRITORY_MASK == player.value or TERRITORY_FLAG
    }

    fun isActiveAndNotTerritory(player: Player): Boolean {
        return value and ACTIVE_AND_TERRITORY_MASK == player.value
    }

    fun isPlaced(player: Player): Boolean {
        return value shr PLACED_PLAYER_SHIFT and ACTIVE_MASK == player.value
    }

    fun getPlacedPlayer(): Player {
        return Player(value shr PLACED_PLAYER_SHIFT and ACTIVE_MASK)
    }

    fun getEmptyTerritoryPlayer(): Player {
        return Player(value shr EMPTY_TERRITORY_SHIFT and ACTIVE_MASK)
    }

    fun isWithinEmptyTerritory(player: Player): Boolean {
        return value shr EMPTY_TERRITORY_SHIFT and ACTIVE_MASK == player.value
    }

    fun isTerritory(): Boolean {
        return value and TERRITORY_FLAG != ZERO
    }

    fun setTerritoryAndActivePlayer(player: Player): DotState {
        return DotState(TERRITORY_FLAG or (value and INVALIDATE_TERRITORY_MASK) or player.value)
    }

    fun setVisited(): DotState {
        return DotState(value or VISITED_FLAG)
    }

    fun isVisited(): Boolean {
        return value and VISITED_FLAG != ZERO
    }

    fun clearVisited(): DotState {
        return DotState(value and INVALIDATE_VISITED_MASK)
    }

    override fun toString(): String {
        return buildString {
            val activePlayer = getActivePlayer()
            if (activePlayer != Player.None) {
                append("Active: ")
                append(activePlayer)
            }

            val placedPlayer = getPlacedPlayer()
            if (placedPlayer != Player.None) {
                if (isNotEmpty()) append(", ")
                append("Placed: ")
                append(placedPlayer)
            }

            val emptyTerritoryPlayer = getEmptyTerritoryPlayer()
            if (emptyTerritoryPlayer != Player.None) {
                if (isNotEmpty()) append(", ")
                append("WithinEmptyTerritory: ")
                append(emptyTerritoryPlayer)
            }

            if (isTerritory()) {
                if (isNotEmpty()) append(", ")
                append("Territory")
            }

            if (isVisited()) {
                if (isNotEmpty()) append(", ")
                append("Visited")
            }
        }.takeIf { it.isNotEmpty() } ?: "Empty"
    }
}

const val FIRST_PLAYER_MARKER = '*'
const val SECOND_PLAYER_MARKER = '+'
const val TERRITORY_EMPTY_MARKER = '^'
const val EMPTY_TERRITORY_MARKER = '`'
const val EMPTY_POSITION_MARKER = '.'
const val BORDER_MARKER = '#'
const val VISITED_MARKER = '$'

val playerMarker = mapOf(
    Player.First to FIRST_PLAYER_MARKER,
    Player.Second to SECOND_PLAYER_MARKER,
    Player.None to EMPTY_POSITION_MARKER,
    Player.WallOrBoth to BORDER_MARKER,
)
