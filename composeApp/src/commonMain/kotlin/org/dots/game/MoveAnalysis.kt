package org.dots.game

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import kotlin.math.sqrt

/**
 * A single `moveInfos` entry of an analysis engine response.
 *
 * Every evaluation is reported from the perspective of the player to move (`reportAnalysisWinratesAs = SIDETOMOVE`),
 * that is [MoveAnalysis.player]: the greater [winRate] and [scoreLead], the better the move is for that player.
 */
data class AnalyzedMove(
    val positionXY: PositionXY,
    /** The engine preference: `0` is the move the engine would play. */
    val order: Int,
    val visits: Int,
    val edgeVisits: Int,
    val winRate: Double,
    val scoreLead: Double,
    val scoreMean: Double,
    val scoreStdev: Double,
    val scoreSelfplay: Double,
    val utility: Double,
    val utilityLcb: Double,
    val lcb: Double,
    val prior: Double,
    val weight: Double,
    /** Set for a move that is a symmetric equivalent of an already reported one, thus shares its evaluation. */
    val symmetryOf: PositionXY?,
    /** The principal variation: the line the engine expects after this move. */
    val pv: List<PositionXY>,
)

/**
 * The evaluation of the analyzed position itself (the `rootInfo` of a response) rather than of a move in it,
 * reported from the perspective of [MoveAnalysis.player] the same way [AnalyzedMove] is.
 */
data class AnalyzedPosition(
    val winRate: Double,
    val scoreLead: Double,
    val visits: Int,
    val weight: Double,
)

/**
 * The result of a single analysis query, see [AnalyzedMove] for the evaluation perspective
 * and [parseMoveAnalysis] for the response it's parsed from.
 */
data class MoveAnalysis(
    val player: Player,
    /** Candidate moves ordered by [AnalyzedMove.order], the best one first. */
    val moves: List<AnalyzedMove>,
    /**
     * The expected owner of every field position, or `null` if the ownership wasn't requested.
     * Use [ownershipOf] instead of indexing it directly.
     */
    val ownership: List<Double>? = null,
    /** Needed to address [ownership], which is a flat row-major array. */
    val fieldWidth: Int = 0,
    /** How the position itself is evaluated, which is what a graph of a whole game is drawn of. */
    val position: AnalyzedPosition? = null,
    /**
     * The move the engine would play itself, which is not necessarily [best]: the engine varies its play
     * by `chosenMoveTemperature`. It's a finishing move when the engine decided to ground or to resign,
     * and `null` when the engine reported no move at all.
     */
    val chosenMove: MoveInfo? = null,
) {
    val best: AnalyzedMove? = moves.firstOrNull()

    /** @return the candidate move at [positionXY], or `null` if the engine reported none there. */
    fun moveAt(positionXY: PositionXY): AnalyzedMove? = moves.firstOrNull { it.positionXY == positionXY }

    /**
     * How likely [positionXY] ends up captured, from `-1.0` (surely owned by the opponent of [player])
     * through `0.0` (nobody owns it) to `1.0` (surely owned by [player]).
     *
     * @return `null` if the ownership wasn't requested.
     */
    fun ownershipOf(positionXY: PositionXY): Double? =
        ownership?.getOrNull((positionXY.y - Field.OFFSET) * fieldWidth + (positionXY.x - Field.OFFSET))

    val totalVisits: Int = moves.sumOf { it.visits }

    private val maxVisits: Int = moves.maxOfOrNull { it.visits } ?: 0

    /**
     * How much worse [move] is than [best], from `0.0` (the best move) to `1.0` (a blunder).
     *
     * Both the win rate and the score lead are taken into account because they may diverge:
     * a move may barely affect the win rate but still give away a lot of points (and the other way around).
     */
    fun lossOf(move: AnalyzedMove): Double {
        val best = best ?: return 0.0

        val winRateLoss = ((best.winRate - move.winRate) / MAX_MEANINGFUL_WIN_RATE_LOSS).coerceIn(0.0, 1.0)
        val scoreLeadLoss = ((best.scoreLead - move.scoreLead) / MAX_MEANINGFUL_SCORE_LEAD_LOSS).coerceIn(0.0, 1.0)

        return WIN_RATE_LOSS_WEIGHT * winRateLoss + (1.0 - WIN_RATE_LOSS_WEIGHT) * scoreLeadLoss
    }

    /**
     * How trustworthy the evaluation of [move] is, from `0.0` (barely visited) to `1.0` (the most explored move).
     * The square root is used because the very first visits are the most informative ones.
     */
    fun confidenceOf(move: AnalyzedMove): Double =
        if (maxVisits <= 0) 0.0 else sqrt(move.visits.toDouble() / maxVisits)

    companion object {
        /** A win rate drop that is already bad enough to render a move as the worst one. */
        private const val MAX_MEANINGFUL_WIN_RATE_LOSS = 0.15

        /** A score lead drop (in dots) that is already bad enough to render a move as the worst one. */
        private const val MAX_MEANINGFUL_SCORE_LEAD_LOSS = 8.0

        private const val WIN_RATE_LOSS_WEIGHT = 0.65
    }
}

