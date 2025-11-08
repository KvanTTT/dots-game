package org.dots.game

import org.dots.game.core.ClassSettings

data class KataGoDotsSettings(
    val exePath: String,
    val modelPath: String,
    val configPath: String,
    val maxTime: Int = 0,
    val maxVisits: Int = 0,
    val maxPlayouts: Int = 0,
    val logDir: String? = null,
    val autoMove: Boolean = false,
) : ClassSettings<KataGoDotsSettings>() {
    override val default: KataGoDotsSettings
        get() = Default

    companion object {
        val Default: KataGoDotsSettings = KataGoDotsSettings(
            "", "", "",
            maxTime = 0, maxVisits = 0, maxPlayouts = 0,
            autoMove = false
        )
    }
}