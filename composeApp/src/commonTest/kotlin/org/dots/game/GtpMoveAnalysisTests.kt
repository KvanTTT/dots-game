package org.dots.game

import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The GTP engine reports the very same values as the analysis one, only as a flat sequence of tokens,
 * so the response is reshaped into the one of the analysis engine and parsed by [parseMoveAnalysis],
 * see [parseGtpMoveAnalysis].
 *
 * The test data is a real `kata-search_analyze P1` response for a cross-initialized 8x8 field
 * (`set_position P1 4-4 P2 5-4 P2 4-5 P1 5-5`).
 */
class GtpMoveAnalysisTests {
    companion object {
        private const val FIELD_WIDTH = 8
        private const val FIELD_HEIGHT = 8

        private const val INFO_LINE =
            "info move 5-6 visits 119 edgeVisits 119 utility -0.132423 winrate 0.491757 scoreMean -1.07043 " +
                "scoreStdev 8.4554 scoreLead -1.07043 scoreSelfplay 0.502535 prior 0.156628 lcb 0.364556 " +
                "utilityLcb -0.488586 weight 66.5761 order 0 pv 5-6 5-3 3-3 3-6 " +
                "info move 4-3 visits 119 edgeVisits 119 utility -0.132423 winrate 0.491757 scoreMean -1.07043 " +
                "scoreStdev 8.4554 scoreLead -1.07043 scoreSelfplay 0.502535 prior 0.156628 lcb 0.364556 " +
                "utilityLcb -0.488586 weight 66.5761 isSymmetryOf 5-6 order 1 pv 4-3 4-6 6-6 6-3 " +
                "info move 3-3 visits 1 edgeVisits 1 utility -1.01275 winrate 0.0968801 scoreMean -0.726382 " +
                "scoreStdev 6.47739 scoreLead -8.726382 scoreSelfplay -5.56129 prior 0.0652113 lcb -1.15312 " +
                "utilityLcb -4.51275 weight 0.647309 order 2 pv 3-3"

        private val RESPONSE_LINES = listOf("=", INFO_LINE, "play 5-6")
    }

    private fun parse(lines: List<String> = RESPONSE_LINES): MoveAnalysis =
        parseGtpMoveAnalysis(lines, Player.First, FIELD_WIDTH, FIELD_HEIGHT)

    @Test
    fun allInfoBlocksAreParsed() {
        val analysis = parse()

        assertEquals(Player.First, analysis.player)
        assertEquals(3, analysis.moves.size)
        assertEquals(listOf(0, 1, 2), analysis.moves.map { it.order })
        assertEquals(239, analysis.totalVisits)
    }

    @Test
    fun theVerticalAxisIsInverted() {
        // `5-6` in GTP is the 6th row from the bottom, that is the 3rd one from the top of an 8-row field
        assertEquals(PositionXY(5, 3), parse().moves[0].positionXY)
        assertEquals(PositionXY(4, 6), parse().moves[1].positionXY)
    }

    @Test
    fun allValuesOfABlockAreParsed() {
        val move = parse().moves[0]

        assertEquals(119, move.visits)
        assertEquals(119, move.edgeVisits)
        assertEquals(0.491757, move.winRate)
        assertEquals(-1.07043, move.scoreLead)
        assertEquals(-1.07043, move.scoreMean)
        assertEquals(8.4554, move.scoreStdev)
        assertEquals(0.502535, move.scoreSelfplay)
        assertEquals(-0.132423, move.utility)
        assertEquals(-0.488586, move.utilityLcb)
        assertEquals(0.364556, move.lcb)
        assertEquals(0.156628, move.prior)
        assertEquals(66.5761, move.weight)
        assertNull(move.symmetryOf)
    }

    @Test
    fun theVariationLastsUntilTheEndOfItsBlock() {
        assertEquals(
            listOf(PositionXY(5, 3), PositionXY(5, 6), PositionXY(3, 6), PositionXY(3, 3)),
            parse().moves[0].pv,
        )
        assertEquals(listOf(PositionXY(3, 6)), parse().moves[2].pv)
    }

    @Test
    fun theOptionalSymmetryKeyDoesNotShiftTheOtherValues() {
        val symmetricMove = parse().moves[1]

        assertEquals(PositionXY(5, 3), symmetricMove.symmetryOf)
        assertEquals(1, symmetricMove.order)
        assertEquals(119, symmetricMove.visits)
        assertEquals(listOf(PositionXY(4, 6), PositionXY(4, 3), PositionXY(6, 3), PositionXY(6, 6)), symmetricMove.pv)
    }

