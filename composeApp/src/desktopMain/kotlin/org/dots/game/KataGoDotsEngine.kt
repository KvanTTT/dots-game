package org.dots.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import java.nio.file.Paths

/**
 * Runs KataGoDots as a process of its own and asks it the questions of the app, no matter which
 * [EngineProtocol] the process is talked to by: the protocol itself is [KataGoDotsProtocol].
 */
actual class KataGoDotsEngine internal constructor(private val protocol: KataGoDotsProtocol) {
    actual companion object {
        const val KATA_GO_DOTS_APP_NAME = "KataGoDots"

        val DEFAULT_KATA_GO_DOTS_DIR: String = Paths.get(System.getProperty("user.dir"), "src/desktopMain/resources/$KATA_GO_DOTS_APP_NAME").toString()

        // TODO: Add defaults later
        val DEFAULT_CONFIG: String = Paths.get(DEFAULT_KATA_GO_DOTS_DIR, "default_config.cfg").toString()
        val DEFAULT_MODEL: String = Paths.get(DEFAULT_KATA_GO_DOTS_DIR, "default_model.bin.gz").toString()
        val DEFAULT_EXE: String = Paths.get(DEFAULT_KATA_GO_DOTS_DIR, "KataGoDots.exe").toString()

        val DEFAULT_LOGS_DIR: String = Paths.get(System.getProperty("user.home"), KATA_GO_DOTS_APP_NAME).toString()

        actual const val IS_SUPPORTED = true

        actual suspend fun initialize(kataGoDotsSettings: KataGoDotsSettings, logger: (Diagnostic) -> Unit): KataGoDotsEngine? {
            if (kataGoDotsSettings.exePath.isEmpty()) {
                return null
            }

            try {
                return withContext(Dispatchers.IO) {
                    val protocol = when (kataGoDotsSettings.protocol) {
                        EngineProtocol.Analysis -> AnalysisProtocol.initialize(kataGoDotsSettings, logger)
                        EngineProtocol.Gtp -> GtpProtocol.initialize(kataGoDotsSettings, logger)
                    }

                    protocol?.let { KataGoDotsEngine(it) }
                }
            } catch (e: Exception) {
                logger(Diagnostic(e.message ?: e.toString(), severity = DiagnosticSeverity.Critical))
                return null
            }
        }
    }

    actual val settings: KataGoDotsSettings
        get() = protocol.settings

    actual val logger: (Diagnostic) -> Unit
        get() = protocol.logger

    /**
     * The GTP engine keeps a position of its own, which is not a part of the API of the app but is what
     * the tests of the synchronization address it by, see [GtpProtocol.sync].
     */
    internal val gtp: GtpProtocol?
        get() = protocol as? GtpProtocol

    actual suspend fun generateMove(field: Field, player: Player?, clock: PlayerClock?): MoveInfo? =
        protocol.generateMove(field, player, clock)

    actual suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis? =
        protocol.analyze(field, player, withOwnership)

    actual suspend fun analyzeGame(
        field: Field,
        moves: List<MoveInfo>,
        turnNumbers: List<Int>,
        onTurnAnalyzed: (turnNumber: Int, analysis: MoveAnalysis) -> Unit,
    ) = protocol.analyzeGame(field, moves, turnNumbers, onTurnAnalyzed)

    actual fun close() = protocol.close()
}
