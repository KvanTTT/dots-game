package org.dots.game

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.dots.game.core.Player

private const val INFO_MARKER = "info"
private const val PLAY_MARKER = "play"

/** The keys of an `info` block that are followed by exactly one value. */
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
 * Parses a `kata-search_analyze` response of the GTP engine, which looks like
 * `info move 21-15 visits 276 ... order 0 pv 21-15 22-17 info move 19-17 visits 83 ... order 1 pv 19-17 21-18`.
 *
 * @param player the player the command was sent for, see [MoveAnalysis.player].
 */
fun parseGtpMoveAnalysis(
    responseLines: List<String>,
    player: Player,
    fieldWidth: Int,
    fieldHeight: Int,
): MoveAnalysis = parseMoveAnalysis(toAnalysisResponse(responseLines), player, fieldWidth, fieldHeight)

/**
 * Reshapes a GTP analysis response into the response of the analysis engine, so that both protocols are
 * parsed by the very same [parseMoveAnalysis]: the two report the same values under the same names, only
 * GTP writes them as a flat sequence of tokens rather than as JSON.
 *
 * All the `info` blocks are reported on a single line, and a trailing `play <move>` line names the move
 * the engine would play, that is the [CHOSEN_MOVE_KEY] of an analysis response.
 *
 * Every value stays a string: the numbers of a response are parsed out of the text of a token in any case.
 */
internal fun toAnalysisResponse(responseLines: List<String>): JsonObject {
    // If a reporting interval is requested, the engine emits several reports; the last one is the most complete
    val infoLine = responseLines.lastOrNull { it.startsWith("$INFO_MARKER ") }
    val tokens = infoLine?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()
    // A `pv` move never looks like `info`, so the marker unambiguously delimits the blocks
    val blockStarts = tokens.indices.filter { tokens[it] == INFO_MARKER }

    val blocks = blockStarts.mapIndexed { index, blockStart ->
        val blockEnd = blockStarts.getOrNull(index + 1) ?: tokens.size
        parseBlock(tokens.subList(blockStart + 1, blockEnd))
    }

    // The ownership is reported once for the whole response rather than per move, so it's hoisted out of
    // the block it's appended to
    val ownership = blocks.firstNotNullOfOrNull { it[OWNERSHIP_KEY] }

    val chosenMove = responseLines.lastOrNull { it.startsWith("$PLAY_MARKER ") }?.substringAfter(' ')?.trim()

    return buildJsonObject {
        putJsonArray(MOVE_INFOS_KEY) {
            for (block in blocks) {
                addJsonObject {
                    for ((key, values = value) in block) {
                        when {
                            key == OWNERSHIP_KEY -> {}
                            key in listKeys -> putJsonArray(key) { for (value in values) add(value) }
                            else -> values.firstOrNull()?.let { put(key, it) }
                        }
                    }
                }
            }
        }
        ownership?.let { values -> putJsonArray(OWNERSHIP_KEY) { for (value in values) add(value) } }
        chosenMove?.let { put(CHOSEN_MOVE_KEY, it) }
    }
}

/**
 * The parsing is key-based rather than position-based, because the set of the reported keys varies
 * ([SYMMETRY_OF_KEY] is only present for symmetric moves) and because [PV_KEY] has a variable length.
 */
private fun parseBlock(tokens: List<String>): Map<String, List<String>> {
    val values = mutableMapOf<String, List<String>>()

    var index = 0
    while (index < tokens.size) {
        val key = tokens[index]
        if (key in scalarKeys) {
            values[key] = listOf(tokens.getOrNull(index + 1) ?: break)
            index += 2
        } else {
            // A list-valued or an unknown key: its values last until the next known key.
            // Resynchronizing on a key instead of counting the values keeps the scalars aligned
            // no matter how many values a list has
            val valuesEnd = (index + 1 until tokens.size).firstOrNull { tokens[it] in knownKeys } ?: tokens.size
            if (key in listKeys) {
                values[key] = tokens.subList(index + 1, valuesEnd)
            }
            index = valuesEnd
        }
    }

    return values
}
