package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.Rules

expect class KataGoDotsEngine {
    companion object {
        suspend fun initialize(kataGoDotsSettings: KataGoDotsSettings, onMessage: (Diagnostic) -> Unit): KataGoDotsEngine?
    }

    val settings: KataGoDotsSettings

    suspend fun sync(field: Field)

    suspend fun generateMove(player: Player, field: Field): MoveInfo?
}