package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player

expect class KataGoDotsEngine {
    companion object {
        val IS_SUPPORTED: Boolean

        suspend fun initialize(kataGoDotsSettings: KataGoDotsSettings, logger: (Diagnostic) -> Unit): KataGoDotsEngine?
    }

    val settings: KataGoDotsSettings

    val logger: (Diagnostic) -> Unit

    /**
     * [generateMove] calls [sync] before generating.
     * However, the external [sync] call is needed if pondering (evaluating on a player's move) is enabled.
     */
    suspend fun sync(field: Field): SyncType

    suspend fun generateMove(field: Field, player: Player?): MoveInfo?
}

sealed class SyncType {
    override fun toString(): String {
        return buildString {
            append("SyncType: ${this@SyncType::class.simpleName}")
            if (this@SyncType is MovesSync) {
                if (this@SyncType.undoMovesCount > 0) {
                    append("; undo: -${this@SyncType.undoMovesCount}")
                }
                if (this@SyncType.moves.isNotEmpty()) {
                    append("; moves: +${moves.size}")
                }
            }
        }
    }
}

object FullSync : SyncType()

class MovesSync(val undoMovesCount: Int, val moves: List<MoveInfo>) : SyncType()

object NoSync : SyncType()

object UnsupportedRules : SyncType()

