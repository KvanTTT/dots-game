package org.dots.game

import androidx.compose.ui.graphics.Color
import org.dots.game.core.ClassSettings
import org.dots.game.core.Player
import org.dots.game.localization.Language
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.PolygonDrawMode

const val minFieldDimension = 4
const val maxFieldDimension = 39

data class UiSettings(
    val playerFirstColor: Color = Color.Blue,
    val playerSecondColor: Color = Color.Red,
    val connectionDrawMode: ConnectionDrawMode = ConnectionDrawMode.PolygonOutlineAndFill,
    val baseDrawMode: PolygonDrawMode = PolygonDrawMode.OutlineAndFill,
    val showDiagonalConnections: Boolean = false,
    val showThreats: Boolean = false,
    val showSurroundings: Boolean = false,
    val developerMode: Boolean = false,
    val experimentalMode: Boolean = false,
    val showWinRateGraph: Boolean = true,
    val showScoreGraph: Boolean = true,
    val showWeightGraph: Boolean = false,
    val showVisitsGraph: Boolean = false,
    val showCandidateMoves: Boolean = true,
    val showOwnership: Boolean = false,
    val language: Language = Language.English,
) : ClassSettings<UiSettings>() {
    companion object {
        val Standard = UiSettings()
    }

    override val default: UiSettings
        get() = Standard

    /** The analysis is only run while something displays it, so it needs no option of its own. */
    val analysisEnabled: Boolean
        get() = showCandidateMoves || showOwnership

    fun toColor(player: Player): Color = if (player == Player.First) playerFirstColor else playerSecondColor
}
