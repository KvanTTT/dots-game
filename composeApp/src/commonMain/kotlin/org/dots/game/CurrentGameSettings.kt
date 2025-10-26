package org.dots.game

data class CurrentGameSettings(
    var path: String?,
    var content: String?,
    var currentGameNumber: Int,
    var currentNodeNumber: Int,
) {
    companion object {
        val Default = CurrentGameSettings(path = null, content = null, currentGameNumber = 0, currentNodeNumber = 0)
    }
}