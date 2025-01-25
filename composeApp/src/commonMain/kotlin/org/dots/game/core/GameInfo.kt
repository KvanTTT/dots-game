package org.dots.game.core

class GameInfo(
    val gameName: String?,
    val player1Name: String?,
    val player1Rating: Double?,
    val player1Team: String?,
    val player2Name: String?,
    val player2Rating: Double?,
    val player2Team: String?,
    val komi: Double?,
    val date: String?,
    val description: String?,
    val comment: String?,
    val place: String?,
    val event: String?,
    val opening: String?,
    val annotator: String?,
    val copyright: String?,
    val source: String?,
    val time: Double?,
    val overtime: String?,
    val appInfo: AppInfo?,
    val rules: Rules,
)

data class AppInfo(val name: String, val version: String?)