private const val MOVE_INFOS_KEY = "moveInfos"
private const val MOVE_KEY = "move"
private const val PV_KEY = "pv"
private const val SYMMETRY_OF_KEY = "isSymmetryOf"
private const val ORDER_KEY = "order"
private const val VISITS_KEY = "visits"
private const val EDGE_VISITS_KEY = "edgeVisits"
private const val WIN_RATE_KEY = "winrate"
private const val SCORE_LEAD_KEY = "scoreLead"
private const val SCORE_MEAN_KEY = "scoreMean"
private const val SCORE_STDEV_KEY = "scoreStdev"
private const val SCORE_SELFPLAY_KEY = "scoreSelfplay"
private const val UTILITY_KEY = "utility"
private const val UTILITY_LCB_KEY = "utilityLcb"
private const val LCB_KEY = "lcb"
private const val PRIOR_KEY = "prior"
private const val WEIGHT_KEY = "weight"
private const val OWNERSHIP_KEY = "ownership"
private const val ROOT_INFO_KEY = "rootInfo"
private const val CURRENT_PLAYER_KEY = "currentPlayer"
private const val TURN_NUMBER_KEY = "turnNumber"
private const val CHOSEN_MOVE_KEY = "chosenMove"
private const val RESIGN_REASONABLE_KEY = "resignReasonable"

internal const val GROUND_MOVE = "ground"
internal const val RESIGN_MOVE = "resign"

internal const val PLAYER1_MARKER = "P1"
internal const val PLAYER2_MARKER = "P2"

internal fun Player.toEngineMarker(): String = when (this) {
    Player.First -> PLAYER1_MARKER
    Player.Second -> PLAYER2_MARKER
    else -> error("Unexpected player $this")
}

private fun String.toPlayerOrNull(): Player? = when (this) {
    PLAYER1_MARKER -> Player.First
    PLAYER2_MARKER -> Player.Second
    else -> null
}

/**
 * Parses a response of the analysis engine (`katago analysis`), which reports a whole query as a single
 * JSON line, see the [Analysis Engine documentation](https://github.com/lightvector/KataGo/blob/master/docs/Analysis_Engine.md).
 *
 * The values that are absent are reported as their neutral defaults rather than as a failure, because
 * the set of the reported ones depends on the query (the ownership) and on the version of the engine.
 *
 * @param player the player the query was made for, used only if the response reports none of its own,
 * see [MoveAnalysis.player].
 */
fun parseMoveAnalysis(response: JsonObject, player: Player, fieldWidth: Int, fieldHeight: Int): MoveAnalysis {
    val moves = (response[MOVE_INFOS_KEY] as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.let { moveInfo -> parseAnalyzedMove(moveInfo, fieldWidth, fieldHeight) } }
        ?.sortedBy { it.order }
        ?: emptyList()

    val rootInfo = response[ROOT_INFO_KEY] as? JsonObject
    // A query of a whole game analyzes the turns of both players at once, so the player of a response
    // is the one the response itself reports rather than the one the query was made for
    val analyzedPlayer = rootInfo?.string(CURRENT_PLAYER_KEY)?.toPlayerOrNull() ?: player

    return MoveAnalysis(
        analyzedPlayer,
        moves,
        ownership = parseOwnership(response, fieldWidth, fieldHeight),
        fieldWidth = fieldWidth,
        position = rootInfo?.let {
            AnalyzedPosition(
                winRate = it.double(WIN_RATE_KEY),
                scoreLead = it.double(SCORE_LEAD_KEY),
                visits = it.int(VISITS_KEY),
                weight = it.double(WEIGHT_KEY),
            )
        },
        chosenMove = parseChosenMove(response, analyzedPlayer, fieldWidth, fieldHeight),
    )
}

