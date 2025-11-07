package org.dots.game.core

enum class MoveMode {
    Next,
    First,
    Second;

    fun getMovePlayer(field: Field): Player {
        return when (this) {
            Next -> field.getCurrentPlayer()
            First -> Player.First
            Second -> Player.Second
        }
    }
}