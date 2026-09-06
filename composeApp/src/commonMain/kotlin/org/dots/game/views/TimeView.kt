package org.dots.game.views

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import org.dots.game.TimeSpending
import org.dots.game.TimeSettings
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.Player
import org.dots.game.localization.Strings
import org.dots.game.toClockString
import kotlin.math.ceil

/**
 * The clock of a game: the main time of every player and, for the player to move, the time of the move
 * that is spent before it, see [TimeSettings].
 *
 * @param timeSettings the time control the clock is running by, `null` for the times a game merely keeps
 * a record of, see [org.dots.game.mainTimeLeft].
 * @param movePlayer the player whose clock is running, `null` if no clock is running at all.
 */
@Composable
fun TimeView(
    timeSettings: TimeSettings?,
    timeSpending: TimeSpending,
    movePlayer: Player?,
    strings: Strings,
    uiSettings: UiSettings,
) {
    val timeControlInfo = timeSettings?.let {
        buildString {
            appendLine("${strings.mainTime}: ${it.mainTimeMinutes.toMinutesString()}")
            append("${strings.turnTime}: ${it.turnTimeSeconds}")
        }
    }

    Tooltip(timeControlInfo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun PlayerTime(player: Player) {
                val isMovePlayer = player == movePlayer
                val time = buildString {
                    append(timeSpending.mainTimeLeft[player].toClockString())
                    // The time of the move is only spent by the player who is thinking right now
                    if (isMovePlayer && (timeSettings?.turnTimeSeconds ?: 0) > 0) {
                        append(" +${ceil(timeSpending.turnTimeLeft.coerceAtLeast(0.0)).toInt()}")
                    }
                }

                Text(
                    time,
                    color = uiSettings.toColor(player),
                    fontWeight = if (isMovePlayer) FontWeight.Bold else FontWeight.Normal,
                )
            }

            PlayerTime(Player.First)
            Text(" : ")
            PlayerTime(Player.Second)
        }
    }
}

/** The minutes of a time control, which are whole unless the game was played with a time of its own. */
private fun Double.toMinutesString(): String = if (this % 1.0 == 0.0) toInt().toString() else toFixed(1)
