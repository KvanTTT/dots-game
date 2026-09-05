package org.dots.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.IgnoreParseNodeComparator
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.equalsIgnoringParseNode
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.time.Duration
import kotlin.reflect.KProperty1

/**
 * Talks to `katago gtp`: a command is a line of text and its response is one or several lines of text,
 * matched to the command by their order alone.
 *
 * The engine keeps a position of its own, and a command applies to that position rather than to one it
 * carries, so the engine is synchronized with the field before every command, see [sync]. That is also why
 * a command exchange is never interrupted and never interleaves with another one: an unread part of
 * a response would be taken for the response of the next command, and a half-applied synchronization
 * would leave the engine on a position of its own.
 */
internal class GtpProtocol private constructor(
    settings: KataGoDotsSettings,
    process: Process,
    writer: OutputStreamWriter,
    private val reader: BufferedReader,
    logger: (Diagnostic) -> Unit,
) : KataGoDotsProtocol(settings, process, writer, logger) {
    companion object {
        private const val GTP_COMMAND = "gtp"
        private const val SEARCH_ANALYZE_COMMAND = "kata-search_analyze"
        private const val OWNERSHIP_OPTION_NAME = "ownership"
        private const val SUICIDE_OPTION_NAME = "suicide"
        private const val CAPTURE_EMPTY_BASE_OPTION_NAME = "dotsCaptureEmptyBase"

        suspend fun initialize(settings: KataGoDotsSettings, logger: (Diagnostic) -> Unit): GtpProtocol? {
            // A response is looked up among the lines the engine writes, the ones of its log included
            val process = startProcess(settings, GTP_COMMAND, mergeErrorStream = true)

            val writer = OutputStreamWriter(process.outputStream)
            val reader = process.inputStream.bufferedReader()

            val initResponse = sendMessage("version", writer, reader, logger)
            // The engine dies on a broken setup rather than on the command itself, thus it's given a moment
            delay(Duration.ofMillis(500))

            if (!process.isAlive) {
                logger(
                    Diagnostic(
                        startupFailureMessage(process, initResponse.allLines.lastOrNull()),
                        severity = DiagnosticSeverity.Critical,
                    )
                )
                return null
            }

            initResponse.extraLines.forEach {
                logger(Diagnostic(it, severity = DiagnosticSeverity.Info))
            }

            val nameResponse = sendMessage("name", writer, reader, logger)
            if (nameResponse.message != KataGoDotsEngine.KATA_GO_DOTS_APP_NAME) {
                logger(
                    Diagnostic(
                        "The engine should support Dots game mode (expected name is `${KataGoDotsEngine.KATA_GO_DOTS_APP_NAME}`, actual is `${nameResponse.message}`)",
                        severity = DiagnosticSeverity.Error
                    )
                )
                process.destroy()
                return null
            }

            return GtpProtocol(settings, process, writer, reader, logger).also { it.setUpSettings() }
        }
    }

    /** A single stream carries every response, thus the exchanges must not interleave, see [GtpProtocol]. */
    private val commandMutex = Mutex()

    /**
     * The limits of a search are set once, because the engine keeps them along with the rest of its state.
     * The analysis protocol sends them with every query instead.
     */
    private suspend fun setUpSettings() {
        suspend fun getOrSetParam(property: KProperty1<KataGoDotsSettings, Int>) {
            val intValue = property.get(settings)
            if (intValue == 0) {
                val message = "${property.name} = ${sendMessage("kata-get-param ${property.name}").message}"
                logger(Diagnostic(message, severity = DiagnosticSeverity.Info))
            } else {
                // A rejected parameter is reported by `trySendMessage`, and the engine stays usable
                // with the default value of that parameter, so it must not fail the initialization
                val _ = trySendMessage("kata-set-param ${property.name} $intValue")
            }
        }

        getOrSetParam(KataGoDotsSettings::maxTime)
        getOrSetParam(KataGoDotsSettings::maxVisits)
        getOrSetParam(KataGoDotsSettings::maxPlayouts)
    }

    override suspend fun generateMove(field: Field, player: Player?): MoveInfo? = onSynchronizedPosition(field) {
        val effectivePlayer = player ?: field.getCurrentPlayer()

        val response = sendMessage("genmove ${effectivePlayer.toEngineMarker()}")
        if (response.isError) return@onSynchronizedPosition null

        parseEngineMove(response.message, effectivePlayer, field.width, field.height)
    }

    override suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis? =
        onSynchronizedPosition(field) {
            val effectivePlayer = player ?: field.getCurrentPlayer()

            val command = buildString {
                append(SEARCH_ANALYZE_COMMAND)
                append(' ')
                append(effectivePlayer.toEngineMarker())
                if (withOwnership) {
                    append(" $OWNERSHIP_OPTION_NAME true")
                }
            }

            val response = sendMessage(command)
            if (response.isError) return@onSynchronizedPosition null

            parseGtpMoveAnalysis(response.allLines, effectivePlayer, field.width, field.height)
                .takeIf { it.moves.isNotEmpty() }
        }

    /**
     * Synchronizes the engine with [field] and runs [command] on the position it ends up with.
     *
     * The whole exchange is uninterruptible: a cancelled command would leave its response in the stream,
     * and every following command would then read the response of the previous one.
     *
     * @return `null` if the rules are unsupported or the synchronization was rejected.
     */
    private suspend fun <T> onSynchronizedPosition(field: Field, command: suspend () -> T?): T? =
        commandMutex.withLock {
            withContext(NonCancellable) {
                if (!doSync(field).isSynchronized) return@withContext null

                command()
            }
        }

    /**
     * Brings the engine to the position of [field], replaying as little of it as possible:
     * a position that only grew is caught up with by the moves that are missing.
     *
     * [generateMove] and [analyze] synchronize on their own, so this is only needed to prepare a position
     * no command of the app follows, which is what the tests of the synchronization use it for.
     */
    suspend fun sync(field: Field): SyncType = commandMutex.withLock {
        withContext(NonCancellable) { doSync(field) }
    }

    /** @see sync */
    suspend fun getSyncType(field: Field): SyncType = commandMutex.withLock {
        withContext(NonCancellable) { computeSyncType(field) }
    }

    private suspend fun doSync(field: Field): SyncType {
        val rules = field.rules

        val syncType = computeSyncType(field)
        logger(Diagnostic.info(syncType.toString()))

        if (syncType == FullSync) {
            if (!trySendMessage("boardsize ${field.width}:${field.height}")) return SyncFailed
            if (!trySendMessage("kata-set-rule $CAPTURE_EMPTY_BASE_OPTION_NAME ${rules.baseMode == BaseMode.AnySurrounding}")) return SyncFailed
            if (!trySendMessage("kata-set-rule $SUICIDE_OPTION_NAME ${rules.suicideAllowed}")) return SyncFailed
            if (!trySendMessage("komi ${rules.komi}")) return SyncFailed

            val startPosMovesPieces = field.initialMoves().map { it.toGtpMove(field) }
            val movesPieces = field.playedMoves().map { it.toGtpMove(field) }

            /**
             * `set_position` is sent even without moves, because it's the only way to drop the start position
             * the engine installs on its own: both `boardsize` and `clear_board` restore the one
             * of the `startPos` config option (`CROSS` by default) instead of clearing the board.
             */
            if (!trySendMessage("set_position ${startPosMovesPieces.joinToString(" ")}".trimEnd())) return SyncFailed

            if (movesPieces.isNotEmpty()) {
                if (!trySendMessage("play ${movesPieces.joinToString(" ")}")) return SyncFailed
            }
        } else if (syncType is MovesSync) {
            if (syncType.undoMovesCount > 0) {
                if (!trySendMessage("undo ${syncType.undoMovesCount}")) return SyncFailed
            }

            if (syncType.moves.isNotEmpty()) {
                val command = buildString {
                    append("play ")
                    for (move in syncType.moves) {
                        append(move.toGtpMove(field))
                        append(" ")
                    }
                }

                if (!trySendMessage(command)) return SyncFailed
            }
        }

        return syncType
    }

    private suspend fun computeSyncType(field: Field): SyncType {
        val rules = field.rules

        if (!doesKataSupportRules(rules)) {
            return UnsupportedRules
        }

        val boardsizeResponse = sendMessage("get_boardsize")

        val pieces = boardsizeResponse.message.split(":")
        require(pieces.size.let { it == 1 || it == 2 })
        val width: Int = pieces[0].toInt()
        val height: Int = if (pieces.size == 1) {
            width
        } else {
            pieces[1].toInt()
        }

        if (width != field.width || height != field.height) {
            return FullSync
        }

        val rulesResponse = sendMessage("kata-get-rules")
        val keyValuePairs = rulesResponse.message.removeSurrounding("{", "}").split(",")
        for (keyValuePair in keyValuePairs) {
            val keyValuePairPieces = keyValuePair.split(":")
            val key = keyValuePairPieces[0].removeSurrounding("\"")
            val value = keyValuePairPieces[1].removeSurrounding("\"")

            when (key) {
                "dots" -> {
                    require(value.toBoolean())
                }
                CAPTURE_EMPTY_BASE_OPTION_NAME -> {
                    val engineCaptureEmptyBase = value.toBoolean()
                    val isSame = when (rules.baseMode) {
                        BaseMode.AtLeastOneOpponentDot -> !engineCaptureEmptyBase
                        BaseMode.AnySurrounding -> engineCaptureEmptyBase
                        BaseMode.OnlyOpponentDots -> return UnsupportedRules
                    }
                    if (!isSame) {
                        return FullSync
                    }
                }
                SUICIDE_OPTION_NAME -> {
                    if (rules.suicideAllowed != value.toBoolean()) {
                        return FullSync
                    }
                }
            }
        }

        val engineKomi = sendMessage("get_komi").message.toDouble()
        if (rules.komi != engineKomi) {
            return FullSync
        }

        val startPositionMoves = toMovesSequence(sendMessage("get_position").message, field)

        // The order of start moves doesn't matter
        if (field.initialMoves().toSortedSet(IgnoreParseNodeComparator) != startPositionMoves.toSortedSet(IgnoreParseNodeComparator)) {
            return FullSync
        }

        val engineMoves = toMovesSequence(sendMessage("get_moves").message, field)
        val refinedMoves = field.playedMoves()

        val minSize = minOf(refinedMoves.size, engineMoves.size)
        var firstDistinctIndex = minSize
        for (index in 0 until minSize) {
            if (!refinedMoves[index].equalsIgnoringParseNode(engineMoves[index])) {
                firstDistinctIndex = index
                break
            }
        }

        val undoMovesCount = engineMoves.size - firstDistinctIndex
        val newMoves = refinedMoves.drop(firstDistinctIndex)

        return if (undoMovesCount > 0 || newMoves.isNotEmpty())
            MovesSync(undoMovesCount, newMoves)
        else
            NoSync
    }

    /**
     * The score of the position of the engine, the way `final_score` reports it: it scores the position
     * as it stands rather than only a game that is over, so it tells nothing about whether the game
     * is finished, and a `0` of an unfinished game is a tie of the estimate rather than a missing result.
     *
     * Currently, it's not a part of public API, however, it's useful for the engine testing.
     *
     * @return `null` if neither player leads.
     */
    suspend fun getGameResult(): GameResult? = commandMutex.withLock {
        withContext(NonCancellable) {
            val message = sendMessage("final_score").message

            if (message == "0") return@withContext null

            val pieces = message.split("+")
            val winner = pieces[0].toPlayerOrNull() ?: error("Unexpected GTP player `${pieces[0]}`")
            val score = pieces[1].toDouble()

            if (score == 0.0) {
                GameResult.ResignWin(winner)
            } else {
                GameResult.ScoreWin(score, endGameKind = null, winner, player = null)
            }
        }
    }

    /** A GTP move is the player it's made by followed by the move itself. */
    private fun MoveInfo.toGtpMove(field: Field): String = player.toEngineMarker() + " " + toEngineMove(field)

    private fun toMovesSequence(input: String, field: Field): List<MoveInfo> {
        if (input.isEmpty()) return emptyList()
        val pieces = input.split(" ")
        return buildList {
            for (i in pieces.indices step 2) {
                val player = pieces[i].toPlayerOrNull() ?: error("Unexpected GTP player `${pieces[i]}`")
                add(
                    parseEngineMove(pieces[i + 1], player, field.width, field.height)
                        ?: error("Unexpected GTP move `${pieces[i + 1]}`")
                )
            }
        }
    }

    private suspend fun sendMessage(message: String): Response = sendMessage(message, writer, reader, logger)

    /**
     * Sends [command] and reports its rejection to [logger] instead of throwing, because a command
     * rejected in the middle of a synchronization would otherwise take the whole app down.
     *
     * @return `false` if the engine rejected the command.
     */
    private suspend fun trySendMessage(command: String): Boolean {
        val response = sendMessage(command)
        if (response.isError) {
            logger(
                Diagnostic(
                    "The engine rejected `${command.trimMessageIfNecessary()}`: ${response.message}",
                    severity = DiagnosticSeverity.Error,
                )
            )
            return false
        }
        return true
    }
}

