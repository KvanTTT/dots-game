package org.dots.game.core.features

import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position

fun Field.getCrosses(): List<Position> {
    return buildList {
        for (move in moveSequence) {
            val position = move.position
            with(this) {
                val state = position.getState()

                val territoryOrPlacedPlayer = state.getActivePlayer()
                if (territoryOrPlacedPlayer != Player.None) {
                    val xp1yp1Position = position.xp1yp1(realWidth)
                    if (!xp1yp1Position.getState().isActive(territoryOrPlacedPlayer)) continue

                    val oppositePlayer = territoryOrPlacedPlayer.opposite()
                    val xp1yPosition = position.xp1y()
                    if (!xp1yPosition.getState().isActive(oppositePlayer)) continue

                    val xyp1Position = position.xyp1(realWidth)
                    if (!xyp1Position.getState().isActive(oppositePlayer)) continue

                    add(move.position)
                    add(xp1yp1Position)
                    add(xp1yPosition)
                    add(xyp1Position)
                }
            }
        }
    }
}