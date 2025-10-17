package org.dots.game

import androidx.compose.ui.graphics.Color
import org.dots.game.core.Player
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.PolygonDrawMode

const val minFieldDimension = 4
const val maxFieldDimension = 39

data class UiSettings(
    val playerFirstColor: Color = Color.Blue,
    val playerSecondColor: Color = Color.Red,
    val connectionDrawMode: ConnectionDrawMode = ConnectionDrawMode.PolygonOutline,
    val baseDrawMode: PolygonDrawMode = PolygonDrawMode.OutlineAndFill,
    val showDiagonalConnections: Boolean = false,
    val showThreats: Boolean = false,
    val showSurroundings: Boolean = false,
    val developerMode: Boolean = false,
) {
    companion object {
        val Standard = UiSettings()
    }

    fun toColor(player: Player): Color = if (player == Player.First) playerFirstColor else playerSecondColor
}
