package org.dots.game

import org.dots.game.core.ClassSettings
import org.dots.game.core.Games
import org.dots.game.sgf.SgfWriter

data class CurrentGameSettings(
    var path: String?,
    var sgfContent: String?,
    var currentGameNumber: Int,
    var currentNodeNumber: Int,
) : ClassSettings<CurrentGameSettings>() {
    override val default: CurrentGameSettings
        get() = Default

    companion object {
        val Default = CurrentGameSettings(path = null, sgfContent = null, currentGameNumber = 0, currentNodeNumber = 0)
    }

    fun update(games: Games?): CurrentGameSettings {
        if (games != null) {
            sgfContent = SgfWriter.write(games)
            val currentGame = games.elementAtOrNull(currentGameNumber)
            if (currentGame != null) {
                currentNodeNumber = currentGame.gameTree.getCurrentNodeDepthFirstIndex()
            }
        }
        return this
    }
}