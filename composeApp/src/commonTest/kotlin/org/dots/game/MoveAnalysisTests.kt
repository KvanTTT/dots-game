package org.dots.game

import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.views.toFixed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The test data is a real `kata-search_analyze P1` response for a cross-initialized 8x8 field
 * (`set_position P1 4-4 P2 5-4 P2 4-5 P1 5-5`).
 */
class MoveAnalysisTests {
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
        parseMoveAnalysis(lines, Player.First, FIELD_WIDTH, FIELD_HEIGHT)

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

    @Test
    fun theBestMoveHasNoLossAndTheFullConfidence() {
        val analysis = parse()
        val best = analysis.best

        assertSame(analysis.moves[0], best)
        assertEquals(0.0, analysis.lossOf(best!!))
        assertEquals(1.0, analysis.confidenceOf(best))
    }

    @Test
    fun aWorseMoveLosesBothTheWinRateAndTheScoreLead() {
        val analysis = parse()
        // The win rate part is fully saturated (-39% out of the meaningful 15%),
        // the score lead one is almost (-7.66 out of the meaningful 8.0)
        assertEquals(
            0.65 + 0.35 * (7.655952 / 8.0),
            analysis.lossOf(analysis.moves[2]),
            absoluteTolerance = 1e-9,
        )
        // The symmetric move shares the evaluation of the best one
        assertEquals(0.0, analysis.lossOf(analysis.moves[1]))
        assertTrue(analysis.confidenceOf(analysis.moves[2]) < 0.1)
    }

    @Test
    fun theLossIsAffectedByTheScoreLeadEvenWhenTheWinRateIsTheSame() {
        val analysis = parse(
            listOf(
                "info move 5-6 visits 10 winrate 0.5 scoreLead 4.0 order 0 pv 5-6 " +
                    "info move 4-3 visits 10 winrate 0.5 scoreLead 0.0 order 1 pv 4-3"
            )
        )

        assertEquals(0.0, analysis.lossOf(analysis.moves[0]))
        // A half of the meaningful score lead loss weighted by `1 - WIN_RATE_LOSS_WEIGHT`
        assertEquals(0.175, analysis.lossOf(analysis.moves[1]), absoluteTolerance = 1e-9)
    }

    @Test
    fun theOwnershipIsAbsentUnlessItIsRequested() {
        val analysis = parse()

        assertNull(analysis.ownership)
        assertNull(analysis.ownershipOf(PositionXY(1, 1)))
    }

    /**
     * The array is laid out row by row starting from the topmost one, which is the same order
     * the field positions are numbered in, so no flipping is needed (unlike for the moves).
     *
     * The values below are a real `kata-search_analyze P1 ownership true` response for a 4x3 field
     * shrunk to one value per position.
     */
    @Test
    fun theOwnershipIsMappedToThePositionsRowByRowFromTheTop() {
        val ownershipValues = listOf(
            "0.11", "0.12", "0.13", "0.14",
            "0.21", "0.22", "0.23", "0.24",
            "-0.31", "-0.32", "-0.33", "0.86",
        )
        val analysis = parseMoveAnalysis(
            listOf("info move 1-3 order 0 pv 1-3 ownership ${ownershipValues.joinToString(" ")}"),
            Player.First,
            fieldWidth = 4,
            fieldHeight = 3,
        )

        assertEquals(12, analysis.ownership?.size)

        // The top left position is the very first value, the bottom right one is the very last
        assertEquals(0.11, analysis.ownershipOf(PositionXY(1, 1)))
        assertEquals(0.86, analysis.ownershipOf(PositionXY(4, 3)))
        // The second row starts right after the first one
        assertEquals(0.21, analysis.ownershipOf(PositionXY(1, 2)))
        assertEquals(0.24, analysis.ownershipOf(PositionXY(4, 2)))
        // A negative value means the position is expected to be captured by the opponent
        assertEquals(-0.31, analysis.ownershipOf(PositionXY(1, 3)))

        // The variation must not swallow the ownership array
        assertEquals(listOf(PositionXY(1, 1)), analysis.moves.single().pv)
    }

    @Test
    fun anOwnershipThatDoesNotCoverTheFieldIsRejected() {
        // A partial array can't be mapped to the positions, so it's dropped instead of being misaligned
        val analysis = parseMoveAnalysis(
            listOf("info move 1-3 order 0 pv 1-3 ownership 0.11 0.12 0.13"),
            Player.First,
            fieldWidth = 4,
            fieldHeight = 3,
        )

        assertNull(analysis.ownership)
        assertEquals(1, analysis.moves.size)
    }

    /** The details of an analyzed move render the values with exactly two fraction digits. */
    @Test
    fun theOwnershipIsFormattedWithTwoFractionDigits() {
        assertEquals("0.86", 0.86.toFixed(2))
        assertEquals("-0.42", (-0.42).toFixed(2))
        assertEquals("0.00", 0.0.toFixed(2))
        assertEquals("1.00", 1.0.toFixed(2))
        assertEquals("-1.00", (-1.0).toFixed(2))
        // The trailing zeros are kept, so the hint never jumps between the widths of `0.5` and `0.05`
        assertEquals("0.50", 0.5.toFixed(2))
        assertEquals("0.05", 0.05.toFixed(2))
        // Rounding up to the whole value keeps both digits
        assertEquals("1.00", 0.996.toFixed(2))
        // A value that rounds to zero must not be rendered as a negative zero
        assertEquals("0.00", (-0.004).toFixed(2))
    }

    @Test
    fun fractionDigitsAreFormattedWithoutStringFormat() {
        assertEquals("51.9", (0.519 * 100).toFixed(1))
        assertEquals("5.07", 5.06729.toFixed(2))
        assertEquals("-1.1", (-1.07043).toFixed(1))
        assertEquals("0.0", (-0.04).toFixed(1))
        assertEquals("0.100", 0.0999.toFixed(3))
        assertEquals("-13", (-13.4).toFixed(0))
        assertEquals("294.6", 294.632.toFixed(1))
    }
}
