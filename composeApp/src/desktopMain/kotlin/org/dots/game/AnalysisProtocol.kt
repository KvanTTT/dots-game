package org.dots.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.InitPosGenType
import org.dots.game.core.InitPosType
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Talks to `katago analysis`: a query is a single JSON line and so is its response, and the two are matched
 * by the `id` of the query rather than by their order, which is what makes the queries independent.
 *
 * Every query carries the whole position, so the engine shares no state with the app: nothing has to be
 * synchronized, several queries may be searched at once, and a query that is no longer needed is cancelled
 * instead of being waited for.
 */
internal class AnalysisProtocol private constructor(
    settings: KataGoDotsSettings,
    process: Process,
    writer: OutputStreamWriter,
    logger: (Diagnostic) -> Unit,
) : KataGoDotsProtocol(settings, process, writer, logger) {
    companion object {
        private const val ANALYSIS_COMMAND = "analysis"

        /** The engine reports it on the standard error once it has loaded the model and is ready to be queried. */
        private const val READY_MARKER = "Started, ready to begin handling requests"

        suspend fun initialize(settings: KataGoDotsSettings, logger: (Diagnostic) -> Unit): AnalysisProtocol? {
            // The standard error carries the startup log of the engine, while the standard output
            // carries the responses, so the two streams must not be merged
            val process = startProcess(settings, ANALYSIS_COMMAND, mergeErrorStream = false)

            val protocol = AnalysisProtocol(settings, process, OutputStreamWriter(process.outputStream), logger)
            protocol.readResponses(process.inputStream.bufferedReader())
            protocol.readStartupLog(process.errorStream.bufferedReader())

            if (!protocol.ready.await()) {
                protocol.close()
                logger(
                    Diagnostic(
                        startupFailureMessage(process, protocol.lastStartupLogLine),
                        severity = DiagnosticSeverity.Critical,
                    )
                )
                return null
            }

            if (!protocol.isKataGoDots) {
                protocol.close()
                logger(
                    Diagnostic(
                        "The engine should support Dots game mode (expected `${KataGoDotsEngine.KATA_GO_DOTS_APP_NAME}` engine)",
                        severity = DiagnosticSeverity.Error
                    )
                )
                return null
            }

            return protocol
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * The queries that are still waiting for their responses, keyed by [QUERY_ID_KEY]. A query is answered
     * with a single response, except the one of a whole game, which is answered with one response per turn,
     * so the responses are streamed rather than awaited one by one. A `null` means the engine dropped
     * the query and nothing else is coming.
     */
    private val pendingQueries = ConcurrentHashMap<String, Channel<JsonObject?>>()

    private val queryCounter = AtomicLong()

    /** Completed with `false` if the engine dies before it reports [READY_MARKER]. */
    private val ready = CompletableDeferred<Boolean>()

    @Volatile
    private var isKataGoDots = false

    /** The last thing the engine said before it died, which is the reason it reports a broken setup by. */
    @Volatile
    private var lastStartupLogLine: String? = null

    /**
     * Reads the responses until the engine exits. Every line that isn't a response is reported as is:
     * the engine writes its warnings (an outdated model, for one) into the very same stream.
     */
    private fun readResponses(reader: BufferedReader) {
        scope.launch {
            reader.use {
                for (line in it.lineSequence()) {
                    val response = line.toJsonObjectOrNull()
                    if (response == null) {
                        if (line.isNotBlank()) {
                            logger(Diagnostic(line.trimMessageIfNecessary(), severity = DiagnosticSeverity.Warning))
                        }
                        continue
                    }
                    handleResponse(response)
                }
            }

            // The engine is gone, so nothing is going to answer the queries that are still waiting
            ready.complete(false)
            releasePendingQueries()
        }
    }

    private fun handleResponse(response: JsonObject) {
        val queryId = response.stringOrNull(QUERY_ID_KEY)
        val error = response.stringOrNull(ERROR_KEY)
        val warning = response.stringOrNull(WARNING_KEY)

        when {
            error != null -> {
                logger(Diagnostic(response.describe(error), severity = DiagnosticSeverity.Error))
                queryId?.let { pendingQueries[it]?.trySend(null) }
            }
            warning != null -> {
                // A warning precedes the response of the very same query, which is still to come
                logger(Diagnostic(response.describe(warning), severity = DiagnosticSeverity.Warning))
            }
            // A partial report of a search that is still running: only the final one is of interest
            (response[IS_DURING_SEARCH_KEY] as? JsonPrimitive)?.booleanOrNull == true -> {}
            queryId != null -> {
                pendingQueries[queryId]?.trySend(response.takeIf { NO_RESULTS_KEY !in it })
            }
        }
    }

    private fun readStartupLog(reader: BufferedReader) {
        scope.launch {
            reader.use {
                for (line in it.lineSequence()) {
                    if (line.startsWith(KataGoDotsEngine.KATA_GO_DOTS_APP_NAME)) {
                        isKataGoDots = true
                    }
                    if (line.contains(READY_MARKER)) {
                        ready.complete(true)
                    }
                    if (line.isNotBlank()) {
                        lastStartupLogLine = line
                        logger(Diagnostic(line.trimMessageIfNecessary(), severity = DiagnosticSeverity.Info))
                    }
                }
            }

            ready.complete(false)
        }
    }

    override suspend fun generateMove(field: Field, player: Player?, clock: PlayerClock?): MoveInfo? {
        // The ownership is of no use for a move, and it's by far the largest part of a response
        return query(field, player, withOwnership = false, clock)?.chosenMove
    }

    override suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis? {
        // An analysis is no move of a game, so it's searched by the limits of the engine rather than
        // by the clock the players are on
        return query(field, player, withOwnership, clock = null)?.takeIf { it.moves.isNotEmpty() }
    }

    /** A whole game is analyzed by a single query rather than by a search per turn. */
    override suspend fun analyzeGame(
        field: Field,
        moves: List<MoveInfo>,
        turnNumbers: List<Int>,
        onTurnAnalyzed: (turnNumber: Int, analysis: MoveAnalysis) -> Unit,
    ) {
        if (!doesKataSupportRules(field.rules) || turnNumbers.isEmpty()) return

        val queryId = queryCounter.incrementAndGet().toString()
        val query = buildQuery(
            queryId, field, moves,
            // Every turn is analyzed for the player it's the turn of, and the ownership of a whole game
            // would be a value per position per turn, which no graph of it displays
            player = null, turnNumbers = turnNumbers, withOwnership = false, clock = null,
        )

        val _ = withQuery(queryId, query) { responses ->
            // The engine reports a turn as soon as it's searched, and the turns of a query may be searched
            // in any order, so every response tells the turn it's about
            repeat(turnNumbers.size) {
                val response = responses.receive() ?: return@withQuery
                val turnNumber = turnNumberOf(response) ?: return@withQuery
                onTurnAnalyzed(
                    turnNumber,
                    parseMoveAnalysis(response, field.getCurrentPlayer(), field.width, field.height),
                )
            }
        }
    }

    /** @return `null` if the engine reported no analysis at all, that is it rejected or dropped the query. */
    private suspend fun query(
        field: Field,
        player: Player?,
        withOwnership: Boolean,
        clock: PlayerClock?,
    ): MoveAnalysis? {
        if (!doesKataSupportRules(field.rules)) return null

        val effectivePlayer = player ?: field.getCurrentPlayer()
        val queryId = queryCounter.incrementAndGet().toString()
        val query = buildQuery(
            queryId, field, field.playedMoves(), effectivePlayer, turnNumbers = null, withOwnership, clock,
        )
        val response = send(queryId, query) ?: return null

        return parseMoveAnalysis(response, effectivePlayer, field.width, field.height)
    }

    /** Sends [query] and lets [receiveResponses] take its responses until it's done with them. */
    private suspend fun <T> withQuery(
        queryId: String,
        query: JsonObject,
        receiveResponses: suspend (ReceiveChannel<JsonObject?>) -> T,
    ): T? {
        val responses = Channel<JsonObject?>(Channel.UNLIMITED)
        pendingQueries[queryId] = responses

        try {
            writeLine(query.toString())

            return receiveResponses(responses)
        } catch (e: IOException) {
            // The engine is gone, and the app has to keep running without it
            logger(Diagnostic(e.message ?: e.toString(), severity = DiagnosticSeverity.Critical))
            return null
        } catch (cancellation: CancellationException) {
            // The engine keeps searching a query nobody waits for anymore, and it's the queries of
            // the current position that its threads are needed for
            terminate(queryId)
            throw cancellation
        } finally {
            pendingQueries.remove(queryId)
        }
    }

    private suspend fun send(queryId: String, query: JsonObject): JsonObject? =
        withQuery(queryId, query) { responses -> responses.receive() }

    /** Asks the engine to stop the search of [queryId], see [send]. */
    private fun terminate(queryId: String) {
        scope.launch {
            val query = buildJsonObject {
                put(QUERY_ID_KEY, "$queryId-terminate")
                put(ACTION_KEY, TERMINATE_ACTION)
                put(TERMINATE_ID_KEY, queryId)
            }

            // The engine may be gone already, and a termination that doesn't reach it changes nothing
            val _ = runCatching { writeLine(query.toString()) }
        }
    }

    /**
     * The engine is told the whole position rather than the difference from the previous one: the start
     * position of [field] as `initialStones` (they are placed rather than played, the same way the field
     * sets them up) and [moves] as `moves`.
     *
     * `playerToMove` is a KataGoDots extension: in Go the player to move follows from the moves, while
     * in Dots either player may move at any point, and the app even lets the user choose the one to analyze.
     * It's left out for [turnNumbers], where every turn is analyzed for the player whose turn it is.
     *
     * @param turnNumbers the turns of the game to analyze, `null` for the position [moves] end at.
     * @param clock the clock the search is limited by, `null` for a search the engine limits itself.
     */
    private fun buildQuery(
        queryId: String,
        field: Field,
        moves: List<MoveInfo>,
        player: Player?,
        turnNumbers: List<Int>?,
        withOwnership: Boolean,
        clock: PlayerClock?,
    ): JsonObject {
        val rules = field.rules

        return buildJsonObject {
            put(QUERY_ID_KEY, queryId)
            put(BOARD_X_SIZE_KEY, field.width)
            put(BOARD_Y_SIZE_KEY, field.height)

            putJsonObject(RULES_KEY) {
                put(DOTS_KEY, true)
                // The dots of the start position are sent as `initialStones` in any case: the analysis engine
                // never generates them out of the rule the way the GTP one does on `boardsize`,
                // so the rule only tells it which pattern the game started from
                put(START_POS_KEY, rules.initPosType.toEngineStartPos())
                // The engine only shuffles a generated start position by it, so this one is descriptive too
                put(START_POS_IS_RANDOM_KEY, rules.initPosGenType != InitPosGenType.Static)
                put(SUICIDE_KEY, rules.suicideAllowed)
                put(CAPTURE_EMPTY_BASE_KEY, rules.baseMode == BaseMode.AnySurrounding)
            }
            put(KOMI_KEY, rules.komi)

            putJsonArray(INITIAL_STONES_KEY) {
                for (move in field.initialMoves()) {
                    addMove(move, field)
                }
            }
            putJsonArray(MOVES_KEY) {
                for (move in moves) {
                    addMove(move, field)
                }
            }
            player?.let { put(PLAYER_TO_MOVE_KEY, it.toEngineMarker()) }
            turnNumbers?.let { turns ->
                putJsonArray(ANALYZE_TURNS_KEY) {
                    for (turnNumber in turns) {
                        add(turnNumber)
                    }
                }
            }

            put(INCLUDE_OWNERSHIP_KEY, withOwnership)

            if (clock != null) {
                // The clock of the game limits the search of a move of it, and a game that is played without
                // one leaves the engine with the limits of its config, see `GtpProtocol.setTimeControl`
                putJsonObject(TIME_CONTROL_KEY) {
                    put(MAIN_TIME_KEY, clock.timeSettings.mainTimeSeconds)
                    put(PER_MOVE_TIME_KEY, clock.timeSettings.turnTimeSeconds)
                    put(MAIN_TIME_LEFT_KEY, clock.mainTimeLeft.roundToTenthOfSecond())
                    put(PER_MOVE_TIME_LEFT_KEY, clock.turnTimeLeft.roundToTenthOfSecond())
                }
            } else {
                // A zero means "unset" in the settings, and the engine then keeps the limit of its config
                settings.maxVisits.takeIf { it > 0 }?.let { put(MAX_VISITS_KEY, it) }
                if (settings.maxTime > 0 || settings.maxPlayouts > 0) {
                    putJsonObject(OVERRIDE_SETTINGS_KEY) {
                        settings.maxTime.takeIf { it > 0 }?.let { put(MAX_TIME_KEY, it) }
                        settings.maxPlayouts.takeIf { it > 0 }?.let { put(MAX_PLAYOUTS_KEY, it) }
                    }
                }
            }
        }
    }

    override fun close() {
        // The process is stopped first, so that the reading of its output ends by itself, and whoever
        // is waiting for a response is released rather than left waiting for an engine that is gone
        super.close()
        ready.complete(false)
        releasePendingQueries()
        scope.cancel()
    }

    private fun releasePendingQueries() {
        for (queryId in pendingQueries.keys.toList()) {
            pendingQueries[queryId]?.trySend(null)
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addMove(move: MoveInfo, field: Field) {
        addJsonArray {
            add(move.player.toEngineMarker())
            add(move.toEngineMove(field))
        }
    }

    /** The start position patterns of the engine, see `Rules::startPosNameToId` of KataGoDots. */
    private fun InitPosType.toEngineStartPos(): String = when (this) {
        InitPosType.Empty -> EMPTY_START_POS
        InitPosType.Single -> SINGLE_START_POS
        InitPosType.Cross -> CROSS_START_POS
        InitPosType.DoubleCross -> DOUBLE_CROSS_START_POS
        InitPosType.QuadrupleCross -> QUADRUPLE_CROSS_START_POS
        // A start position of its own fits no pattern of the engine, and its dots are sent as they are
        InitPosType.Custom -> EMPTY_START_POS
    }
}

private const val QUERY_ID_KEY = "id"
private const val ACTION_KEY = "action"
private const val TERMINATE_ACTION = "terminate"
private const val TERMINATE_ID_KEY = "terminateId"
private const val BOARD_X_SIZE_KEY = "boardXSize"
private const val BOARD_Y_SIZE_KEY = "boardYSize"
private const val RULES_KEY = "rules"
private const val DOTS_KEY = "dots"
private const val START_POS_KEY = "startPos"
private const val EMPTY_START_POS = "EMPTY"
private const val SINGLE_START_POS = "SINGLE"
private const val CROSS_START_POS = "CROSS"
private const val DOUBLE_CROSS_START_POS = "CROSS_2"
private const val QUADRUPLE_CROSS_START_POS = "CROSS_4"
private const val START_POS_IS_RANDOM_KEY = "startPosIsRandom"
private const val SUICIDE_KEY = "suicide"
private const val CAPTURE_EMPTY_BASE_KEY = "dotsCaptureEmptyBase"
private const val KOMI_KEY = "komi"
private const val INITIAL_STONES_KEY = "initialStones"
private const val MOVES_KEY = "moves"
private const val PLAYER_TO_MOVE_KEY = "playerToMove"
private const val ANALYZE_TURNS_KEY = "analyzeTurns"
private const val INCLUDE_OWNERSHIP_KEY = "includeOwnership"
private const val TIME_CONTROL_KEY = "timeControl"
private const val MAIN_TIME_KEY = "mainTime"
private const val PER_MOVE_TIME_KEY = "perMoveTime"
private const val MAIN_TIME_LEFT_KEY = "mainTimeLeft"
private const val PER_MOVE_TIME_LEFT_KEY = "perMoveTimeLeft"
private const val MAX_VISITS_KEY = "maxVisits"
private const val OVERRIDE_SETTINGS_KEY = "overrideSettings"
private const val MAX_TIME_KEY = "maxTime"
private const val MAX_PLAYOUTS_KEY = "maxPlayouts"
private const val ERROR_KEY = "error"
private const val FIELD_KEY = "field"
private const val WARNING_KEY = "warning"
private const val IS_DURING_SEARCH_KEY = "isDuringSearch"
private const val NO_RESULTS_KEY = "noResults"

private val json = Json { ignoreUnknownKeys = true }

/** @return `null` if the line is not a response of the engine but a message it printed along with them. */
internal fun String.toJsonObjectOrNull(): JsonObject? {
    if (!trimStart().startsWith("{")) return null

    return runCatching { json.parseToJsonElement(this) as? JsonObject }.getOrNull()
}

/** The field a message is about is reported separately, and it's the most useful part of it. */
private fun JsonObject.describe(message: String): String {
    val field = stringOrNull(FIELD_KEY)
    return (if (field != null) "$field: $message" else message).trimMessageIfNecessary()
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