/** @return the turn of a game the response is about, see the `analyzeTurns` of a query. */
fun turnNumberOf(response: JsonObject): Int? = (response[TURN_NUMBER_KEY] as? JsonPrimitive)?.intOrNull

/**
 * The ownership is reported once for the whole position rather than per candidate move, and it's laid out
 * row by row starting from the topmost one, which matches the order [MoveAnalysis.ownershipOf] addresses it by.
 *
 * @return `null` if the ownership wasn't requested or if the reported array doesn't cover the field,
 * because a partial array can't be mapped to the positions reliably.
 */
private fun parseOwnership(response: JsonObject, fieldWidth: Int, fieldHeight: Int): List<Double>? =
    (response[OWNERSHIP_KEY] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
        ?.takeIf { it.size == fieldWidth * fieldHeight }

/**
 * The engine reports the move it would play itself along with the evaluation of every candidate, so that
 * the very same query both analyzes a position and plays it.
 *
 * Resigning is a decision of a player rather than of the search, and the engine reports it separately:
 * a lost position is resigned the same way the GTP `genmove` of KataGoDots resigns it.
 */
private fun parseChosenMove(response: JsonObject, player: Player, fieldWidth: Int, fieldHeight: Int): MoveInfo? {
    if (response.boolean(RESIGN_REASONABLE_KEY)) {
        return MoveInfo.createFinishingMove(player, ExternalFinishReason.Resign)
    }

    return when (val move = response.string(CHOSEN_MOVE_KEY)) {
        null -> null
        GROUND_MOVE -> MoveInfo.createFinishingMove(player, ExternalFinishReason.Grounding)
        RESIGN_MOVE -> MoveInfo.createFinishingMove(player, ExternalFinishReason.Resign)
        else -> parseAnalysisPosition(move, fieldWidth, fieldHeight)?.let { MoveInfo(it, player) }
    }
}

private fun parseAnalyzedMove(moveInfo: JsonObject, fieldWidth: Int, fieldHeight: Int): AnalyzedMove? {
    // Non-coordinate moves (`ground`, `resign`) are not worth highlighting on the field
    val positionXY = parseAnalysisPosition(moveInfo.string(MOVE_KEY) ?: return null, fieldWidth, fieldHeight)
        ?: return null

    return AnalyzedMove(
        positionXY = positionXY,
        order = moveInfo.int(ORDER_KEY),
        visits = moveInfo.int(VISITS_KEY),
        edgeVisits = moveInfo.int(EDGE_VISITS_KEY),
        winRate = moveInfo.double(WIN_RATE_KEY),
        scoreLead = moveInfo.double(SCORE_LEAD_KEY),
        scoreMean = moveInfo.double(SCORE_MEAN_KEY),
        scoreStdev = moveInfo.double(SCORE_STDEV_KEY),
        scoreSelfplay = moveInfo.double(SCORE_SELFPLAY_KEY),
        utility = moveInfo.double(UTILITY_KEY),
        utilityLcb = moveInfo.double(UTILITY_LCB_KEY),
        lcb = moveInfo.double(LCB_KEY),
        prior = moveInfo.double(PRIOR_KEY),
        weight = moveInfo.double(WEIGHT_KEY),
        symmetryOf = moveInfo.string(SYMMETRY_OF_KEY)?.let { parseAnalysisPosition(it, fieldWidth, fieldHeight) },
        pv = (moveInfo[PV_KEY] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }
                ?.let { primitive -> parseAnalysisPosition(primitive.content, fieldWidth, fieldHeight) }
        } ?: emptyList(),
    )
}

/**
 * Converts an `x-y` move of the engine to [PositionXY].
 * The vertical axis is inverted (the engine counts it from the bottom), the same way as in `toEngineMove`.
 */
internal fun parseAnalysisPosition(move: String, fieldWidth: Int, fieldHeight: Int): PositionXY? {
    val dashIndex = move.indexOf('-')
    if (dashIndex <= 0) return null

    val x = move.substring(0, dashIndex).toIntOrNull() ?: return null
    val y = move.substring(dashIndex + 1).toIntOrNull() ?: return null

    if (x !in 1..fieldWidth || y !in 1..fieldHeight) return null

    return PositionXY(x, fieldHeight - y + 1)
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.double(key: String): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
