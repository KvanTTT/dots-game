package org.dots.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.dots.game.core.*
import org.dots.game.core.Player
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.nio.file.Paths
import java.time.Duration
import kotlin.reflect.KProperty1

actual class KataGoDotsEngine private constructor(
    actual val settings: KataGoDotsSettings,
    val writer: OutputStreamWriter,
    val reader: BufferedReader,
    val errorReader: BufferedReader,
    actual val logger: (Diagnostic) -> Unit,
) {
    actual companion object {
        private const val DLL_NOT_FOUND_ERROR_CODE = -1073741515
        private const val ACCESS_VIOLATION_ERROR_CODE = -1073741819

        const val KATA_GO_DOTS_APP_NAME = "KataGoDots"

        private const val SEARCH_ANALYZE_COMMAND = "kata-search_analyze"
        private const val OWNERSHIP_OPTION_NAME = "ownership"
        private const val RESIGN_MOVE = "resign"
        private const val GROUND_MOVE = "ground"
        private const val PLAYER1_MARKER = "P1"
        private const val PLAYER2_MARKER = "P2"
        private const val SUICIDE_OPTION_NAME = "suicide"
        private const val CAPTURE_EMPTY_BASE_OPTION_NAME = "dotsCaptureEmptyBase"

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
                    val args = buildList {
                        add(kataGoDotsSettings.exePath)
                        add("gtp")
                        add("-model")
                        add(kataGoDotsSettings.modelPath)
                        add("-config")
                        add(kataGoDotsSettings.configPath)
                        // MacOS doesn't allow writing to a `user.home` directory without extra permissions, so don't use it for now
                        // Probably it makes sense to introduce logging to a custom directory.
                        // add("-override-config")
                        // add("${kataGoDotsSettings::logDir.name}=\"${kataGoDotsSettings.logDir ?: DEFAULT_LOGS_DIR}\"")
                    }

                    val processBuilder = ProcessBuilder(args).redirectErrorStream(true)

                    val process = processBuilder.start()

                    val writer = OutputStreamWriter(process.outputStream)
                    val reader = process.inputStream.bufferedReader()
                    val errorReader = process.errorStream.bufferedReader()

                    val initResponse = sendMessage("version", writer, reader, logger)
                    delay(Duration.ofMillis(500))

                    if (process.isAlive) {
                        initResponse.extraLines.forEach {
                            logger(Diagnostic(it, severity = DiagnosticSeverity.Info))
                        }

                        val nameResponse = sendMessage("name", writer, reader, logger)
                        if (nameResponse.message != KATA_GO_DOTS_APP_NAME) {
                            logger(
                                Diagnostic(
                                    "The engine should support Dots game mode (expected name is `$KATA_GO_DOTS_APP_NAME`, actual is `${nameResponse.message}`)",
                                    severity = DiagnosticSeverity.Error
                                )
                            )
                            return@withContext null
                        }
                    } else {
                        val errorMessage = when (val exitValue = process.exitValue()) {
                            DLL_NOT_FOUND_ERROR_CODE -> {
                                "Some of the following libraries are missing: 'zip.dll', 'zlib1.dll', 'bz2.dll', 'OpenCL.dll' or Microsoft Visual C++ Redistributable libraries. " +
                                        "Ensure they are present in the 'katago.exe' directory (or accessible via PATH)."
                            }
                            ACCESS_VIOLATION_ERROR_CODE -> {
                                "Access violation during engine initialization."
                            }
                            else -> {
                                "Error during engine initialization (error code: $exitValue)"
                            }
                        }
                        logger(Diagnostic(errorMessage, severity = DiagnosticSeverity.Critical))
                        return@withContext null
                    }

                    return@withContext KataGoDotsEngine(kataGoDotsSettings, writer, reader, errorReader, logger).also {
                        it.setUpSettings { diagnostic -> logger(diagnostic) }
                    }
                }
            } catch (e: Exception) {
                logger(Diagnostic(e.message ?: e.toString(), severity = DiagnosticSeverity.Critical))
                return null
            }
        }
    }

    suspend fun setUpSettings(onMessage: (Diagnostic) -> Unit) {
        suspend fun getOrSetParam(property: KProperty1<KataGoDotsSettings, Int>) {
            val intValue = property.get(settings)
            if (intValue == 0) {
                val message = "${property.name} = ${sendMessage("kata-get-param ${property.name}").message}"
                onMessage(Diagnostic(message, severity = DiagnosticSeverity.Info))
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

    actual suspend fun generateMove(field: Field, player: Player?): MoveInfo? {
        if (!sync(field).isSynchronized) return null

        val effectivePlayer = player ?: field.getCurrentPlayer()

        val response = sendMessage("genmove " + playerToGtp(effectivePlayer)).message
        return parseMoveInfo(response, field, effectivePlayer)
    }

    actual suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis? {
        if (!sync(field).isSynchronized) return null

        val effectivePlayer = player ?: field.getCurrentPlayer()

        val command = buildString {
            append(SEARCH_ANALYZE_COMMAND)
            append(' ')
            append(playerToGtp(effectivePlayer))
            if (withOwnership) {
                append(" $OWNERSHIP_OPTION_NAME true")
            }
        }

        val response = sendMessage(command)
        if (response.isError) return null

        return parseMoveAnalysis(response.allLines, effectivePlayer, field.width, field.height)
            .takeIf { it.moves.isNotEmpty() }
    }

    actual suspend fun sync(field: Field): SyncType {
        val rules = field.rules

        val syncType = getSyncType(field)
        logger(Diagnostic.info(syncType.toString()))

        if (syncType == FullSync) {
            if (!trySendMessage("boardsize ${field.width}:${field.height}")) return SyncFailed
            if (!trySendMessage("kata-set-rule $CAPTURE_EMPTY_BASE_OPTION_NAME ${rules.baseMode == BaseMode.AnySurrounding}")) return SyncFailed
            if (!trySendMessage("kata-set-rule $SUICIDE_OPTION_NAME ${rules.suicideAllowed}")) return SyncFailed
            if (!trySendMessage("komi ${rules.komi}")) return SyncFailed

            val startPosMovesPieces = mutableListOf<String>()
            val movesPieces =  mutableListOf<String>()

            for ((index, legalMove = value) in field.moveSequence.withIndex()) {
                val pieces = if (index < field.initialMovesCount) {
                    startPosMovesPieces
                } else {
                    movesPieces
                }
                pieces.add(MoveInfo.fromLegalMove(legalMove, field).toGtpMove(field))
            }

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

    suspend fun getSyncType(field: Field): SyncType {
        val rules = field.rules

        if (rules.captureByBorder || rules.baseMode == BaseMode.OnlyOpponentDots) {
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

        val refinedMoves = field.moveSequence.drop(field.initialMovesCount).map {
            MoveInfo.fromLegalMove(it, field)
        }

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
     * @return `null` if game is not yet completed, or it's a draw.
     * Currently, it's not a part of public API, however, it's useful for the engine testing.
     */
    suspend fun getGameResult(): GameResult? {
        val message = sendMessage("final_score").message

        if (message == "0") return null

        val pieces = message.split("+")
        val winner = parsePlayer(pieces[0])
        val score = pieces[1].toDouble()

        return if (score == 0.0) {
            GameResult.ResignWin(winner)
        } else {
            GameResult.ScoreWin(score, endGameKind = null, winner, player = null)
        }
    }

    /**
     * The moves the field treats as its start position.
     *
     * [Rules.initialMoves] alone is not enough: [Field.create] also places [Rules.remainingInitMoves],
     * that is the setup dots that don't fit the recognized [Rules.initPosType] pattern, and it skips
     * the initial moves it finds illegal. [Field.initialMovesCount] is the only count that is guaranteed
     * to match the beginning of [Field.moveSequence].
     */
    private fun Field.initialMoves(): List<MoveInfo> =
        moveSequence.take(initialMovesCount).map { MoveInfo.fromLegalMove(it, this) }

    private fun MoveInfo.toGtpMove(field: Field): String {
        return playerToGtp(player) + " " + when (externalFinishReason) {
            ExternalFinishReason.Grounding -> {
                GROUND_MOVE
            }
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

    private fun toMovesSequence(input: String, field: Field): List<MoveInfo> {
        if (input.isEmpty()) return emptyList()
        val pieces = input.split(" ")
        return buildList {
            for (i in pieces.indices step 2) {
                val player = parsePlayer(pieces[i])
                add(parseMoveInfo(pieces[i + 1], field, player))
            }
        }
    }

    private fun parseMoveInfo(string: String, field: Field, player: Player): MoveInfo {
        return when (string) {
            GROUND_MOVE -> {
                MoveInfo.createFinishingMove(player, ExternalFinishReason.Grounding)
            }
            RESIGN_MOVE -> {
                MoveInfo.createFinishingMove(player, ExternalFinishReason.Resign)
            }
            else -> {
                val dashIndex = string.indexOf('-')
                val x = string.take(dashIndex).toInt()
                val y = string.substring(dashIndex + 1, string.length).toInt()
                MoveInfo(PositionXY(x, field.height - y + 1), player)
            }
        }
    }

    private fun playerToGtp(player: Player): String {
        return when (player) {
            Player.First -> PLAYER1_MARKER
            Player.Second -> PLAYER2_MARKER
            else -> error("Unexpected player $player")
        }
    }

    private fun parsePlayer(str: String): Player {
        return when (str) {
            PLAYER1_MARKER -> Player.First
            PLAYER2_MARKER -> Player.Second
            else -> error("Unexpected GTP player `$str`")
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
        isError = markedLine?.startsWith(GTP_ERROR_MARKER) == true,
        extraLines = lines.dropLast(1),
    )
}

private fun String.hasGtpMarker(): Boolean = startsWith(GTP_SUCCESS_MARKER) || startsWith(GTP_ERROR_MARKER)

/** The app never sends a command id, thus a marker is never followed by one. */
private fun String.removeGtpMarker(): String = (if (hasGtpMarker()) drop(1) else this).trim()

private suspend fun sendMessage(command: String, writer: OutputStreamWriter, reader: BufferedReader, logger: (Diagnostic) -> Unit): Response {
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