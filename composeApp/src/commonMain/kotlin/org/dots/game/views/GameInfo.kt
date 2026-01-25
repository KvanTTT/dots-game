package org.dots.game.views

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.Game
import org.dots.game.isAlmostEqual
import org.dots.game.localization.Strings
import org.dots.game.toNeatNumber

@Composable
fun GameInfo(
    currentGame: Game,
    player1Score: Double,
    player2Score: Double,
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
        Row {
            val player1Name = currentGame.player1Name ?: strings.firstPlayerDefaultName
            val player2Name = currentGame.player2Name ?: strings.secondPlayerDefaultName

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
                val diff = player2Score - player1Score
                val winnerColor: Color = when {
                    diff.isAlmostEqual(0.0) -> Color.Black
                    diff > 0.0 -> uiSettings.playerSecondColor
                    else -> uiSettings.playerFirstColor
                }
                Text("  ($diff)", color = winnerColor)
            }
        }
    }
}