package org.dots.game

import kotlinx.coroutines.runBlocking
import org.dots.game.core.BaseMode
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.InitPosGenType
import org.dots.game.core.InitPosType
import org.dots.game.core.LegalMove
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine tests of the GTP protocol, see [KataGoDotsAnalysisEngineTests] for the analysis one.
 *
 * Unlike the analysis engine, the GTP one keeps a position of its own, so most of the tests are about
 * the synchronization of that position with the field, see [GtpProtocol.sync].
 */
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfEnvironmentVariable(named = KataGoDotsEngineKey, matches = ".*")
@EnabledIfEnvironmentVariable(named = KataGoDotsModelKey, matches = ".*")
@EnabledIfEnvironmentVariable(named = KataGoDotsGtpConfigKey, matches = ".*")
class GtpEngineTests {
    companion object {
        /** The engine reads the config of the mode it's run in, thus a GTP config rather than an analysis one. */
        val TEST_GTP_CONFIG: String = System.getenv(KataGoDotsGtpConfigKey)!!
    }

    private val testRandom = Random(2)

    private val diagnostics = mutableListOf<Diagnostic>()

    /** Every engine is a process of its own, and a test class instance is created per test. */
    private val engines = mutableListOf<KataGoDotsEngine>()

    private val defaultEngine = initialize()!!

    /** The GTP protocol of [defaultEngine]: the position of the engine is no part of the API of the app. */
    private val gtp: GtpProtocol
        get() = assertNotNull(defaultEngine.gtp)

    private fun initialize(): KataGoDotsEngine? {
        return runBlocking {
            KataGoDotsEngine.initialize(
                KataGoDotsSettings(
                    KataGoDotsAnalysisEngineTests.TEST_ENGINE,
                    KataGoDotsAnalysisEngineTests.TEST_MODEL,
                    TEST_GTP_CONFIG,
                    protocol = EngineProtocol.Gtp,
                )
            ) {
                diagnostics.add(it)
                println(it)
            }
        }.also { engine -> engine?.let { engines.add(it) } }
    }

    @AfterTest
    fun stopEngines() {
        engines.forEach { it.close() }
    }

    @Test
    fun unsupportedRules() {
        runBlocking {
            val fieldWithUnsupportedRules = createField(captureByBorder = true)

            assertEquals(UnsupportedRules, gtp.getSyncType(fieldWithUnsupportedRules))
            assertFalse(UnsupportedRules.isSynchronized)

            assertNull(defaultEngine.analyze(fieldWithUnsupportedRules, Player.First))
            assertNull(defaultEngine.generateMove(fieldWithUnsupportedRules, Player.First))
        }
    }

