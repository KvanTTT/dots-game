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
        const val KATA_GO_DOTS_APP_NAME = "KataGoDots"

        private const val RESIGN_MOVE = "resign"
        private const val GROUND_MOVE = "ground"
        private const val PLAYER1_MARKER = "P1"
        private const val PLAYER2_MARKER = "P2"

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
                        logger(Diagnostic(initResponse.message, severity = DiagnosticSeverity.Critical))
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
                sendMessage("kata-set-param ${property.name} $intValue").message
            }
        }

        getOrSetParam(KataGoDotsSettings::maxTime)
        getOrSetParam(KataGoDotsSettings::maxVisits)
        getOrSetParam(KataGoDotsSettings::maxPlayouts)
    }

    actual suspend fun generateMove(field: Field, player: Player?): MoveInfo? {
        if (sync(field) == UnsupportedRules) return null

        val effectivePlayer = player ?: field.getCurrentPlayer()

        val response = sendMessage("genmove " + playerToGtp(effectivePlayer)).message
        return parseMoveInfo(response, field, effectivePlayer)
    }

    actual suspend fun sync(field: Field): SyncType {
        val rules = field.rules

        val syncType = getSyncType(field)
        logger(Diagnostic.info(syncType.toString()))

        if (syncType == FullSync) {
            require(!sendMessage("boardsize ${field.width}:${field.height}").isError)
            require(!sendMessage("kata-set-rule ${KataGoDotsExtraRules::dotsCaptureEmptyBase.name} ${rules.baseMode == BaseMode.AnySurrounding}").isError)
            require(!sendMessage("kata-set-rule suicide ${rules.suicideAllowed}").isError)
            require(!sendMessage("komi ${rules.komi}").isError)

            val startPosMovesPieces = mutableListOf<String>()
            val movesPieces =  mutableListOf<String>()

            for ((index, legalMove) in field.moveSequence.withIndex()) {
                val pieces = if (index < rules.initialMoves.size) {
                    startPosMovesPieces
                } else {
                    movesPieces
                }
                pieces.add(MoveInfo.fromLegalMove(legalMove, field).toGtpMove(field))
            }

            if (startPosMovesPieces.isNotEmpty()) {
                require(!sendMessage("set_position ${startPosMovesPieces.joinToString(" ")}").isError)
            }
            if (movesPieces.isNotEmpty()) {
                require(!sendMessage("play ${movesPieces.joinToString(" ")}").isError)
            }
        } else if (syncType is MovesSync) {
            if (syncType.undoMovesCount > 0) {
                require(!sendMessage("undo ${syncType.undoMovesCount}").isError)
            }

            if (syncType.moves.isNotEmpty()) {
                val command = buildString {
                    append("play ")
                    for (move in syncType.moves) {
                        append(move.toGtpMove(field))
                        append(" ")
                    }
                }

                require(!sendMessage(command).isError)
            }
        }

        return syncType
    }

    suspend fun getSyncType(field: Field): SyncType {
        val rules = field.rules

        if (rules.captureByBorder || rules.baseMode == BaseMode.AllOpponentDots) {
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
                KataGoDotsExtraRules::dotsCaptureEmptyBase.name -> {
                    val engineCaptureEmptyBase = value.toBoolean()
                    val isSame = when (rules.baseMode) {
                        BaseMode.AtLeastOneOpponentDot -> !engineCaptureEmptyBase
                        BaseMode.AnySurrounding -> engineCaptureEmptyBase
                        BaseMode.AllOpponentDots -> return UnsupportedRules
                    }
                    if (!isSame) {
                        return FullSync
                    }
                }
                "suicide" -> {
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
        if (rules.initialMoves.toSortedSet(MoveInfo.IgnoreParseNodeComparator) != startPositionMoves.toSortedSet(MoveInfo.IgnoreParseNodeComparator)) {
            return FullSync
        }

        val engineMoves = toMovesSequence(sendMessage("get_moves").message, field)

        val refinedMoves = field.moveSequence.drop(startPositionMoves.size).map {
            MoveInfo.fromLegalMove(it, field)
        }

        val minSize = minOf(refinedMoves.size, engineMoves.size)
        var firstDistinctIndex = minSize
        for (index in 0 until minSize) {
            if (MoveInfo.IgnoreParseNodeComparator.compare(refinedMoves[index], engineMoves[index]) != 0) {
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
}

data class Response(val message: String, val isError: Boolean, val extraLines: List<String> = emptyList()) {
    override fun toString(): String {
        return "Response: $message${if (isError) "; hasError" else ""}${if (extraLines.isNotEmpty()) "\n$extraLines" else ""}"
    }
}

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

            Response(
                lines.lastOrNull()?.removePrefix("= ")?.trim() ?: "",
                false,
                lines.takeIf { it.isNotEmpty() }?.take(lines.size - 1) ?: emptyList()
            )
        }
    } catch (e: Exception) {
        Response(e.message ?: "Error communicating with GTP engine", true)
    }.also {
        logger(Diagnostic.info(it.toString()))
        logger(Diagnostic.info(""))
    }
}