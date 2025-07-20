package org.dots.game.core.features

import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.createPlacedState

fun Field.getCrosses(): List<Position> {
    return buildList {
        for (move in moveSequence) {
            val position = move.position
            with(this) {
                val state = position.getState()

                val territoryOrPlacedPlayer = state.getTerritoryOrPlacedPlayer()
                if (territoryOrPlacedPlayer != Player.None) {
                    val playerPlaced = territoryOrPlacedPlayer.createPlacedState()

                    val (x, y) = position
                    val xp1yp1Position = Position(x + 1, y + 1)
                    if (!xp1yp1Position.getState().checkActive(playerPlaced)) continue

                    val oppositePlayer = territoryOrPlacedPlayer.opposite()
                    val oppositePlayerPlaced = oppositePlayer.createPlacedState()
                    val xp1yPosition = Position(x + 1, y)
                    if (!xp1yPosition.getState().checkActive(oppositePlayerPlaced)) continue

                    val xyp1Position = Position(x, y + 1)
                    if (!xyp1Position.getState().checkActive(oppositePlayerPlaced)) continue

                    add(move.position)
                    add(xp1yp1Position)
                    add(xp1yPosition)
                    add(xyp1Position)
                }
            }
        }
    }
}