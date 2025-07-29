package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class DotState internal constructor(val value: Int) {
    companion object {
        private const val PLAYER_BITS_COUNT = 2

        private const val PLACED_PLAYER_SHIFT = PLAYER_BITS_COUNT
        private const val EMPTY_TERRITORY_SHIFT = PLACED_PLAYER_SHIFT + PLAYER_BITS_COUNT
        private const val TERRITORY_FLAG_SHIFT = EMPTY_TERRITORY_SHIFT + PLAYER_BITS_COUNT
        private const val VISITED_FLAG_SHIFT = TERRITORY_FLAG_SHIFT + 1

        private const val ACTIVE_MASK: Int = (1 shl PLAYER_BITS_COUNT) - 1
        private const val TERRITORY_FLAG: Int = 1 shl TERRITORY_FLAG_SHIFT
        private const val VISITED_FLAG: Int = 1 shl VISITED_FLAG_SHIFT
        private const val ACTIVE_AND_TERRITORY_MASK: Int = ACTIVE_MASK or TERRITORY_FLAG
        private const val INVALIDATE_TERRITORY_MASK: Int = (ACTIVE_MASK or (ACTIVE_MASK shl EMPTY_TERRITORY_SHIFT)).inv()
        private const val INVALIDATE_VISITED_MASK: Int = VISITED_FLAG.inv()

        val Empty: DotState = DotState(0)
        val Wall: DotState = DotState(Player.WallOrBoth.value)

        fun createPlaced(player: Player): DotState {
            return DotState(player.value or (player.value shl PLACED_PLAYER_SHIFT))
        }

        fun createEmptyTerritory(player: Player): DotState {
            return DotState(player.value shl EMPTY_TERRITORY_SHIFT)
        }
    }

    fun getActivePlayer(): Player {
        return Player(value and ACTIVE_MASK)
    }

    fun isActive(): Boolean {
        return value and ACTIVE_MASK != 0
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
        return value and TERRITORY_FLAG != 0
    }

    fun setTerritory(player: Player): DotState {
        return DotState(TERRITORY_FLAG or (value and INVALIDATE_TERRITORY_MASK) or player.value)
    }

    fun setVisited(): DotState {
        return DotState(value or VISITED_FLAG)
    }

    fun isVisited(): Boolean {
        return value and VISITED_FLAG != 0
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
                append("; ")
            }

            val placedPlayer = getPlacedPlayer()
            if (placedPlayer != Player.None) {
                append("Placed: ")
                append(placedPlayer)
                append("; ")
            }

            val emptyTerritoryPlayer = getEmptyTerritoryPlayer()
            if (emptyTerritoryPlayer != Player.None) {
                append("WithinEmptyTerritory: ")
                append(emptyTerritoryPlayer)
                append("; ")
            }

            if (isVisited()) {
                append("Visited; ")
            }
        }
    }
}

const val FIRST_PLAYER_MARKER = '*'
const val SECOND_PLAYER_MARKER = '+'
const val TERRITORY_EMPTY_MARKER = '^'
const val EMPTY_TERRITORY_MARKER = '`'
const val EMPTY_POSITION_MARKER = '.'
const val BOARDER_MARKER = '#'
const val VISITED_MARKER = '$'

val playerMarker = mapOf(
    Player.First to FIRST_PLAYER_MARKER,
    Player.Second to SECOND_PLAYER_MARKER,
    Player.None to EMPTY_POSITION_MARKER,
    Player.WallOrBoth to BOARDER_MARKER,
)
