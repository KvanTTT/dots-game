package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player

/**
 * The KataGoDots analysis engine (`katago analysis`), which answers a JSON query with a JSON response.
 *
 * Every query carries the whole position, so the engine shares no state with the app: nothing has to be
 * synchronized, several queries may be searched at once, and a query that is no longer needed is cancelled
 * instead of being waited for.
 */
expect class KataGoDotsEngine {
    companion object {
        val IS_SUPPORTED: Boolean

        /**
         * The engine the app is shipped with, which is what it plays with until the settings of the user
         * point it somewhere else. The paths are empty when the app carries no engine at all, which is
         * every platform but the desktop one and a desktop app that was packaged without it.
         */
        val defaultSettings: KataGoDotsSettings

        suspend fun initialize(kataGoDotsSettings: KataGoDotsSettings, logger: (Diagnostic) -> Unit): KataGoDotsEngine?
    }

    val settings: KataGoDotsSettings

    val logger: (Diagnostic) -> Unit

    /**
     * @param clock the clock the engine is to think on, `null` for a game that is played without one,
     * which leaves the engine with the limits of its own config, see [PlayerClock].
     * @return the move the engine would play for [player] (the player to move by default), or `null`
     * if the rules are unsupported or the engine reported no move.
     */
    suspend fun generateMove(field: Field, player: Player?, clock: PlayerClock? = null): MoveInfo?

    /**
     * Evaluates all the candidate moves of the [field] position for [player] without playing any of them.
     *
     * @param withOwnership additionally requests [MoveAnalysis.ownership]. It's opt-in because
     * the engine then appends a value per field position, which is by far the largest part of the response.
     * @return `null` if the rules are unsupported, the query failed, or the engine reported no candidate move.
     */
    suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean = false): MoveAnalysis?

    /**
     * Evaluates the positions of a game in a single query: [moves] are the moves of the game (the start
     * position of [field] is used as its own), and [turnNumbers] are the turns of it to analyze,
     * a turn being the number of the moves played before the position.
     *
     * [onTurnAnalyzed] is called for every turn as soon as the engine reports it, which is not necessarily
     * in the order the turns were asked for, and the whole call ends when the engine has reported them all
     * or dropped the query.
     */
    suspend fun analyzeGame(
        field: Field,
        moves: List<MoveInfo>,
        turnNumbers: List<Int>,
        onTurnAnalyzed: (turnNumber: Int, analysis: MoveAnalysis) -> Unit,
    )

    /** Stops the engine process, the instance is unusable afterwards. */
    fun close()
}
