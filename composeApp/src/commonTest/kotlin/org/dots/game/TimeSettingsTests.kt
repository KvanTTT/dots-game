package org.dots.game

import org.dots.game.core.Field
import org.dots.game.core.Game
import org.dots.game.core.GameTree
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.sgf.Sgf
import org.dots.game.sgf.SgfWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** A move of SGF carries the time of the player who made it alone, that is `BL` or `WL` but not both. */
    @Test
    fun theTimeLeftOfAMoveIsTheOneOfTheLastMoveOfEveryPlayer() {
        val gameTree = playedGame()
        // The time limit of the game is what a player who hasn't moved yet has left
        val game = Game(gameTree).apply { time = 300.0 }

        val firstMove = assertNotNull(gameTree.rootNode.children.single())
        firstMove.setMainTimeLeft(Player.First, 290.0)
        val secondMove = assertNotNull(firstMove.children.single())
        secondMove.setMainTimeLeft(Player.Second, 280.0)
        val thirdMove = assertNotNull(secondMove.children.single())
        thirdMove.setMainTimeLeft(Player.First, 270.0)

        // The opponent hasn't moved yet, so nothing of their time is spent
        assertEquals(MainTimeLeft(290.0, 300.0), firstMove.mainTimeLeft(game))
        assertEquals(MainTimeLeft(300.0, 300.0), gameTree.rootNode.mainTimeLeft(game))
        assertEquals(MainTimeLeft(290.0, 280.0), secondMove.mainTimeLeft(game))
        // The time of the player who didn't move is the one of their last move
        assertEquals(MainTimeLeft(270.0, 280.0), thirdMove.mainTimeLeft(game))
    }

    @Test
    fun theTimeLeftOfTheGameIsWhatBothPlayersStartWith() {
        val gameTree = playedGame()
        val game = Game(gameTree).apply {
            player1TimeLeft = 315.0
            player2TimeLeft = 312.0
            // The time limit is only used by a player the game keeps no time of
            time = 300.0
        }

        assertEquals(MainTimeLeft(315.0, 312.0), gameTree.rootNode.mainTimeLeft(game))

        val firstMove = assertNotNull(gameTree.rootNode.children.single())
        firstMove.setMainTimeLeft(Player.First, 290.0)
        assertEquals(MainTimeLeft(290.0, 312.0), firstMove.mainTimeLeft(game))
    }

    @Test
    fun aGameThatKeepsNoTimeOfAPlayerHasNoTimeToRender() {
        val gameTree = playedGame()
        val firstMove = assertNotNull(gameTree.rootNode.children.single())

        assertNull(firstMove.mainTimeLeft(Game(gameTree)))
        assertNull(firstMove.mainTimeLeft(game = null))

        // The time of a single player is not enough to render the clock of a game
        firstMove.setMainTimeLeft(Player.First, 290.0)
        assertNull(firstMove.mainTimeLeft(Game(gameTree)))

        // A player the game keeps no time of has spent nothing of the time limit
        assertEquals(MainTimeLeft(290.0, 300.0), firstMove.mainTimeLeft(Game(gameTree).apply { time = 300.0 }))
    }

    /**
     * A move spends the time of the player who made it, so a move that records none leaves the clock
     * unknown rather than unchanged.
     */
    @Test
    fun aMoveThatRecordsNoTimeOfItsPlayerHidesTheClock() {
        val gameTree = playedGame()
        val game = Game(gameTree).apply { time = 300.0 }

        val firstMove = assertNotNull(gameTree.rootNode.children.single())
        firstMove.setMainTimeLeft(Player.First, 290.0)
        // The move of the second player records no time of it
        val secondMove = assertNotNull(firstMove.children.single())
        val thirdMove = assertNotNull(secondMove.children.single())
        thirdMove.setMainTimeLeft(Player.First, 270.0)

        assertEquals(MainTimeLeft(290.0, 300.0), firstMove.mainTimeLeft(game))
        assertNull(secondMove.mainTimeLeft(game))
        // The time of the second player stays unknown for the moves that follow
        assertNull(thirdMove.mainTimeLeft(game))
        // The root records no move at all, so both players still have the time limit of the game
        assertEquals(MainTimeLeft(300.0, 300.0), gameTree.rootNode.mainTimeLeft(game))
    }

    /** Only the player who made a move has spent any time on it, so the other one records none. */
    @Test
    fun aMoveRecordsTheTimeOfItsPlayerAlone() {
        val gameTree = playedGame()

        val firstMove = assertNotNull(gameTree.rootNode.children.single())
        firstMove.setMainTimeLeft(Player.First, 289.96)
        // The time of a move is kept with a tenth of a second, the way SGF renders it
        assertEquals(290.0, firstMove.player1TimeLeft)
        assertNull(firstMove.player2TimeLeft)

        val secondMove = assertNotNull(firstMove.children.single())
        secondMove.setMainTimeLeft(Player.Second, 280.0)
        assertEquals(280.0, secondMove.player2TimeLeft)
        assertNull(secondMove.player1TimeLeft)
    }

    /**
     * A move records the time of the player who made it alone, so it's the game that tells the time
     * control both players start with: the time limit (`TM`) and the time of a move (`OT`) of SGF.
     */
    @Test
    fun theTimeControlOfAGameSurvivesSgf() {
        val gameTree = playedGame()
        val game = Game(gameTree).apply { setTimeControl(TIME_SETTINGS) }
        assertNotNull(gameTree.rootNode.children.single()).setMainTimeLeft(Player.First, 290.0)

        val sgf = SgfWriter.write(Games(listOf(game)))
        assertTrue("TM[300]" in sgf, sgf)
        assertTrue("OT[25]" in sgf, sgf)

        val loadedGame = Sgf.parseAndConvert(sgf) { }.single()
        assertEquals(TIME_SETTINGS, loadedGame.timeSettings())
        // A loaded game reports the time both players start with, the way a played one does
        assertEquals(MainTimeLeft(300.0, 300.0), loadedGame.gameTree.rootNode.mainTimeLeft(loadedGame))
        assertEquals(
            MainTimeLeft(290.0, 300.0),
            assertNotNull(loadedGame.gameTree.rootNode.children.single()).mainTimeLeft(loadedGame),
        )
    }

    private fun playedGame(): GameTree = GameTree(Field.create(Rules.Standard)).apply {
        addChild(MoveInfo(PositionXY(3, 3), Player.First))
        addChild(MoveInfo(PositionXY(4, 4), Player.Second))
        addChild(MoveInfo(PositionXY(5, 5), Player.First))
    }
}
