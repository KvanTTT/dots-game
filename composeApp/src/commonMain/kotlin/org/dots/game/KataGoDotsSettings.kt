package org.dots.game

import org.dots.game.core.ClassSettings

/**
 * The protocol the engine is talked to by, see [KataGoDotsEngine]. Both of them answer the very same
 * questions, so the app works the same way no matter which one is picked; the config file of the engine,
 * however, belongs to the mode it's run in.
 */
enum class EngineProtocol {
    /**
     * `katago analysis`: a JSON query is answered with a JSON response, and every query carries the whole
     * position, so the queries are independent of each other and of the engine state.
     */
    Analysis,

    /**
     * `katago gtp`: a text command is answered with a text response, and the engine keeps the position
     * the commands are applied to, so it has to be synchronized with the field, see `SyncType`.
     */
    Gtp,
}

data class KataGoDotsSettings(
    val exePath: String,
    val modelPath: String,
    val configPath: String,
    val maxTime: Int = 0,
    val maxVisits: Int = 0,
    val maxPlayouts: Int = 0,
    val logDir: String? = null,
    val autoMove: Boolean = false,
    /**
     * The app plays with the analysis engine; the GTP one is kept to test the engine against it,
     * see `GtpEngineTests`.
     */
    val protocol: EngineProtocol = EngineProtocol.Analysis,
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