/** How much of the position of the engine has to be replayed to bring it to the one of the field. */
sealed class SyncType {
    /**
     * `false` if the engine position doesn't match the field one, thus no command may rely on it.
     */
    val isSynchronized: Boolean
        get() = this != UnsupportedRules && this != SyncFailed

    override fun toString(): String {
        return buildString {
            append("SyncType: ${this@SyncType::class.simpleName}")
            if (this@SyncType is MovesSync) {
                if (this@SyncType.undoMovesCount > 0) {
                    append("; undo: -${this@SyncType.undoMovesCount}")
                }
                if (this@SyncType.moves.isNotEmpty()) {
                    append("; moves: +${moves.size}")
                }
            }
        }
    }
}

object FullSync : SyncType()

class MovesSync(val undoMovesCount: Int, val moves: List<MoveInfo>) : SyncType()

object NoSync : SyncType()

object UnsupportedRules : SyncType()

/**
 * The engine rejected one of the synchronization commands, the reason is reported to
 * [KataGoDotsEngine.logger]. The position of the engine is undefined afterwards, and it's recovered
 * by the next [GtpProtocol.sync] that detects the mismatch and resynchronizes from scratch.
 */
object SyncFailed : SyncType()

data class Response(val message: String, val isError: Boolean, val extraLines: List<String> = emptyList()) {
    /**
     * The whole engine response, [message] being its last line.
     * Multiline responses are produced by the analysis commands.
     */
    val allLines: List<String> get() = extraLines + message

