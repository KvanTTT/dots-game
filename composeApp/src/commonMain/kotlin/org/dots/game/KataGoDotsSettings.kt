package org.dots.game

data class KataGoDotsSettings(
    val exePath: String,
    val modelPath: String,
    val configPath: String,
    val maxTime: Int = 0,
    val maxVisits: Int = 0,
    val maxPlayouts: Int = 0,
    val logDir: String? = null,
    val autoMove: Boolean = false,
) {
    companion object {
        val Default: KataGoDotsSettings = KataGoDotsSettings(
            "", "", "",
            maxTime = 0, maxVisits = 0, maxPlayouts = 0,
            autoMove = false
        )
    }
}