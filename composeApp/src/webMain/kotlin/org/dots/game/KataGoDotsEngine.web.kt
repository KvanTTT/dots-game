package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player

actual class KataGoDotsEngine {
    actual val settings: KataGoDotsSettings
        get() = TODO("Not yet implemented")

    actual suspend fun generateMove(field: Field, player: Player?, clock: PlayerClock?): MoveInfo? {
        TODO("Not yet implemented")
    }

    actual suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis? {
        TODO("Not yet implemented")
    }

    actual suspend fun analyzeGame(
        field: Field,
        moves: List<MoveInfo>,
        turnNumbers: List<Int>,
        onTurnAnalyzed: (turnNumber: Int, analysis: MoveAnalysis) -> Unit,
    ) {
        TODO("Not yet implemented")
    }

    actual fun close() {
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