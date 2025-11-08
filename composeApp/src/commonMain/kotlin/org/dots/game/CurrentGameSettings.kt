package org.dots.game

import org.dots.game.core.ClassSettings

data class CurrentGameSettings(
    var path: String?,
    var content: String?,
    var currentGameNumber: Int,
    var currentNodeNumber: Int,
) : ClassSettings<CurrentGameSettings>() {
    override val default: CurrentGameSettings
        get() = Default

    companion object {
        val Default = CurrentGameSettings(path = null, content = null, currentGameNumber = 0, currentNodeNumber = 0)
    }
}