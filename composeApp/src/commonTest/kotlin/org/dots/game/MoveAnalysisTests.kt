package org.dots.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.views.toFixed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The test data is a real response of `katago analysis` for a cross-initialized 8x8 field,
 * shortened to the first candidate moves.
 */
class MoveAnalysisTests {
    companion object {
        private const val FIELD_WIDTH = 8
        private const val FIELD_HEIGHT = 8

        private const val RESPONSE = """{
            "id": "1",
            "isDuringSearch": false,
            "turnNumber": 0,
            "chosenMove": "5-6",
            "resignReasonable": false,
            "rootInfo": { "currentPlayer": "P1", "visits": 240, "winrate": 0.491757 },
            "moveInfos": [
                {
                    "move": "5-6", "order": 0, "visits": 119, "edgeVisits": 119, "winrate": 0.491757,
                    "scoreLead": -1.07043, "scoreMean": -1.07043, "scoreStdev": 8.4554, "scoreSelfplay": 0.502535,
                    "utility": -0.132423, "utilityLcb": -0.488586, "lcb": 0.364556, "prior": 0.156628,
                    "weight": 66.5761, "pv": ["5-6", "5-3", "3-3", "3-6"]
                },
                {
                    "move": "4-3", "order": 1, "visits": 119, "edgeVisits": 119, "winrate": 0.491757,
                    "scoreLead": -1.07043, "scoreMean": -1.07043, "scoreStdev": 8.4554, "scoreSelfplay": 0.502535,
                    "utility": -0.132423, "utilityLcb": -0.488586, "lcb": 0.364556, "prior": 0.156628,
                    "weight": 66.5761, "isSymmetryOf": "5-6", "pv": ["4-3", "4-6", "6-6", "6-3"]
                },
                {
                    "move": "3-3", "order": 2, "visits": 1, "edgeVisits": 1, "winrate": 0.0968801,
                    "scoreLead": -8.726382, "scoreMean": -0.726382, "scoreStdev": 6.47739, "scoreSelfplay": -5.56129,
                    "utility": -1.01275, "utilityLcb": -4.51275, "lcb": -1.15312, "prior": 0.0652113,
                    "weight": 0.647309, "pv": ["3-3"]
                }
            ]
        }"""
    }

    private fun parse(
        response: String = RESPONSE,
        player: Player = Player.First,
        fieldWidth: Int = FIELD_WIDTH,
        fieldHeight: Int = FIELD_HEIGHT,
    ): MoveAnalysis = parseMoveAnalysis(response.toJsonObject(), player, fieldWidth, fieldHeight)

    private fun String.toJsonObject(): JsonObject = Json.parseToJsonElement(this).jsonObject

    @Test
    fun allCandidateMovesAreParsed() {
        val analysis = parse()

        assertEquals(Player.First, analysis.player)
        assertEquals(3, analysis.moves.size)
        assertEquals(listOf(0, 1, 2), analysis.moves.map { it.order })
        assertEquals(239, analysis.totalVisits)
    }

    @Test
    fun theVerticalAxisIsInverted() {
        // `5-6` of the engine is the 6th row from the bottom, that is the 3rd one from the top of an 8-row field
        assertEquals(PositionXY(5, 3), parse().moves[0].positionXY)
        assertEquals(PositionXY(4, 6), parse().moves[1].positionXY)
    }

