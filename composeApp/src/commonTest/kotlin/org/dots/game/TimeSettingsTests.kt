package org.dots.game

import org.dots.game.core.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The time of a move is spent before the main one and it's not banked, that is a Bronstein delay. */
class TimeSettingsTests {
    companion object {
        private val TIME_SETTINGS = TimeSettings(mainTimeMinutes = 5, turnTimeSeconds = 25)
    }

    private val initialSpending = TimeSpending.of(TIME_SETTINGS)

    @Test
    fun theClockStartsWithTheWholeTime() {
        assertEquals(300.0, initialSpending.mainTimeLeft[Player.First])
        assertEquals(300.0, initialSpending.mainTimeLeft[Player.Second])
        assertEquals(25.0, initialSpending.turnTimeLeft)
    }

    @Test
    fun aMoveWithinItsOwnTimeCostsNoMainTime() {
        val spending = initialSpending.spend(Player.First, 10.0)

        assertEquals(15.0, spending.turnTimeLeft)
        assertEquals(300.0, spending.mainTimeLeft[Player.First])
    }

    @Test
    fun onlyWhatAMoveTakesOnTopOfItsOwnTimeIsChargedToTheMainOne() {
        val spending = initialSpending.spend(Player.First, 30.0)

        assertEquals(0.0, spending.turnTimeLeft)
        assertEquals(295.0, spending.mainTimeLeft[Player.First])
        // The clock of the opponent is untouched
        assertEquals(300.0, spending.mainTimeLeft[Player.Second])
    }

    @Test
    fun theTimeOfAMoveThatIsLeftIsNotBanked() {
        val spending = initialSpending.spend(Player.First, 5.0).startTurn(TIME_SETTINGS)

        assertEquals(25.0, spending.turnTimeLeft)
        assertEquals(300.0, spending.mainTimeLeft[Player.First])
    }

    /** The clock is ticked rather than charged at once, and the two must not diverge. */
    @Test
    fun theTimeIsSpentTickByTickTheSameWayAsAtOnce() {
        var tickByTick = initialSpending
        repeat(300) { tickByTick = tickByTick.spend(Player.First, 0.1) }

        val atOnce = initialSpending.spend(Player.First, 30.0)
        assertEquals(atOnce.turnTimeLeft, tickByTick.turnTimeLeft, absoluteTolerance = 1e-9)
        assertEquals(
            atOnce.mainTimeLeft[Player.First],
            tickByTick.mainTimeLeft[Player.First],
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun theMainTimeIsSpentUntilItIsOver() {
        val spending = initialSpending.spend(Player.First, 1000.0)

        assertEquals(0.0, spending.mainTimeLeft[Player.First])
        assertTrue(spending.isTimeUp(Player.First))
        assertFalse(spending.isTimeUp(Player.Second))
    }

    /** A game without the main time is a game where a move that is not made in time is lost. */
    @Test
    fun theTimeOfAMoveIsTheWholeTimeWhenThereIsNoMainOne() {
        val turnTimeOnly = TimeSpending.of(TimeSettings(mainTimeMinutes = 0, turnTimeSeconds = 25))

        assertFalse(turnTimeOnly.spend(Player.First, 24.0).isTimeUp(Player.First))
        assertTrue(turnTimeOnly.spend(Player.First, 25.0).isTimeUp(Player.First))
    }

    @Test
    fun theClockIsRenderedWithTheSecondsOfAMinute() {
        assertEquals("5:00", 300.0.toClockString())
        assertEquals("0:25", 25.0.toClockString())
        assertEquals("1:05", 65.0.toClockString())
        assertEquals("0:00", 0.0.toClockString())
        // A clock shows `0:00` when the time is really over rather than during the last second of it
        assertEquals("0:01", 0.1.toClockString())
        assertEquals("0:10", 9.2.toClockString())
        assertEquals("0:00", (-1.0).toClockString())
    }

    /** The time left is stamped on a move as the `BL` and `WL` properties of SGF, which are read by people. */
    @Test
    fun theTimeLeftIsStoredWithATenthOfASecond() {
        assertEquals(123.4, 123.44.roundToTenthOfSecond())
        assertEquals(123.5, 123.46.roundToTenthOfSecond())
        assertEquals(300.0, 300.0.roundToTenthOfSecond())
        // The rendering of such a value is short as well, which is what the property of a move is stored as
        assertEquals("123.4", 123.44.roundToTenthOfSecond().toString())
        assertEquals("0.3", 0.29999999.roundToTenthOfSecond().toString())
    }

    @Test
    fun aTimeControlOfZerosIsNoTimeControl() {
        assertFalse(TimeSettings(mainTimeMinutes = 0, turnTimeSeconds = 0).isEnabled)
        assertTrue(TimeSettings(mainTimeMinutes = 0, turnTimeSeconds = 25).isEnabled)
        assertTrue(TimeSettings(mainTimeMinutes = 5, turnTimeSeconds = 0).isEnabled)
    }
}
