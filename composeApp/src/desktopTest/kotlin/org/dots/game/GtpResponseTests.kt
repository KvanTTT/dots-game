package org.dots.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The test data are the real engine outputs, including the warnings it writes into the same stream. */
class GtpResponseTests {
    companion object {
        private const val WARNING =
            "Warning: Model test2-katadots_b15 was trained on a different KataGo version."
    }

    @Test
    fun aSucceededCommandIsNotAnError() {
        val response = toGtpResponse(listOf("= KataGoDots"))

        assertFalse(response.isError)
        assertEquals("KataGoDots", response.message)
        assertTrue(response.extraLines.isEmpty())
    }

    @Test
    fun aFailedCommandIsAnErrorAndItsMarkerIsStripped() {
        val response = toGtpResponse(listOf("? Illegal move Player2 20-17"))

        assertTrue(response.isError)
        assertEquals("Illegal move Player2 20-17", response.message)
    }

    @Test
    fun anEmptyResponseOfASucceededCommandIsNotAnError() {
        val response = toGtpResponse(listOf("= "))

        assertFalse(response.isError)
        assertEquals("", response.message)
    }

    @Test
    fun theWarningsPrecedingTheMarkerAreReportedSeparately() {
        val response = toGtpResponse(listOf(WARNING, WARNING, "= 39:32"))

        assertFalse(response.isError)
        assertEquals("39:32", response.message)
        assertEquals(listOf(WARNING, WARNING), response.extraLines)
    }

    @Test
    fun theMarkerIsFoundEvenWhenItIsNeitherTheFirstNorTheLastLine() {
        // The analysis commands answer with the marker, the reports and the recommended move
        val infoLine = "info move 5-6 visits 119 order 0 pv 5-6 5-3"
        val response = toGtpResponse(listOf("=", infoLine, "play 5-6"))

        assertFalse(response.isError)
        // The payload of a multiline response must not be mistaken for a marked line
        assertEquals("play 5-6", response.message)
        assertEquals(listOf("=", infoLine, "play 5-6"), response.allLines)
    }

    @Test
    fun aFailureIsDetectedBehindTheWarnings() {
        val response = toGtpResponse(listOf(WARNING, "? unknown command"))

        assertTrue(response.isError)
        assertEquals("unknown command", response.message)
    }

    @Test
    fun anUnmarkedOutputIsNotReportedAsAFailure() {
        assertFalse(toGtpResponse(emptyList()).isError)
        assertEquals("", toGtpResponse(emptyList()).message)

        // Only a `?` marker means a failure, a stray line is not enough to claim one
        assertFalse(toGtpResponse(listOf(WARNING)).isError)
        assertEquals(WARNING, toGtpResponse(listOf(WARNING)).message)
    }
}
