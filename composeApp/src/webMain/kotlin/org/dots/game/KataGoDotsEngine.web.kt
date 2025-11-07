package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player

actual class KataGoDotsEngine {
    actual val settings: KataGoDotsSettings
        get() = TODO("Not yet implemented")

    actual suspend fun sync(field: Field): SyncType {
        TODO("Not yet implemented")
    }

    actual suspend fun generateMove(field: Field, player: Player?): MoveInfo? {
        TODO("Not yet implemented")
    }

    actual companion object {
        actual const val IS_SUPPORTED = false

        actual suspend fun initialize(
            kataGoDotsSettings: KataGoDotsSettings,
            logger: (Diagnostic) -> Unit
        ): KataGoDotsEngine? {
            return null
        }
    }

    actual val logger: (Diagnostic) -> Unit
        get() = TODO("Not yet implemented")
}