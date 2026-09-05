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
 * @param movePlayer the player whose clock is running, `null` if the game is over.
 */
@Composable
fun TimeView(
    timeSettings: TimeSettings,
    timeSpending: TimeSpending,
    movePlayer: Player?,
    strings: Strings,
    uiSettings: UiSettings,
) {
    val timeControlInfo = buildString {
        appendLine("${strings.mainTime}: ${timeSettings.mainTimeMinutes}")
        append("${strings.turnTime}: ${timeSettings.turnTimeSeconds}")
    }

    Tooltip(timeControlInfo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun PlayerTime(player: Player) {
                val isMovePlayer = player == movePlayer
                val time = buildString {
                    append(timeSpending.mainTimeLeft[player].toClockString())
                    // The time of the move is only spent by the player who is thinking right now
                    if (isMovePlayer && timeSettings.turnTimeSeconds > 0) {
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