    override fun toString(): String {
        return "Response: $message${if (isError) "; hasError" else ""}${if (extraLines.isNotEmpty()) "\n$extraLines" else ""}"
    }
}

private const val GTP_SUCCESS_MARKER = '='
private const val GTP_ERROR_MARKER = '?'

/**
 * Builds a [Response] out of the raw engine output.
 *
 * A GTP response is marked with `=` when the command succeeded and with `?` when it failed.
 * The marked line is neither necessarily the first one (the engine writes its warnings into the same stream,
 * see `redirectErrorStream`) nor necessarily the last one (the analysis commands answer with several lines),
 * so it's looked up explicitly.
 */
internal fun toGtpResponse(lines: List<String>): Response {
    val markedLine = lines.firstOrNull { it.hasGtpMarker() }

    return Response(
        message = lines.lastOrNull()?.removeGtpMarker() ?: "",
        isError = markedLine == null || markedLine.startsWith(GTP_ERROR_MARKER),
        extraLines = lines.dropLast(1),
    )
}

private fun String.hasGtpMarker(): Boolean = startsWith(GTP_SUCCESS_MARKER) || startsWith(GTP_ERROR_MARKER)

/** The app never sends a command id, thus a marker is never followed by one. */
private fun String.removeGtpMarker(): String = (if (hasGtpMarker()) drop(1) else this).trim()

private suspend fun sendMessage(
    command: String,
    writer: OutputStreamWriter,
    reader: BufferedReader,
    logger: (Diagnostic) -> Unit,
): Response {
    return try {
        withContext(Dispatchers.IO) {
            writer.write(command + "\n")
            writer.flush()

            logger(Diagnostic.info("Command: $command"))

            val channel = Channel<String>(UNLIMITED)

            launch(Dispatchers.IO) {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break // GTP responses are separated by a blank line
                    channel.send(line)
                }
                channel.close()
            }

            val lines = mutableListOf<String>()

            // Perform non-blocking awaiting
            withTimeout(Duration.ofSeconds(100)) {
                channel.consumeEach {
                    lines.add(it)
                }
            }

            toGtpResponse(lines)
        }
    } catch (e: Exception) {
        Response(e.message ?: "Error communicating with GTP engine", true)
    }.also {
        logger(Diagnostic.info(it.toString()))
        logger(Diagnostic.info(""))
    }
}