    @Test
    fun fullResync() {
        runEngine {
            // Field with another size should cause a full resync
            val field2 = Field.create(
                Rules.create(
                    9, 9,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Cross,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )
            assertIs<FullSync>(gtp.getSyncType(field2))
        }
    }

    @Test
    fun noSync() {
        runEngine {
            assertIs<LegalMove>(it.makeMove(2, 2, Player.First))
            assertIs<MovesSync>(gtp.sync(it))
            assertIs<NoSync>(gtp.sync(it))
        }
    }

    @Test
    fun singleMoveAndUndo() {
        runEngine {
            // Check a single move
            assertIs<LegalMove>(it.makeMove(2, 2, Player.First))
            val syncTypeAfterFirstMove = assertIs<MovesSync>(gtp.getSyncType(it))
            assertEquals(0, syncTypeAfterFirstMove.undoMovesCount)
            assertEquals(listOf(MoveInfo(PositionXY(2, 2), Player.First)), syncTypeAfterFirstMove.moves)
            assertIs<MovesSync>(gtp.sync(it))

            // Check single undo
            assertIs<LegalMove>(it.unmakeMove())
            val syncTypeAfterUndo = assertIs<MovesSync>(gtp.getSyncType(it))
            assertEquals(1, syncTypeAfterUndo.undoMovesCount)
            assertTrue(syncTypeAfterUndo.moves.isEmpty())
        }
    }

    @Test
    fun complexSync() {
        runEngine {
            val field2 = it.clone()
            // Check undo + move
            assertIs<LegalMove>(it.makeMove(2, 3, Player.First))
            assertIs<MovesSync>(gtp.sync(it))
            assertIs<LegalMove>(field2.makeMove(2, 4, Player.First))

            val syncTypeWithUndoAndMove = assertIs<MovesSync>(gtp.getSyncType(field2))
            assertEquals(1, syncTypeWithUndoAndMove.undoMovesCount)
            assertEquals(listOf(MoveInfo(PositionXY(2, 4), Player.First)), syncTypeWithUndoAndMove.moves)
        }
    }

    /**
     * A finishing move is played on the engine as well, and the game it ends is scored the very same way
     * the field scores it.
     *
     * The score of a game that is *not* over is no part of it: `final_score` scores the position as it
     * stands, see [GtpProtocol.getGameResult], and the estimate it answers with is not a result of a game.
     */
    @Test
    fun grounding() {
        runEngine {
            assertIs<GameResult>(it.makeMove(MoveInfo.createFinishingMove(Player.First, ExternalFinishReason.Grounding)))
            assertIs<MovesSync>(gtp.sync(it))
            val engineGameResult = assertIs<GameResult.ScoreWin>(gtp.getGameResult())
            val fieldGameResult = it.gameResult as GameResult.ScoreWin
            assertEquals(fieldGameResult.winner, engineGameResult.winner)
            assertEquals(fieldGameResult.score, engineGameResult.score)

            // The move is taken back on both sides, so that the engine is ready for the game to go on
            assertIs<GameResult>(it.unmakeMove())
            assertIs<MovesSync>(gtp.sync(it))
            assertIs<NoSync>(gtp.getSyncType(it))
        }
    }

    /** @see grounding */
    @Test
    fun resigning() {
        runEngine {
            assertIs<GameResult>(it.makeMove(MoveInfo.createFinishingMove(Player.First, ExternalFinishReason.Resign)))
            assertIs<MovesSync>(gtp.sync(it))
            val engineGameResult = assertIs<GameResult.ResignWin>(gtp.getGameResult())
            val fieldGameResult = it.gameResult as GameResult.ResignWin
            assertEquals(fieldGameResult.winner, engineGameResult.winner)

            assertIs<GameResult>(it.unmakeMove())
            assertIs<MovesSync>(gtp.sync(it))
            assertIs<NoSync>(gtp.getSyncType(it))
        }
    }

    @Test
    fun generateMoves() {
        runEngine {
            val moveInfo = defaultEngine.generateMove(it, Player.First)!!
            assertNotNull(moveInfo.positionXY)
            assertEquals(Player.First, moveInfo.player)

            val moveInfo2 = defaultEngine.generateMove(it, Player.Second)!!
            assertNotNull(moveInfo2.positionXY)
            assertEquals(Player.Second, moveInfo2.player)

            val moveInfo3 = defaultEngine.generateMove(it, player = null)!!
            assertNotNull(moveInfo3.positionXY)
            assertEquals(Player.First, moveInfo3.player)
        }
    }

    /**
     * The clock of the game is stated to the engine before it thinks: `time_settings` is the control
     * it's played with and `time_left` is what the player has left of it, see `GTP_Extensions.md`
     * of KataGoDots.
     */
    @Test
    fun theClockOfTheGameIsStatedBeforeAMoveIsGenerated() {
        runEngine { field ->
            val clock = PlayerClock(
                TimeSettings(mainTimeSeconds = 300, turnTimeSeconds = 1),
                mainTimeLeft = 290.53,
                turnTimeLeft = 1.0,
            )

            val moveInfo = assertNotNull(defaultEngine.generateMove(field, Player.First, clock))
            assertNotNull(moveInfo.positionXY)

            val commands = diagnostics.mapNotNull { it.message.substringAfterOrNull("Command: ") }
            assertEquals("time_settings 300 1", commands.single { it.startsWith("time_settings") })
            // The time left is stated with a tenth of a second, and the time of the move takes the place
            // of the stones of a period, which a Bronstein delay has none of
            assertEquals("time_left P1 290.5 1.0", commands.single { it.startsWith("time_left") })
            assertTrue(
                diagnostics.none { it.severity == DiagnosticSeverity.Error },
                "The engine rejected the clock: $diagnostics",
            )
        }
    }

    /**
     * A player who has spent the whole main time is left with the time of the move alone, and that is
     * what the engine thinks within: the ten seconds a move of a Dots game is given by default,
     * let alone a slice of the main time, would take far longer.
     */
    @Test
    fun aMoveIsThoughtAboutWithinTheTimeThatIsLeft() {
        runEngine { field ->
            val turnTimes = listOf(5, 10, 15)
            for (turnTime in turnTimes) {
                val clock = PlayerClock(
                    TimeSettings(mainTimeSeconds = 300, turnTimeSeconds = turnTime),
                    mainTimeLeft = 0.0,
                    turnTimeLeft = turnTime.toDouble(),
                )

                val searchTime = TimeSource.Monotonic.markNow()
                assertNotNull(defaultEngine.generateMove(field, Player.First, clock))
                val elapsed = searchTime.elapsedNow()

                assertTrue(elapsed > (turnTime - 2).seconds && elapsed < (turnTime + 1).seconds)
            }
        }
    }

    /**
     * A game that is played without a clock leaves the engine with the limits of its own config, which
     * it has to be brought back to: the engine keeps the clock of the game that was played before this
     * one otherwise, and would think the moves of this game on that clock.
     */
    @Test
    fun theClockOfTheEngineItselfIsRestoredForAGameThatIsPlayedWithoutOne() {
        runEngine { field ->
            assertNotNull(defaultEngine.generateMove(field, Player.First))

            fun statedClocks() = diagnostics
                .mapNotNull { it.message.substringAfterOrNull("Command: ") }
                .filter { it.contains("time_settings") || it.startsWith("time_left") }

            // An engine that was never taken off the clock of its config is left alone
            assertTrue(statedClocks().isEmpty(), "The clock of no game at all was stated: ${statedClocks()}")

            val clock = PlayerClock(
                TimeSettings(mainTimeSeconds = 300, turnTimeSeconds = 1),
                mainTimeLeft = 0.0,
                turnTimeLeft = 1.0,
            )
            assertNotNull(defaultEngine.generateMove(field, Player.Second, clock))
            assertNotNull(defaultEngine.generateMove(field, Player.First))

            // The clock of the config is restored by the engine itself, and what is left of a clock
            // that is no longer played on is stated no more
            assertEquals(
                listOf("time_settings 300 1", "time_left P2 0.0 1.0", "kata-time_settings default"),
                statedClocks(),
            )
        }
    }

    @Test
    fun consecutiveMovesOfSameColor() {
        // It checks a bug in KataGoDots that already should have been fixed (otherwise this test would fail)
        runEngine {
            val _ = it.makeMove(4, 3, Player.Second)

            val moveInfo = defaultEngine.generateMove(it, Player.Second)!!
            assertIs<LegalMove>(it.makeMove(moveInfo))

            assertEquals(NoSync, gtp.getSyncType(it))

            assertNotNull(defaultEngine.generateMove(it, Player.First)!!)

            // `genmove` plays the generated move on the board of the engine, and the field knows nothing of it
            val syncResult = assertIs<MovesSync>(gtp.sync(it))
            assertEquals(1, syncResult.undoMovesCount)
            assertTrue(syncResult.moves.isEmpty())
        }
    }

    @Test
    fun analyze() {
        runEngine {
            val analysis = defaultEngine.analyze(it, Player.First)!!
            assertEquals(Player.First, analysis.player)

            val best = assertNotNull(analysis.best)
            assertEquals(0, best.order)
            assertTrue(best.visits > 0)
            assertTrue(best.winRate in 0.0..1.0)
            // The first move of a principal variation is the analyzed move itself
            assertEquals(best.positionXY, best.pv.firstOrNull())
            assertTrue(analysis.moves.all { move -> move.order in analysis.moves.indices })

            // The `play` line of the response names the move the engine would play
            val chosenMove = assertNotNull(analysis.chosenMove)
            assertEquals(Player.First, chosenMove.player)
            assertNotNull(analysis.moveAt(assertNotNull(chosenMove.positionXY)))

            // The analysis must not change the position of the engine
            assertIs<NoSync>(gtp.sync(it))

            assertEquals(Player.Second, defaultEngine.analyze(it, Player.Second)!!.player)
            assertEquals(Player.First, defaultEngine.analyze(it, player = null)!!.player)
        }
    }

    /**
     * A GTP engine has no query of a whole game, so its turns are analyzed one by one on a field
     * of their own, see `KataGoDotsProtocol.analyzeGame`.
     */
    @Test
    fun analyzeGame() {
        runEngine { field ->
            val moves = listOf(
                MoveInfo(PositionXY(3, 3), Player.First),
                MoveInfo(PositionXY(6, 6), Player.Second),
                MoveInfo(PositionXY(3, 6), Player.First),
            )
            val turnNumbers = listOf(0, 1, 2, 3)

            val analyzedTurns = mutableMapOf<Int, MoveAnalysis>()
            defaultEngine.analyzeGame(field, moves, turnNumbers) { turnNumber, analysis ->
                analyzedTurns[turnNumber] = analysis
            }

            assertEquals(turnNumbers.toSet(), analyzedTurns.keys)

            // Every turn is analyzed for the player whose turn it is rather than for a single one
            assertEquals(listOf(Player.First, Player.Second, Player.First, Player.Second), turnNumbers.map {
                analyzedTurns.getValue(it).player
            })

            for (turnNumber in turnNumbers) {
                val best = assertNotNull(analyzedTurns.getValue(turnNumber).best, "turn $turnNumber")
                assertTrue(best.winRate in 0.0..1.0, "The win rate of the turn $turnNumber is ${best.winRate}")
                assertTrue(best.visits > 0)
            }

            // The turns are replayed on a field of their own, thus the analyzed one is left untouched
            assertEquals(0, field.currentMoveNumber)
        }
    }

    /**
     * Surrounds a single opponent dot near the bottom of the field and expects the engine to report
     * that very position as captured. A flipped or transposed ownership array would put the confident
     * value elsewhere, so the mirrored position is asserted to stay unclaimed.
     */
    @Test
    fun theOwnershipIsReportedForTheCapturedPositions() {
        runBlocking {
            val field = createField(initPosType = InitPosType.Empty)

            val capturedPosition = PositionXY(3, 7)
            // The dots surrounding [capturedPosition], interleaved with the far away moves of the opponent
            val surroundingMoves = listOf(
                PositionXY(2, 8), PositionXY(3, 8), PositionXY(4, 8), PositionXY(4, 7),
                PositionXY(4, 6), PositionXY(3, 6), PositionXY(2, 6), PositionXY(2, 7),
            )
            val opponentMoves = listOf(
                capturedPosition,
                PositionXY(8, 1), PositionXY(7, 1), PositionXY(6, 1),
                PositionXY(5, 1), PositionXY(8, 2), PositionXY(7, 2),
            )

            for (index in surroundingMoves.indices) {
                val surroundingMove = surroundingMoves[index]
                assertIs<LegalMove>(field.makeMove(surroundingMove.x, surroundingMove.y, Player.First))
                opponentMoves.elementAtOrNull(index)?.let {
                    assertIs<LegalMove>(field.makeMove(it.x, it.y, Player.Second))
                }
            }

            assertEquals(1, field.player1Score, "The opponent dot is expected to be captured")

            val analysis = defaultEngine.analyze(field, Player.First, withOwnership = true)!!
            assertEquals(field.width * field.height, analysis.ownership?.size)

            val capturedOwnership = assertNotNull(analysis.ownershipOf(capturedPosition))
            assertTrue(capturedOwnership > 0.5, "The captured position ownership is $capturedOwnership")

            // The vertically mirrored position is empty, so it must not be claimed by anybody
            val mirroredOwnership = assertNotNull(analysis.ownershipOf(PositionXY(3, 2)))
            assertTrue(mirroredOwnership < 0.5, "The mirrored position ownership is $mirroredOwnership")
        }
    }

    @Test
    fun theOwnershipIsNotReportedUnlessItIsRequested() {
        runEngine {
            assertNull(defaultEngine.analyze(it, Player.First)!!.ownership)
        }
    }

    /**
     * The engine installs the start position of its config (`CROSS` by default) on `boardsize`,
     * so an empty start position has to be dropped from the engine explicitly.
     */
    @Test
    fun anEmptyStartPositionIsSynchronized() {
        runBlocking {
            val field = createField(initPosType = InitPosType.Empty)
            assertEquals(0, field.initialMovesCount)

            assertIs<LegalMove>(field.makeMove(3, 3, Player.First))
            assertIs<LegalMove>(field.makeMove(4, 4, Player.Second))

            assertIs<FullSync>(gtp.sync(field))
            // Only reachable if the default start position of the engine was dropped
            assertIs<NoSync>(gtp.getSyncType(field))
        }
    }

    /**
     * A loaded game may contain setup dots that don't fit the recognized [Rules.initPosType] pattern.
     * They land in [Rules.remainingInitMoves], they still belong to the start position,
     * and replaying them as ordinary moves would collide with the dots already on the board.
     *
     * The commands are asserted rather than the resulting engine state, because `get_position`
     * reports a start position only when it matches a pattern the engine recognizes,
     * so a custom one can't be read back.
     */
    @Test
    fun aStartPositionBeyondTheRecognizedPatternIsSentAsAStartPosition() {
        runBlocking {
            val crossMoves = InitPosType.Cross.generateMoves(8, 8)!!
            val extraSetupMove = MoveInfo(PositionXY(1, 1), Player.First)

            val rules = Rules.createAndDetectInitPos(
                8, 8,
                captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                suicideAllowed = true,
                initialMoves = crossMoves + extraSetupMove,
                komi = 0.0,
                random = testRandom,
                initPosGenType = InitPosGenType.Static,
            ).rules

            // The extra dot doesn't fit the cross, thus it's kept aside of the recognized pattern
            assertEquals(InitPosType.Cross, rules.initPosType)
            assertEquals(crossMoves.size, rules.initialMoves.size)
            assertEquals(listOf(extraSetupMove), rules.remainingInitMoves)

            val field = Field.create(rules)
            assertEquals(crossMoves.size + 1, field.initialMovesCount)

            assertIs<LegalMove>(field.makeMove(1, 8, Player.First))
            assertIs<LegalMove>(field.makeMove(8, 1, Player.Second))

            assertIs<FullSync>(gtp.sync(field))

            val commands = diagnostics.mapNotNull { it.message.substringAfterOrNull("Command: ") }
            val setPositionCommand = commands.single { it.startsWith("set_position") }
            val playCommand = commands.single { it.startsWith("play") }

            // The whole start position is sent as one, the ordinary moves are only the two played ones
            assertEquals(field.initialMovesCount, setPositionCommand.countGtpMoves(), setPositionCommand)
            assertEquals(2, playCommand.countGtpMoves(), playCommand)

            // The vertical axis is inverted in GTP, so the setup dot (1;1) of an 8 rows high field is `1-8`
            assertTrue("P1 1-8" in setPositionCommand, setPositionCommand)
            assertFalse("P1 1-8" in playCommand, playCommand)
        }
    }

    /**
     * The app allows fields up to 39x39, while the engine is compiled for 39x32 at most,
     * so a higher field makes it reject `boardsize`.
     */
    @Test
    fun aRejectedCommandIsReportedInsteadOfCrashing() {
        runBlocking {
            val tooHighField = Field.create(
                Rules.create(39, 39,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Cross,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )

            assertEquals(SyncFailed, gtp.sync(tooHighField))
            assertFalse(SyncFailed.isSynchronized)

            // Nothing may be computed on a position the engine doesn't share
            assertNull(defaultEngine.generateMove(tooHighField, Player.First))
            assertNull(defaultEngine.analyze(tooHighField, Player.First))

            val errors = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
            assertTrue(errors.any { "unacceptable size" in it.message }, "Reported diagnostics: $diagnostics")

            // The engine stays usable for the next position
            assertNotNull(defaultEngine.analyze(createField(), Player.First))
        }
    }

    private fun String.substringAfterOrNull(prefix: String): String? =
        if (startsWith(prefix)) substring(prefix.length) else null

    private fun String.countGtpMoves(): Int = split(" ").count { it == "P1" || it == "P2" }

    private fun createField(
        captureByBorder: Boolean = false,
        initPosType: InitPosType = InitPosType.Cross,
    ): Field = Field.create(
        Rules.create(8, 8,
            captureByBorder = captureByBorder, baseMode = BaseMode.AtLeastOneOpponentDot,
            suicideAllowed = true, initPosType = initPosType,
            random = testRandom,
            initPosGenType = InitPosGenType.Static,
            komi = 0.0
        )
    )

    private fun runEngine(action: suspend (field: Field) -> Unit) {
        runBlocking {
            val field = createField()
            assertIs<FullSync>(gtp.sync(field))
            action(field)
        }
    }
}
