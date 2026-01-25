package org.dots.game.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dots.game.Tooltip
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.Games
import org.dots.game.core.Player
import org.dots.game.localization.Strings
import org.dots.game.toPercent

@Composable
fun SgfStatsView(games: Games, strings: Strings) {
    var firstPlayerWins = 0
    var secondPlayerWins = 0
    var draws = 0
    var remainingValidModesRatioSum = 0.0
    for (game in games) {
        when (val gameResult = game.result) {
            is GameResult.WinGameResult -> {
                when (gameResult.winner) {
                    Player.First -> firstPlayerWins++
                    Player.Second -> secondPlayerWins++
                }
            }
            is GameResult.Draw -> {
                draws++
            }
            else -> {
            }
        }
        val gameTree = game.gameTree
        val field = gameTree.field

        val currentNode = gameTree.currentNode
        gameTree.rewindToEnd()

        if (gameTree.currentNode.moveResults.lastOrNull() is GameResult) {
            gameTree.back()
        }

        var remainingValidModes = 0
        val currentPlayer = field.getCurrentPlayer()
        for (y in Field.OFFSET ..<field.height + Field.OFFSET) {
            for (x in Field.OFFSET..<field.width + Field.OFFSET) {
                if (field.getPositionIfValid(x, y, currentPlayer) != null) {
                    remainingValidModes++
                }
            }
        }
        val remainingValidModesRatio = remainingValidModes.toDouble() / (field.width * field.height)
        require(remainingValidModesRatio <= 1.0)
        remainingValidModesRatioSum += remainingValidModesRatio

        gameTree.switch(currentNode)
    }

    val total = firstPlayerWins + secondPlayerWins + draws
    if (total > 0) {
        val avgRemainingValidModesRatio = remainingValidModesRatioSum / games.size
        require(avgRemainingValidModesRatio <= 1.0)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 15.dp)) {
            Text(
                text = strings.sgfStats,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${strings.firstPlayerDefaultName}: ${firstPlayerWins.toPercent(total)}, " +
                        "${strings.secondPlayerDefaultName}: ${secondPlayerWins.toPercent(total)}, " +
                        "${strings.draws}: ${draws.toPercent(total)}",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                style = MaterialTheme.typography.body2,
            )
            Tooltip(strings.avgRemainingMovesComment) {
                Text(
                    text = "${strings.avgRemainingMoves}: ${avgRemainingValidModesRatio.toPercent()}",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}