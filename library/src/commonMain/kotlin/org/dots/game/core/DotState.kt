package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class DotState internal constructor(val value: Int) {
    companion object {
        private const val PLAYER_BITS_COUNT = 2

        private const val PLACED_PLAYER_SHIFT = PLAYER_BITS_COUNT
        private const val EMPTY_TERRITORY_SHIFT = PLACED_PLAYER_SHIFT + PLAYER_BITS_COUNT
        private const val TERRITORY_FLAG_SHIFT = EMPTY_TERRITORY_SHIFT + PLAYER_BITS_COUNT
        private const val BOARDER_FLAG_SHIFT = TERRITORY_FLAG_SHIFT + 1

        private const val ACTIVE_MASK: Int = (1 shl PLAYER_BITS_COUNT) - 1
        private const val TERRITORY_FLAG: Int = 1 shl TERRITORY_FLAG_SHIFT
        private const val BORDER_FLAG: Int = 1 shl BOARDER_FLAG_SHIFT
        private const val ACTIVE_AND_TERRITORY_MASK: Int = ACTIVE_MASK or TERRITORY_FLAG
        private const val INVALIDATE_TERRITORY_MASK: Int = (ACTIVE_MASK or (ACTIVE_MASK shl EMPTY_TERRITORY_SHIFT)).inv()

        val Empty: DotState = DotState(0)
        val Border: DotState = DotState(BORDER_FLAG)

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

    fun checkActive(): Boolean {
        return value and ACTIVE_MASK != 0 || value and BORDER_FLAG != 0
    }

    fun checkActive(player: Player): Boolean {
        return value and ACTIVE_MASK == player.value || value and BORDER_FLAG != 0
    }

    fun checkActiveAndTerritory(player: Player): Boolean {
        return value and ACTIVE_AND_TERRITORY_MASK == player.value or TERRITORY_FLAG
    }

    fun checkActiveAndNotTerritory(player: Player): Boolean {
        return value and ACTIVE_AND_TERRITORY_MASK == player.value
    }

    fun checkPlaced(player: Player): Boolean {
        return value shr PLACED_PLAYER_SHIFT and ACTIVE_MASK == player.value
    }

    fun getPlacedPlayer(): Player {
        return Player(value shr PLACED_PLAYER_SHIFT and ACTIVE_MASK)
    }

    fun getEmptyTerritoryPlayer(): Player {
        return Player(value shr EMPTY_TERRITORY_SHIFT and ACTIVE_MASK)
    }

    fun checkWithinEmptyTerritory(player: Player): Boolean {
        return value shr EMPTY_TERRITORY_SHIFT and ACTIVE_MASK == player.value
    }

    fun checkTerritory(): Boolean {
        return value and TERRITORY_FLAG != 0
    }

    fun setTerritory(player: Player): DotState {
        return DotState(TERRITORY_FLAG or (value and INVALIDATE_TERRITORY_MASK) or player.value)
    }

    fun checkBorder(): Boolean {
        return value and BORDER_FLAG != 0
    }

    override fun toString(): String {
        return buildString {
            val activePlayer = getActivePlayer()
            if (activePlayer != Player.None) {
                append("Active: Player")
                append(activePlayer.value)
                append("; ")
            }

            val placedPlayer = getPlacedPlayer()
            if (placedPlayer != Player.None) {
                append("Placed: Player ")
                append(placedPlayer.value)
                append("; ")
            }

            val emptyTerritoryPlayer = getEmptyTerritoryPlayer()
            if (emptyTerritoryPlayer != Player.None) {
                append("WithinEmptyTerritory: Player")
                append(emptyTerritoryPlayer.value)
                append("; ")
            }

            if (checkBorder()) {
                append("Border; ")
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

val playerMarker = mapOf(
    Player.First to FIRST_PLAYER_MARKER,
    Player.Second to SECOND_PLAYER_MARKER,
    Player.None to EMPTY_POSITION_MARKER,
    Player.WallOrBoth to BOARDER_MARKER,
)
