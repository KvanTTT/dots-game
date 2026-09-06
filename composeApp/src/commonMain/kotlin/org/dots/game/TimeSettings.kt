package org.dots.game

import org.dots.game.core.ClassSettings
import org.dots.game.core.Game
import org.dots.game.core.GameTreeNode
import org.dots.game.core.Player
import org.dots.game.core.PropertiesHolder
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

/**
 * The time control of a game: Dots is normally played with [mainTimeMinutes] for the whole game plus
 * [turnTimeSeconds] for every move, where the time of a move is spent first and only its overflow is charged
 * to the main time, which is known as a Bronstein delay. The time of a move that is left is not banked.
 *
 * A player who has spent the whole main time loses the game, see [ExternalFinishReason.Time].
 */
data class TimeSettings(
    val mainTimeMinutes: Int = 5,
    val turnTimeSeconds: Int = 25,
) : ClassSettings<TimeSettings>() {
    companion object {
        val Default = TimeSettings()

        const val MAX_MAIN_TIME_MINUTES = 60
        const val MAX_TURN_TIME_SECONDS = 60
    }

    override val default: TimeSettings
        get() = Default

    /** A clock of no time at all would end the game at once, so both times zeroed mean no clock. */
    val isEnabled: Boolean
        get() = mainTimeMinutes > 0 || turnTimeSeconds > 0

    val mainTimeSeconds: Int
        get() = mainTimeMinutes * SECONDS_PER_MINUTE
}

/** How much of the main time every player has left, in seconds. */
data class MainTimeLeft(val player1: Double, val player2: Double) {
    operator fun get(player: Player): Double = if (player == Player.First) player1 else player2

    fun with(player: Player, secondsLeft: Double): MainTimeLeft =
        if (player == Player.First) copy(player1 = secondsLeft) else copy(player2 = secondsLeft)
}

/**
 * The main time both players have left at this node, which SGF keeps as the `BL` and `WL` properties
 * of a move.
 *
 * A move only records the time of the player who made it, so the time of the other one is the one of
 * the move that player made last; what a player who hasn't moved yet has left is the time the game starts
 * with, that is the `BL` and `WL` of the game itself and, failing those, its time limit (`TM`).
 *
 * @return `null` if the move of either player records no time of it: the move has spent an unknown part
 * of the clock, so what the moves before it recorded is out of date rather than still valid.
 */
fun GameTreeNode.mainTimeLeft(game: Game?): MainTimeLeft? {
    fun timeLeftOf(player: Player): Double? {
        var node: GameTreeNode? = this
        while (node != null) {
            if (node.isMoveOf(player)) return node.timeLeft(player)
            node = node.previousNode
        }

        // The player hasn't moved yet, thus nothing of the time the game starts with is spent
        return game?.timeLeft(player) ?: game?.time
    }

    return MainTimeLeft(timeLeftOf(Player.First) ?: return null, timeLeftOf(Player.Second) ?: return null)
}

private fun GameTreeNode.isMoveOf(player: Player): Boolean =
    !(if (player == Player.First) player1Moves else player2Moves).isNullOrEmpty()

/** The `BL` or the `WL` property of a move or of a game, see [mainTimeLeft]. */
private fun PropertiesHolder.timeLeft(player: Player): Double? =
    if (player == Player.First) player1TimeLeft else player2TimeLeft

/**
 * Stamps on this node what [player] has left of the main time after the move it was made with, that is
 * the `BL` property of SGF or the `WL` one: only the player who made a move has spent any time on it,
 * and the time of the other one is read back off the move that player made last, see [mainTimeLeft].
 */
fun GameTreeNode.setMainTimeLeft(player: Player, secondsLeft: Double) {
    val roundedSecondsLeft = secondsLeft.roundToTenthOfSecond()
    if (player == Player.First) {
        player1TimeLeft = roundedSecondsLeft
    } else {
        player2TimeLeft = roundedSecondsLeft
    }
}

/**
 * The clock of a game: what every player has left of the main time and what the player who is thinking
 * right now has left of the time of the move, see [TimeSettings].
 */
data class TimeSpending(val mainTimeLeft: MainTimeLeft, val turnTimeLeft: Double) {
    companion object {
        val None = TimeSpending(MainTimeLeft(0.0, 0.0), 0.0)

        fun of(timeSettings: TimeSettings): TimeSpending = TimeSpending(
            MainTimeLeft(timeSettings.mainTimeSeconds.toDouble(), timeSettings.mainTimeSeconds.toDouble()),
            timeSettings.turnTimeSeconds.toDouble(),
        )
    }

    /**
     * Charges [elapsedSeconds] of thinking to [player]: the time of the move is spent first and only what
     * a move takes on top of it reduces the main time, that is a Bronstein delay.
     */
    fun spend(player: Player, elapsedSeconds: Double): TimeSpending {
        val spentTurnTime = min(turnTimeLeft, elapsedSeconds)
        val spentMainTime = elapsedSeconds - spentTurnTime

        return TimeSpending(
            mainTimeLeft.with(player, (mainTimeLeft[player] - spentMainTime).coerceAtLeast(0.0)),
            turnTimeLeft - spentTurnTime,
        )
    }

    /** A move starts with the whole time of a move: what is left of the previous one is not banked. */
    fun startTurn(timeSettings: TimeSettings): TimeSpending = copy(turnTimeLeft = timeSettings.turnTimeSeconds.toDouble())

    /** A player who has spent both the time of the move and the main one loses the game. */
    fun isTimeUp(player: Player): Boolean = turnTimeLeft <= 0.0 && mainTimeLeft[player] <= 0.0
}

internal const val SECONDS_PER_MINUTE = 60

/**
 * The precision the time left is stored with: a tenth of a second is exact enough for a game and it keeps
 * the `BL` and `WL` properties of SGF short.
 */
fun Double.roundToTenthOfSecond(): Double = round(this * 10.0) / 10.0

/**
 * Renders the remaining time as `m:ss`, rounding up, so that the clock only shows `0:00` when the time
 * is really over rather than during the last second of it.
 */
fun Double.toClockString(): String {
    val totalSeconds = ceil(this.coerceAtLeast(0.0)).toInt()
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return "${totalSeconds / SECONDS_PER_MINUTE}:${if (seconds < 10) "0" else ""}$seconds"
}
