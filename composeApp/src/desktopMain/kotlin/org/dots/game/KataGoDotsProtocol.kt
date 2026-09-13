package org.dots.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.IllegalMove
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import java.io.OutputStreamWriter

/**
 * The way the app talks to the engine process, see [KataGoDotsEngine] for what it is asked for.
 *
 * Everything that differs between the protocols stays here: what a request looks like, whether the engine
 * keeps a position of its own and whether several requests may be searched at once. What is common is
 * the process itself, the moves that are sent to it and the analysis that is parsed out of its answers.
 */
internal abstract class KataGoDotsProtocol(
    val settings: KataGoDotsSettings,
    protected val process: Process,
    protected val writer: OutputStreamWriter,
    val logger: (Diagnostic) -> Unit,
) {
    companion object {
        private const val DLL_NOT_FOUND_ERROR_CODE = -1073741515
        private const val ACCESS_VIOLATION_ERROR_CODE = -1073741819

        /**
         * Starts the engine in [mode], which is the subcommand of its protocol (`analysis` or `gtp`).
         *
         * @param mergeErrorStream lets the log of the engine into the stream its responses are read from,
         * which is what the GTP protocol expects: a response is looked up among the lines the engine writes.
         * The analysis protocol keeps the streams apart instead, because it reads the startup log
         * of the engine along with the responses rather than after them.
         */
        fun startProcess(settings: KataGoDotsSettings, mode: String, mergeErrorStream: Boolean): Process =
            ProcessBuilder(
                settings.exePath,
                mode,
                "-model", settings.modelPath,
                "-config", settings.configPath,
                // MacOS doesn't allow writing to a `user.home` directory without extra permissions, so don't use it for now.
                // Probably it makes sense to introduce logging to a custom directory:
                // "-override-config", "${settings::logDir.name}=\"${settings.logDir ?: DEFAULT_LOGS_DIR}\"",
            ).redirectErrorStream(mergeErrorStream).start()

        /** The reason a process that is no longer alive gives for a setup that doesn't work. */
        fun startupFailureMessage(process: Process, lastLogLine: String?): String {
            if (process.isAlive) return "The engine stopped answering during the initialization"

            val reason = when (val exitValue = process.exitValue()) {
                DLL_NOT_FOUND_ERROR_CODE -> {
                    "Some of the following libraries are missing: 'zip.dll', 'zlib1.dll', 'bz2.dll', 'OpenCL.dll' or Microsoft Visual C++ Redistributable libraries. " +
                            "Ensure they are present in the 'katago.exe' directory (or accessible via PATH)."
                }
                ACCESS_VIOLATION_ERROR_CODE -> "Access violation during engine initialization."
                else -> "Error during engine initialization (error code: $exitValue)"
            }

            return lastLogLine?.let { "$reason: ${it.trimMessageIfNecessary()}" } ?: reason
        }
    }

    /** The requests are sent by several coroutines at once, and a line of one must not be split by another. */
    private val writeMutex = Mutex()

    /** @see KataGoDotsEngine.generateMove */
    abstract suspend fun generateMove(field: Field, player: Player?, clock: PlayerClock?): MoveInfo?

    /** @see KataGoDotsEngine.analyze */
    abstract suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis?

    /**
     * Analyzes the turns of a game one by one, replaying them on a field of its own, which is what any
     * protocol is able to do. The analysis engine is asked for the whole game at once instead,
     * see [AnalysisProtocol.analyzeGame].
     *
     * @see KataGoDotsEngine.analyzeGame
     */
    open suspend fun analyzeGame(
        field: Field,
        moves: List<MoveInfo>,
        turnNumbers: List<Int>,
        onTurnAnalyzed: (turnNumber: Int, analysis: MoveAnalysis) -> Unit,
    ) {
        if (!doesKataSupportRules(field.rules) || turnNumbers.isEmpty()) return

        // The turns are replayed on a field of their own, because the field of the app is the position
        // the user plays on and it keeps changing while a whole game is being analyzed
        val replayedField = Field.create(field.rules)

        for (turnNumber in turnNumbers.sorted()) {
            while (replayedField.currentMoveNumber < turnNumber) {
                val move = moves.getOrNull(replayedField.currentMoveNumber) ?: return
                if (replayedField.makeMove(move) is IllegalMove) return
            }

            // Every turn is analyzed for the player whose turn it is rather than for a single one
            analyze(replayedField, player = null, withOwnership = false)?.let { onTurnAnalyzed(turnNumber, it) }
        }
    }

    /** Stops the engine process, the protocol is unusable afterwards. */
    open fun close() {
        process.destroy()
    }

    protected suspend fun writeLine(line: String) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    /**
     * The moves the field treats as its start position.
     *
     * [org.dots.game.core.Rules.initialMoves] alone is not enough: [Field.create] also places
     * `remainingInitMoves`, that is the setup dots that don't fit the recognized `initPosType` pattern,
     * and it skips the initial moves it finds illegal. [Field.initialMovesCount] is the only count that is
     * guaranteed to match the beginning of [Field.moveSequence].
     */
    protected fun Field.initialMoves(): List<MoveInfo> =
        moveSequence.take(initialMovesCount).map { MoveInfo.fromLegalMove(it, this) }

    /** The moves that are played on top of the start position, see [initialMoves]. */
    protected fun Field.playedMoves(): List<MoveInfo> =
        moveSequence.drop(initialMovesCount).map { MoveInfo.fromLegalMove(it, this) }

    /**
     * The vertical axis is inverted (the engine counts it from the bottom),
     * the same way as in [parseEnginePosition].
     */
    protected fun MoveInfo.toEngineMove(field: Field): String {
        return when (externalFinishReason) {
            ExternalFinishReason.Grounding -> GROUND_MOVE
            ExternalFinishReason.Resign,
            ExternalFinishReason.Time,
            ExternalFinishReason.Interrupt,
            ExternalFinishReason.Unknown -> {
                // KataGoDots supports only `resign` failing move
                RESIGN_MOVE
            }
            else -> {
                val (x, y) = positionXY!!
                "${x}-${field.height - y + 1}"
            }
        }
    }
}
