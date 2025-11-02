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
import java.time.Duration

data class Response(val message: String, val isError: Boolean, val extraLines: List<String> = emptyList())

private suspend fun sendMessage(command: String, writer: OutputStreamWriter, reader: BufferedReader): Response {
    return try {
        withContext(Dispatchers.IO) {
            writer.write(command + "\n")
            writer.flush()

            println("Command: $command")

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
        println(it)
        println()
    }
}

actual class KataGoDotsEngine private constructor(
    kataGoDotsSettings: KataGoDotsSettings,
    val writer: OutputStreamWriter,
    val reader: BufferedReader,
    val errorReader: BufferedReader,
) {
    actual companion object {
        const val KATA_GO_DOTS_APP_NAME = "KataGoDots"
        private const val RESIGN_MOVE = "resign"
        private const val GROUND_MOVE = "ground"
        private const val PLAYER1_MARKER = "P1"
        private const val PLAYER2_MARKER = "P2"

        actual suspend fun initialize(kataGoDotsSettings: KataGoDotsSettings, onMessage: (Diagnostic) -> Unit): KataGoDotsEngine? {
            return try {
                withContext(Dispatchers.IO) {
                    val processBuilder = ProcessBuilder(
                        kataGoDotsSettings.exePath,
                        "gtp",
                        "-model",
                        kataGoDotsSettings.modelPath,
                        "-config",
                        kataGoDotsSettings.configPath
                    ).redirectErrorStream(true)

                    val process = processBuilder.start()

                    val writer = OutputStreamWriter(process.outputStream)
                    val reader = process.inputStream.bufferedReader()
                    val errorReader = process.errorStream.bufferedReader()

                    val initResponse = sendMessage("version", writer, reader)
                    delay(Duration.ofMillis(500))

                    if (process.isAlive) {
                        initResponse.extraLines.forEach {
                            onMessage(Diagnostic(it, severity = DiagnosticSeverity.Info))
                        }

                        val nameResponse = sendMessage("name", writer, reader)
                        if (nameResponse.message != KATA_GO_DOTS_APP_NAME) {
                            onMessage(
                                Diagnostic(
                                    "The engine should support Dots game mode (expected name is `$KATA_GO_DOTS_APP_NAME`, actual is `${nameResponse.message}`)",
                                    severity = DiagnosticSeverity.Error
                                )
                            )
                        }
                    } else {
                        onMessage(Diagnostic(initResponse.message, severity = DiagnosticSeverity.Critical))
                    }

                    KataGoDotsEngine(kataGoDotsSettings, writer, reader, errorReader)
                }
            } catch (e: Exception) {
                onMessage(Diagnostic(e.message ?: e.toString(), severity = DiagnosticSeverity.Critical))
                null
            }
        }
    }

    actual val settings = kataGoDotsSettings

    actual suspend fun generateMove(player: Player, field: Field): MoveInfo? {
        sync(field)

        val response =
            sendMessage("genmove " + if (player == Player.First) "P1" else "P2", writer, reader).message
        return parseMoveInfo(response, field, player)
    }

    actual suspend fun sync(field: Field) {
        val rules = field.rules

        val syncType = getSyncType(field)

        if (syncType == FullSync) {
            require(!sendMessage("boardsize ${field.width}:${field.height}").isError)
            require(!sendMessage("kata-set-rule ${KataGoDotsRules::dotsCaptureEmptyBase.name} ${rules.baseMode == BaseMode.AnySurrounding}").isError)
            require(!sendMessage("kata-set-rule suicide ${rules.suicideAllowed}").isError)
            require(!sendMessage("komi ${rules.komi}").isError)

            val startPosMoves = StringBuilder().append("set_position ")
            val moves = StringBuilder().append("play ")

            for ((index, legalMove) in field.moveSequence.withIndex()) {
                val builder = if (index < rules.initialMoves.size) {
                    startPosMoves
                } else {
                    moves
                }
                builder.append(legalMove.toGtpMove(field))
                builder.append(" ")
            }

            require(!sendMessage(startPosMoves.toString()).isError)
            require(!sendMessage(moves.toString()).isError)
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
    }

    sealed class SyncType

    object FullSync : SyncType()

    class MovesSync(val undoMovesCount: Int, val moves: List<MoveInfo>) : SyncType()

    suspend fun getSyncType(field: Field): SyncType {
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

        val rules = field.rules

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
                KataGoDotsRules::dotsCaptureEmptyBase.name -> {
                    val engineCaptureEmptyBase = value.toBoolean()
                    val isSame = when (rules.baseMode) {
                        BaseMode.AtLeastOneOpponentDot -> !engineCaptureEmptyBase
                        BaseMode.AnySurrounding -> engineCaptureEmptyBase
                        BaseMode.AllOpponentDots -> error("Unsupported")
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

        require(!rules.captureByBorder)

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
            MoveInfo(it.position.toXY(field.realWidth), it.player)
        }

        val minSize = minOf(refinedMoves.size, engineMoves.size)
        var firstDistinctIndex = minSize
        for (index in 0 until minSize) {
            if (refinedMoves[index] != engineMoves[index]) {
                firstDistinctIndex = index
                break
            }
        }

        return MovesSync(engineMoves.size - firstDistinctIndex, refinedMoves.drop(firstDistinctIndex))
    }

    private fun LegalMove.toGtpMove(field: Field): String {
        val externalFinishReason = (this as? GameResult)?.toExternalFinishReason()
        val moveInfo = if (externalFinishReason != null) {
            MoveInfo.createFinishingMove(player, externalFinishReason)
        } else {
            MoveInfo(position.toXY(field.realWidth), player)
        }
        return moveInfo.toGtpMove(field)
    }

    private fun MoveInfo.toGtpMove(field: Field): String {
        return when (externalFinishReason) {
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
                (if (player == Player.First) PLAYER1_MARKER else PLAYER2_MARKER) +
                        " ${positionXY!!.x}-${field.height - positionXY!!.y + 1}"
            }
        }
    }

    private fun toMovesSequence(input: String, field: Field): List<MoveInfo> {
        val pieces = input.split(" ")
        return buildList {
            for (i in pieces.indices step 2) {
                val player = when (pieces[i]) {
                    PLAYER1_MARKER -> Player.First
                    PLAYER2_MARKER -> Player.Second
                    else -> error("Unexpected player ${pieces[i]}")
                }
                add(parseMoveInfo(pieces[i + 1], field, player))
            }
        }
    }

    private fun parseMoveInfo(string: String, field: Field, player: Player): MoveInfo {
        when (string) {
            GROUND_MOVE -> {
                return MoveInfo.createFinishingMove(player, ExternalFinishReason.Grounding)
            }
            RESIGN_MOVE -> {
                return MoveInfo.createFinishingMove(player, ExternalFinishReason.Resign)
            }
            else -> {
                val dashIndex = string.indexOf('-')
                val x = string.take(dashIndex).toInt()
                val y = string.substring(dashIndex + 1, string.length).toInt()
                return MoveInfo(PositionXY(x, field.height - y + 1), player)
            }
        }
    }

    private suspend fun sendMessage(message: String): Response = sendMessage(message, writer, reader)
}