    @Test
    fun aKeyAfterTheVariationIsStillRecognized() {
        val analysis = parse(listOf("info move 5-6 order 0 pv 5-6 5-3 pvVisits 119 60 visits 119"))
        val move = analysis.moves.single()

        assertEquals(listOf(PositionXY(5, 3), PositionXY(5, 6)), move.pv)
        assertEquals(119, move.visits)
    }

    @Test
    fun theBlocksAreSortedByOrder() {
        val analysis = parse(listOf("info move 5-6 order 2 pv 5-6 info move 4-3 order 0 pv 4-3"))

        assertEquals(listOf(0, 2), analysis.moves.map { it.order })
        assertEquals(PositionXY(4, 6), analysis.best?.positionXY)
    }

    @Test
    fun theNonInfoLinesAreIgnored() {
        assertTrue(parse(listOf("=", "play 5-6")).moves.isEmpty())
        assertTrue(parse(emptyList()).moves.isEmpty())
        assertTrue(parse(listOf("")).moves.isEmpty())
    }

    @Test
    fun onlyTheLastReportIsTakenIntoAccount() {
        val analysis = parse(listOf("info move 5-6 visits 1 order 0 pv 5-6", "info move 5-6 visits 50 order 0 pv 5-6"))

        assertEquals(50, analysis.moves.single().visits)
    }

    @Test
    fun theMovesOutsideOfTheFieldAndTheNonCoordinateOnesAreSkipped() {
        assertTrue(parse(listOf("info move 9-1 order 0 pv 9-1")).moves.isEmpty())
        assertTrue(parse(listOf("info move 1-9 order 0 pv 1-9")).moves.isEmpty())
        assertTrue(parse(listOf("info move resign order 0")).moves.isEmpty())
        assertTrue(parse(listOf("info move ground order 0")).moves.isEmpty())
    }

    /** The move the engine would play is the `play` line of a response, the same as `chosenMove` of a query. */
    @Test
    fun theChosenMoveIsTheMoveOfThePlayLine() {
        val chosenMove = assertNotNull(parse().chosenMove)

        assertEquals(PositionXY(5, 3), chosenMove.positionXY)
        assertEquals(Player.First, chosenMove.player)

        assertEquals(
            ExternalFinishReason.Grounding,
            parse(listOf(INFO_LINE, "play ground")).chosenMove?.externalFinishReason,
        )
        assertEquals(
            ExternalFinishReason.Resign,
            parse(listOf(INFO_LINE, "play resign")).chosenMove?.externalFinishReason,
        )
        assertNull(parse(listOf(INFO_LINE)).chosenMove)
    }

    /**
     * The ownership is appended after the last `info` block rather than reported per move, so it belongs
     * to the whole response no matter which block it lands in.
     *
     * The values below are a real `kata-search_analyze P1 ownership true` response for a 4x3 field
     * shrunk to one value per position.
     */
    @Test
    fun theOwnershipIsHoistedOutOfTheBlockItIsAppendedTo() {
        val ownershipValues = listOf(
            "0.11", "0.12", "0.13", "0.14",
            "0.21", "0.22", "0.23", "0.24",
            "-0.31", "-0.32", "-0.33", "0.86",
        )
        val analysis = parseGtpMoveAnalysis(
            listOf("info move 1-3 order 0 pv 1-3 ownership ${ownershipValues.joinToString(" ")}"),
            Player.First,
            fieldWidth = 4,
            fieldHeight = 3,
        )

        assertEquals(12, analysis.ownership?.size)

        // The top left position is the very first value, the bottom right one is the very last
        assertEquals(0.11, analysis.ownershipOf(PositionXY(1, 1)))
        assertEquals(0.86, analysis.ownershipOf(PositionXY(4, 3)))
        // A negative value means the position is expected to be captured by the opponent
        assertEquals(-0.31, analysis.ownershipOf(PositionXY(1, 3)))

        // The variation must not swallow the ownership array
        assertEquals(listOf(PositionXY(1, 1)), analysis.moves.single().pv)
    }

    @Test
    fun theOwnershipIsAbsentUnlessItIsRequested() {
        assertNull(parse().ownership)
    }

    @Test
    fun anOwnershipThatDoesNotCoverTheFieldIsRejected() {
        // A partial array can't be mapped to the positions, so it's dropped instead of being misaligned
        val analysis = parseGtpMoveAnalysis(
            listOf("info move 1-3 order 0 pv 1-3 ownership 0.11 0.12 0.13"),
            Player.First,
            fieldWidth = 4,
            fieldHeight = 3,
        )

        assertNull(analysis.ownership)
        assertEquals(1, analysis.moves.size)
    }

    /** A GTP response reports no evaluation of the position itself, only of the moves in it. */
    @Test
    fun thePositionEvaluationIsNotReported() {
        assertNull(parse().position)
    }
}
