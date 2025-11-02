package org.dots.game

data class KataGoDotsSettings(
    val exePath: String,
    val modelPath: String,
    val configPath: String,
) {
    companion object {
        val Default: KataGoDotsSettings = KataGoDotsSettings("", "", "")
    }
}