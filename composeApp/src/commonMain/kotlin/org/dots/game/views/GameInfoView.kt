package org.dots.game.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.Game
import org.dots.game.core.GameResult
import org.dots.game.isAlmostEqual
import org.dots.game.localization.Strings
import org.dots.game.toNeatNumber

@Composable
fun GameInfo(
    currentGame: Game,
    player1Score: Double,
    player2Score: Double,
    gameResult: GameResult?,
    strings: Strings,
    uiSettings: UiSettings
) {
    val rules = currentGame.rules
    val rulesInfo = buildString {
        appendLine("${strings.initPosType}: ${strings.initPosTypeLabel(rules.initPosType)}")
        appendLine("${strings.initPosGenType}: ${strings.initPosGenTypeLabel(rules.initPosGenType)}")
        appendLine("${strings.baseMode}: ${strings.baseModeLabel(rules.baseMode)}")
        appendLine("${strings.suicideAllowed}: ${strings.boolToString(rules.suicideAllowed)}")
        appendLine("${strings.komi}: ${rules.komi.toNeatNumber()}")
        if (rules.captureByBorder) {
            appendLine("${strings.captureByBorder}: ${strings.boolToString(rules.captureByBorder)}")
        }
        if (lastOrNull() == '\n') {
            deleteAt(lastIndex)
        }
    }

    Tooltip(rulesInfo) {
        val player1Name = currentGame.player1Name ?: strings.firstPlayerDefaultName
        val player2Name = currentGame.player2Name ?: strings.secondPlayerDefaultName
        val diff = player2Score - player1Score
        val winnerColor: Color = when {
            diff.isAlmostEqual(0.0) -> Color.Black
            diff > 0.0 -> uiSettings.playerSecondColor
            else -> uiSettings.playerFirstColor
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$player1Name   ", color = uiSettings.playerFirstColor)
                Text(
                    player1Score.toNeatNumber().toString(),
                    color = uiSettings.playerFirstColor,
                    fontWeight = FontWeight.Bold
                )

                Text(" : ")

                Text(
                    player2Score.toNeatNumber().toString(),
                    color = uiSettings.playerSecondColor,
                    fontWeight = FontWeight.Bold
                )
                Text("   $player2Name", color = uiSettings.playerSecondColor)

                if (uiSettings.developerMode) {
                    Text("  (${diff.toNeatNumber()})", color = winnerColor)
                }
            }
            if (gameResult != null) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val winner = when (gameResult) {
                        is GameResult.WinGameResult -> gameResult.winner
                        is GameResult.Draw -> null
                    }
                    val reason = when (gameResult) {
                        is GameResult.Draw -> null
                        is GameResult.InterruptWin -> strings.interrupt
                        is GameResult.ResignWin -> strings.resign
                        is GameResult.ScoreWin -> strings.score
                        is GameResult.TimeWin -> strings.time
                        is GameResult.UnknownWin -> strings.unknown
                    }
                    val resultText = buildString {
                        append(strings.result)
                        append(": ")
                        if (winner != null) {
                            append(player1Name)
                            append(' ')
                            append(strings.win)
                        } else {
                            append(strings.draw)
                        }
                        if (reason != null) {
                            append(" (")
                            append(strings.reason)
                            append(": ")
                            append(reason)
                            if (gameResult is GameResult.ScoreWin) {
                                append(" +")
                                append(gameResult.score.toNeatNumber())
                            }
                            append(')')
                        }
                    }
                    Text(
                        resultText,
                        color = winnerColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}