    @Test
    fun allValuesOfACandidateMoveAreParsed() {
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
    fun theVariationIsParsed() {
        assertEquals(
            listOf(PositionXY(5, 3), PositionXY(5, 6), PositionXY(3, 6), PositionXY(3, 3)),
            parse().moves[0].pv,
        )
        assertEquals(listOf(PositionXY(3, 6)), parse().moves[2].pv)
    }

    @Test
    fun theOptionalSymmetryIsParsed() {
        val symmetricMove = parse().moves[1]

        assertEquals(PositionXY(5, 3), symmetricMove.symmetryOf)
        assertEquals(1, symmetricMove.order)
        assertEquals(119, symmetricMove.visits)
    }

    @Test
    fun theCandidateMovesAreSortedByOrder() {
        val analysis = parse(
            """{"moveInfos":[{"move":"5-6","order":2},{"move":"4-3","order":0}]}"""
        )

        assertEquals(listOf(0, 2), analysis.moves.map { it.order })
        assertEquals(PositionXY(4, 6), analysis.best?.positionXY)
    }

    @Test
    fun aResponseWithoutCandidateMovesIsParsedAsAnEmptyOne() {
        assertTrue(parse("""{"id":"1"}""").moves.isEmpty())
        assertTrue(parse("""{"id":"1","moveInfos":[]}""").moves.isEmpty())
    }

    @Test
    fun theMovesOutsideOfTheFieldAndTheNonCoordinateOnesAreSkipped() {
        assertTrue(parse("""{"moveInfos":[{"move":"9-1","order":0}]}""").moves.isEmpty())
        assertTrue(parse("""{"moveInfos":[{"move":"1-9","order":0}]}""").moves.isEmpty())
        assertTrue(parse("""{"moveInfos":[{"move":"resign","order":0}]}""").moves.isEmpty())
        assertTrue(parse("""{"moveInfos":[{"move":"ground","order":0}]}""").moves.isEmpty())
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
            """{"moveInfos":[
                {"move":"5-6","order":0,"visits":10,"winrate":0.5,"scoreLead":4.0},
                {"move":"4-3","order":1,"visits":10,"winrate":0.5,"scoreLead":0.0}
            ]}"""
        )

        assertEquals(0.0, analysis.lossOf(analysis.moves[0]))
        // A half of the meaningful score lead loss weighted by `1 - WIN_RATE_LOSS_WEIGHT`
        assertEquals(0.175, analysis.lossOf(analysis.moves[1]), absoluteTolerance = 1e-9)
    }

    @Test
    fun theChosenMoveIsTheMoveTheEngineWouldPlay() {
        val chosenMove = assertNotNullMove(parse().chosenMove)

        assertEquals(PositionXY(5, 3), chosenMove.positionXY)
        assertEquals(Player.First, chosenMove.player)
    }

    @Test
    fun theChosenMoveIsAFinishingOneWhenTheEngineGroundsOrResigns() {
        val grounding = assertNotNullMove(parse("""{"chosenMove":"ground"}""", Player.Second).chosenMove)
        assertEquals(ExternalFinishReason.Grounding, grounding.externalFinishReason)
        assertEquals(Player.Second, grounding.player)

        // A lost position is resigned no matter which move the search has chosen
        val resign = assertNotNullMove(parse("""{"chosenMove":"5-6","resignReasonable":true}""").chosenMove)
        assertEquals(ExternalFinishReason.Resign, resign.externalFinishReason)
    }

    @Test
    fun theChosenMoveIsAbsentUnlessTheEngineReportedOne() {
        assertNull(parse("""{"id":"1"}""").chosenMove)
        // The engine reports a move it has none for as `null`
        assertNull(parse("""{"chosenMove":"null"}""").chosenMove)
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
     */
    @Test
    fun theOwnershipIsMappedToThePositionsRowByRowFromTheTop() {
        val ownershipValues = listOf(
            0.11, 0.12, 0.13, 0.14,
            0.21, 0.22, 0.23, 0.24,
            -0.31, -0.32, -0.33, 0.86,
        )
        val analysis = parse(
            """{"moveInfos":[{"move":"1-3","order":0,"pv":["1-3"]}],
               "ownership":[${ownershipValues.joinToString(",")}]}""",
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

        assertEquals(listOf(PositionXY(1, 1)), analysis.moves.single().pv)
    }

    @Test
    fun anOwnershipThatDoesNotCoverTheFieldIsRejected() {
        // A partial array can't be mapped to the positions, so it's dropped instead of being misaligned
        val analysis = parse(
            """{"moveInfos":[{"move":"1-3","order":0}],"ownership":[0.11,0.12,0.13]}""",
            fieldWidth = 4,
            fieldHeight = 3,
        )

        assertNull(analysis.ownership)
        assertEquals(1, analysis.moves.size)
    }

    /** The details of an analyzed move render the values with exactly two fraction digits. */
    @Test
    fun theValuesAreFormattedWithTwoFractionDigits() {
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

    private fun assertNotNullMove(move: org.dots.game.core.MoveInfo?): org.dots.game.core.MoveInfo {
        return move ?: error("The chosen move is expected to be reported")
    }
}
