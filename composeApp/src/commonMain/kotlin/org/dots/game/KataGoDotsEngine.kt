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

    /**
     * @return `null` if the rules are unsupported, synchronization is rejected, or the engine reports no move.
     */
    suspend fun generateMove(field: Field, player: Player?): MoveInfo?

    /**
     * Evaluates all the candidate moves of the current [field] position without playing any of them.
     * [analyze] calls [sync] before evaluating.
     *
     * @param withOwnership additionally requests [MoveAnalysis.ownership]. It's opt-in because
     * the engine then appends a value per field position, which is by far the largest part of the response.
     * @return `null` if the rules are unsupported, the engine rejected synchronization,
     * the analysis command failed, or the engine reported no candidate move.
     */
    suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean = false): MoveAnalysis?
}

sealed class SyncType {
    /**
     * `false` if the engine position doesn't match the field one, thus no command may rely on it.
     */
    val isSynchronized: Boolean
        get() = this != UnsupportedRules && this != SyncFailed

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

/**
 * The engine rejected one of the synchronization commands, the reason is reported to
 * [KataGoDotsEngine.logger]. The position of the engine is undefined afterwards, and it's recovered
 * by the next [KataGoDotsEngine.sync] that detects the mismatch and resynchronizes from scratch.
 */
object SyncFailed : SyncType()

