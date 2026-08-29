package org.dots.game.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.dots.game.AnalyzedMove
import org.dots.game.MoveAnalysis
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.localization.Strings
import kotlin.math.abs
import kotlin.math.roundToLong

/** The hues of the `best -> worst` gradient the analyzed moves are colored with. */
private const val bestMoveHue = 125.0f
private const val worstMoveHue = 0.0f

private const val analyzedMoveSaturation = 0.75f
private const val analyzedMoveValue = 0.95f

/** A barely visited move is drawn semi-transparent because its evaluation can't be trusted yet. */
private const val minAnalyzedMoveAlpha = 0.4f
private const val maxAnalyzedMoveAlpha = 0.9f

/** The rest of the candidate moves is only reported by the count, otherwise the table takes over the whole screen. */
private const val maxAnalyzedMoveRows = 6

private const val maxVariationLength = 6

private val numberColumnWidth = 26.dp
private val moveColumnWidth = 56.dp
private val valueColumnWidth = 58.dp
private val moveMarkerSize = 10.dp
private val moveMarkerPadding = 3.dp

/**
 * A color for [move] on the `green -> red` gradient, see [MoveAnalysis.lossOf] for the ranking
 * and [MoveAnalysis.confidenceOf] for the transparency.
 */
fun MoveAnalysis.colorOf(move: AnalyzedMove): Color {
    val hue = bestMoveHue + (worstMoveHue - bestMoveHue) * lossOf(move).toFloat()
    val alpha = minAnalyzedMoveAlpha + (maxAnalyzedMoveAlpha - minAnalyzedMoveAlpha) * confidenceOf(move).toFloat()

    return Color.hsv(hue, analyzedMoveSaturation, analyzedMoveValue, alpha)
}

private class AnalysisColumn(val getTitle: (Strings) -> String, val renderValue: (AnalyzedMove) -> String)

private val analysisValueColumns = listOf(
    AnalysisColumn({ it.winRate }, { (it.winRate * 100).toFixed(1) + "%" }),
    AnalysisColumn({ it.score }, { it.scoreLead.toSigned(1) }),
    AnalysisColumn({ it.visits }, { it.visits.toString() }),
    AnalysisColumn({ it.prior }, { (it.prior * 100).toFixed(1) + "%" }),
)

@Composable
fun MoveAnalysisView(moveAnalysis: MoveAnalysis, field: Field, uiSettings: UiSettings, strings: Strings) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tooltip(strings.moveAnalysisDescription) {
                Text(strings.moveAnalysis, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.padding(start = 10.dp, end = 5.dp).size(12.dp)
                    .border(1.dp, Color.White, CircleShape).clip(CircleShape)
                    .background(uiSettings.toColor(moveAnalysis.player))
            )
            Text(
                "${moveAnalysis.playerName(strings)} · ${strings.visits}: ${moveAnalysis.totalVisits}",
                style = MaterialTheme.typography.caption,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
            AnalysisCell("#", Modifier.width(numberColumnWidth), FontWeight.Bold)
            AnalysisCell(strings.move, Modifier.width(moveColumnWidth), FontWeight.Bold)
            analysisValueColumns.forEach {
                AnalysisCell(it.getTitle(strings), Modifier.width(valueColumnWidth), FontWeight.Bold)
            }
            AnalysisCell(strings.variation, Modifier.weight(1f), FontWeight.Bold)
        }

        for (move in moveAnalysis.moves.take(maxAnalyzedMoveRows)) {
            Tooltip(move.renderDetails(field, uiSettings.developerMode, strings)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AnalysisCell((move.order + 1).toString(), Modifier.width(numberColumnWidth))
                    Row(Modifier.width(moveColumnWidth), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(moveMarkerSize).clip(CircleShape)
                                .background(moveAnalysis.colorOf(move))
                        )
                        Spacer(Modifier.width(moveMarkerPadding))
                        AnalysisCell(
                            move.positionXY.toDisplayString(field, uiSettings.developerMode),
                            Modifier,
                        )
                    }
                    analysisValueColumns.forEach {
                        AnalysisCell(it.renderValue(move), Modifier.width(valueColumnWidth))
                    }
                    AnalysisCell(move.pv.renderVariation(field, uiSettings.developerMode), Modifier.weight(1f))
                }
            }
        }

        val notShownMovesCount = moveAnalysis.moves.size - maxAnalyzedMoveRows
        if (notShownMovesCount > 0) {
            Text(
                strings.moreAnalyzedMoves(notShownMovesCount),
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun AnalysisCell(text: String, modifier: Modifier, fontWeight: FontWeight? = null) {
    Text(text, modifier, style = MaterialTheme.typography.caption, fontWeight = fontWeight, maxLines = 1)
}

private fun MoveAnalysis.playerName(strings: Strings): String =
    if (player == Player.First) strings.firstPlayerDefaultName else strings.secondPlayerDefaultName

private fun List<PositionXY>.renderVariation(field: Field, developerMode: Boolean): String =
    take(maxVariationLength).joinToString(" ") { it.toDisplayString(field, developerMode) }

/** The values that fit neither the field nor the table, but are still worth being available on demand. */
private fun AnalyzedMove.renderDetails(field: Field, developerMode: Boolean, strings: Strings): String = buildString {
    appendLine("${strings.move}: ${positionXY.toDisplayString(field, developerMode)}")
    appendLine("${strings.winRate}: ${(winRate * 100).toFixed(2)}%")
    appendLine("${strings.scoreLead}: ${scoreLead.toSigned(2)} (${strings.deviation} ±${scoreStdev.toFixed(2)})")
    appendLine("${strings.utility}: ${utility.toFixed(4)}")
    appendLine("${strings.lowerConfidenceBound}: ${(lcb * 100).toFixed(2)}% / ${utilityLcb.toFixed(4)}")
    appendLine("${strings.weight}: ${weight.toFixed(1)}")
    appendLine("${strings.edgeVisits}: $edgeVisits")
    symmetryOf?.let { appendLine("${strings.symmetryOf}: ${it.toDisplayString(field, developerMode)}") }
    append("${strings.variation}: ${pv.joinToString(" ") { it.toDisplayString(field, developerMode) }}")
}

private fun Double.toSigned(digits: Int): String = (if (this > 0.0) "+" else "") + toFixed(digits)

/** Formats the value with exactly [digits] fraction digits, because `String.format` is unavailable in common code. */
internal fun Double.toFixed(digits: Int): String {
    var factor = 1L
    repeat(digits) { factor *= 10 }

    val scaled = (abs(this) * factor).roundToLong()
    val sign = if (this < 0.0 && scaled != 0L) "-" else ""
    val whole = scaled / factor

    if (digits == 0) return "$sign$whole"

    return "$sign$whole.${(scaled % factor).toString().padStart(digits, '0')}"
}
