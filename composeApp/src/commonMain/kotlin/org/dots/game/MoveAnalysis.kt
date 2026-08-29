package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import kotlin.math.sqrt

/**
 * A single `info` block of a `kata-search_analyze` response.
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

/** The result of a `kata-search_analyze` request, see [AnalyzedMove] for the evaluation perspective. */
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

private const val INFO_MARKER = "info"
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

/** The keys that are followed by exactly one value. */
private val scalarKeys = setOf(
    MOVE_KEY, SYMMETRY_OF_KEY, ORDER_KEY, VISITS_KEY, EDGE_VISITS_KEY, WIN_RATE_KEY,
    SCORE_LEAD_KEY, SCORE_MEAN_KEY, SCORE_STDEV_KEY, SCORE_SELFPLAY_KEY,
    UTILITY_KEY, UTILITY_LCB_KEY, LCB_KEY, PRIOR_KEY, WEIGHT_KEY,
)

/** The keys that are followed by a variable number of values. */
private val listKeys = setOf(
    PV_KEY, "pvVisits", "pvEdgeVisits",
    OWNERSHIP_KEY, "ownershipStdev", "movesOwnership", "movesOwnershipStdev",
)

private val knownKeys = scalarKeys + listKeys

/**
 * Parses a `kata-search_analyze` response that looks like
 * `info move 21-15 visits 276 ... order 0 pv 21-15 22-17 info move 19-17 visits 83 ... order 1 pv 19-17 21-18`.
 *
 * All the `info` blocks are reported on a single line; a trailing `play <move>` line is ignored
 * because the same move is also reported as the block with `order 0`.
 *
 * The parsing is key-based rather than position-based, because the set of the reported keys varies
 * ([SYMMETRY_OF_KEY] is only present for symmetric moves) and because [PV_KEY] has a variable length.
 *
 * If `ownership true` was requested, a single [OWNERSHIP_KEY] array of `fieldWidth * fieldHeight` values
 * is appended after the last block, see [parseOwnership].
 */
fun parseMoveAnalysis(responseLines: List<String>, player: Player, fieldWidth: Int, fieldHeight: Int): MoveAnalysis {
    // If a reporting interval is requested, the engine emits several reports; the last one is the most complete.
    val infoLine = responseLines.lastOrNull { it.startsWith("$INFO_MARKER ") }
        ?: return MoveAnalysis(player, emptyList())

    val tokens = infoLine.split(' ').filter { it.isNotEmpty() }
    // A `pv` move never looks like `info`, so the marker unambiguously delimits the blocks
    val blockStarts = tokens.indices.filter { tokens[it] == INFO_MARKER }

    val moves = blockStarts.mapIndexedNotNull { index, blockStart ->
        val blockEnd = blockStarts.getOrNull(index + 1) ?: tokens.size
        parseAnalyzedMove(tokens.subList(blockStart + 1, blockEnd), fieldWidth, fieldHeight)
    }

    return MoveAnalysis(
        player,
        moves.sortedBy { it.order },
        ownership = parseOwnership(tokens, fieldWidth, fieldHeight),
        fieldWidth = fieldWidth,
    )
}

/**
 * The ownership array is reported once for the whole response rather than per `info` block,
 * and it's laid out row by row starting from the topmost one, which matches the order
 * [MoveAnalysis.ownershipOf] addresses it by.
 *
 * @return `null` if the ownership wasn't requested or if the reported array doesn't cover the field,
 * because a partial array can't be mapped to the positions reliably.
 */
private fun parseOwnership(tokens: List<String>, fieldWidth: Int, fieldHeight: Int): List<Double>? {
    val ownershipIndex = tokens.indexOf(OWNERSHIP_KEY)
    if (ownershipIndex < 0) return null

    return tokens.drop(ownershipIndex + 1)
        .mapNotNull { it.toDoubleOrNull() }
        .takeIf { it.size == fieldWidth * fieldHeight }
}

private fun parseAnalyzedMove(tokens: List<String>, fieldWidth: Int, fieldHeight: Int): AnalyzedMove? {
    val values = mutableMapOf<String, String>()
    var pv: List<PositionXY> = emptyList()

    var index = 0
    while (index < tokens.size) {
        val key = tokens[index]
        if (key in scalarKeys) {
            values[key] = tokens.getOrNull(index + 1) ?: break
            index += 2
        } else {
            // A list-valued or an unknown key: its values last until the next known key.
            // Resynchronizing on a key instead of counting the values keeps the scalars aligned
            // no matter how many values a list has.
            val valuesEnd = (index + 1 until tokens.size).firstOrNull { tokens[it] in knownKeys } ?: tokens.size
            if (key == PV_KEY) {
                pv = tokens.subList(index + 1, valuesEnd).mapNotNull { parseGtpPosition(it, fieldWidth, fieldHeight) }
            }
            index = valuesEnd
        }
    }

    // Non-coordinate moves (`ground`, `resign`) are not worth highlighting on the field
    val positionXY = parseGtpPosition(values[MOVE_KEY] ?: return null, fieldWidth, fieldHeight) ?: return null

    fun double(key: String): Double = values[key]?.toDoubleOrNull() ?: 0.0
    fun int(key: String): Int = values[key]?.toIntOrNull() ?: 0

    return AnalyzedMove(
        positionXY = positionXY,
        order = int(ORDER_KEY),
        visits = int(VISITS_KEY),
        edgeVisits = int(EDGE_VISITS_KEY),
        winRate = double(WIN_RATE_KEY),
        scoreLead = double(SCORE_LEAD_KEY),
        scoreMean = double(SCORE_MEAN_KEY),
        scoreStdev = double(SCORE_STDEV_KEY),
        scoreSelfplay = double(SCORE_SELFPLAY_KEY),
        utility = double(UTILITY_KEY),
        utilityLcb = double(UTILITY_LCB_KEY),
        lcb = double(LCB_KEY),
        prior = double(PRIOR_KEY),
        weight = double(WEIGHT_KEY),
        symmetryOf = values[SYMMETRY_OF_KEY]?.let { parseGtpPosition(it, fieldWidth, fieldHeight) },
        pv = pv,
    )
}

/**
 * Converts an `x-y` GTP move to [PositionXY].
 * The vertical axis is inverted in GTP (it counts from the bottom), the same way as in `MoveInfo.toGtpMove`.
 */
private fun parseGtpPosition(token: String, fieldWidth: Int, fieldHeight: Int): PositionXY? {
    val dashIndex = token.indexOf('-')
    if (dashIndex <= 0) return null

    val x = token.substring(0, dashIndex).toIntOrNull() ?: return null
    val y = token.substring(dashIndex + 1).toIntOrNull() ?: return null

    if (x !in 1..fieldWidth || y !in 1..fieldHeight) return null

    return PositionXY(x, fieldHeight - y + 1)
}
