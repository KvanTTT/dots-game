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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

const val KataGoDotsEngineKey = "KataGoDotsEngine"
const val KataGoDotsModelKey = "KataGoDotsModel"
const val KataGoDotsConfigKey = "KataGoDotsConfig"

@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfEnvironmentVariable(named = KataGoDotsEngineKey, matches = ".*")
@EnabledIfEnvironmentVariable(named = KataGoDotsModelKey, matches = ".*")
@EnabledIfEnvironmentVariable(named = KataGoDotsConfigKey, matches = ".*")
class KataGoDotsEngineTests {
    companion object {
        val TEST_ENGINE: String = System.getenv(KataGoDotsEngineKey)!!
        val TEST_MODEL: String = System.getenv(KataGoDotsModelKey)!!
        val TEST_CONFIG: String = System.getenv(KataGoDotsConfigKey)!!
    }

    private val testRandom = Random(2)

    private val defaultEngine = initialize(KataGoDotsSettings(
        TEST_ENGINE,
        TEST_MODEL,
        TEST_CONFIG
    ))!!

    private fun initialize(kataGoDotsSettings: KataGoDotsSettings): KataGoDotsEngine? {
        return runBlocking {
            KataGoDotsEngine.initialize(kataGoDotsSettings) {
                println(it)
            }
        }
    }

    @Test
    fun incorrectExe() {
        assertNull(initialize(KataGoDotsSettings(
            "invalid path",
            TEST_MODEL,
            TEST_CONFIG,
        )))
    }

    @Test
    fun incorrectModel() {
        assertNull(initialize(KataGoDotsSettings(
            TEST_ENGINE,
            "invalid model",
            TEST_CONFIG,
        )))
    }

    @Test
    fun incorrectConfig() {
        assertNull(initialize(KataGoDotsSettings(
            TEST_ENGINE,
            TEST_MODEL,
            "invalid config",
        )))
    }

    @Test
    fun unsupportedRules() {
        runEngine {
            val fieldWithUnsupportedRules = Field.create(
                Rules.create(8, 8,
                    captureByBorder = true, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Cross,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )
            assertEquals(UnsupportedRules, defaultEngine.getSyncType(fieldWithUnsupportedRules))
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
            assertIs<FullSync>(defaultEngine.getSyncType(field2))
        }
    }

    @Test
    fun noSync() {
        runEngine {
            assertIs<LegalMove>(it.makeMove(2, 2, Player.First))
            assertIs<MovesSync>(defaultEngine.sync(it))
            assertIs<NoSync>(defaultEngine.sync(it))
        }
    }

    @Test
    fun singleMoveAndUndo() {
        runEngine {
            // Check a single move
            assertIs<LegalMove>(it.makeMove(2, 2, Player.First))
            val syncTypeAfterFirstMove = assertIs<MovesSync>(defaultEngine.getSyncType(it))
            assertEquals(0, syncTypeAfterFirstMove.undoMovesCount)
            assertEquals(listOf(MoveInfo(PositionXY(2, 2), Player.First)), syncTypeAfterFirstMove.moves)
            assertIs<MovesSync>(defaultEngine.sync(it))

            // Check single undo
            assertIs<LegalMove>(it.unmakeMove())
            val syncTypeAfterUndo = assertIs<MovesSync>(defaultEngine.getSyncType(it))
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
            assertIs<MovesSync>(defaultEngine.sync(it))
            assertIs<LegalMove>(field2.makeMove(2, 4, Player.First))

            val syncTypeWithUndoAndMove = assertIs<MovesSync>(defaultEngine.getSyncType(field2))
            assertEquals(1, syncTypeWithUndoAndMove.undoMovesCount)
            assertEquals(listOf(MoveInfo(PositionXY(2, 4), Player.First)), syncTypeWithUndoAndMove.moves)
        }
    }

    @Test
    fun grounding() {
        runEngine {
            assertNull(defaultEngine.getGameResult())

            assertIs<GameResult>(it.makeMove(MoveInfo.createFinishingMove(Player.First, ExternalFinishReason.Grounding)))
            assertIs<MovesSync>(defaultEngine.sync(it))
            val engineGameResult = assertIs<GameResult.ScoreWin>(defaultEngine.getGameResult())
            val fieldGameResult = it.gameResult as GameResult.ScoreWin
            assertEquals(fieldGameResult.winner, engineGameResult.winner)
            assertEquals(fieldGameResult.score, engineGameResult.score)

            assertIs<GameResult>(it.unmakeMove())
            assertIs<MovesSync>(defaultEngine.sync(it))
            assertNull(defaultEngine.getGameResult())
        }
    }

    @Test
    fun resigning() {
        runEngine {
            assertNull(defaultEngine.getGameResult())

            assertIs<GameResult>(it.makeMove(MoveInfo.createFinishingMove(Player.First, ExternalFinishReason.Resign)))
            assertIs<MovesSync>(defaultEngine.sync(it))
            val engineGameResult = assertIs<GameResult.ResignWin>(defaultEngine.getGameResult())
            val fieldGameResult = it.gameResult as GameResult.ResignWin
            assertEquals(fieldGameResult.winner, engineGameResult.winner)

            assertIs<GameResult>(it.unmakeMove())
            assertIs<MovesSync>(defaultEngine.sync(it))
            assertNull(defaultEngine.getGameResult())
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

    @Test
    fun consecutiveMovesOfSameColor() {
        // It checks a bug in KataGoDots that already should have been fixed (otherwise this test would fail)
        runEngine {
            val _ = it.makeMove(4, 3, Player.Second)

            val moveInfo = defaultEngine.generateMove(it, Player.Second)!!
            assertIs<LegalMove>(it.makeMove(moveInfo))

            assertEquals(NoSync, defaultEngine.getSyncType(it))

            assertNotNull(defaultEngine.generateMove(it, Player.First)!!)

            val syncResult = defaultEngine.sync(it) as MovesSync
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

            // The analysis must not change the position of the engine
            assertIs<NoSync>(defaultEngine.sync(it))

            assertEquals(Player.Second, defaultEngine.analyze(it, Player.Second)!!.player)
            assertEquals(Player.First, defaultEngine.analyze(it, player = null)!!.player)
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
            val field = Field.create(
                Rules.create(8, 8,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Empty,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )

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

            for ((index, surroundingMove = value) in surroundingMoves.withIndex()) {
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
            val field = Field.create(
                Rules.create(8, 8,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Empty,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )
            assertEquals(0, field.initialMovesCount)

            assertIs<LegalMove>(field.makeMove(3, 3, Player.First))
            assertIs<LegalMove>(field.makeMove(4, 4, Player.Second))

            assertIs<FullSync>(defaultEngine.sync(field))
            // Only reachable if the default start position of the engine was dropped
            assertIs<NoSync>(defaultEngine.getSyncType(field))
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
        val diagnostics = mutableListOf<Diagnostic>()
        val engine = runBlocking {
            KataGoDotsEngine.initialize(KataGoDotsSettings(TEST_ENGINE, TEST_MODEL, TEST_CONFIG)) {
                diagnostics.add(it)
            }
        }!!

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

            assertIs<FullSync>(engine.sync(field))

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
        val diagnostics = mutableListOf<Diagnostic>()
        val engine = runBlocking {
            KataGoDotsEngine.initialize(KataGoDotsSettings(TEST_ENGINE, TEST_MODEL, TEST_CONFIG)) {
                diagnostics.add(it)
            }
        }!!

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

            assertEquals(SyncFailed, engine.sync(tooHighField))
            assertFalse(SyncFailed.isSynchronized)

            // Nothing may be computed on a position the engine doesn't share
            assertNull(engine.generateMove(tooHighField, Player.First))
            assertNull(engine.analyze(tooHighField, Player.First))

            val errors = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
            assertTrue(errors.any { "unacceptable size" in it.message }, "Reported diagnostics: $diagnostics")
        }
    }

    private fun String.substringAfterOrNull(prefix: String): String? =
        if (startsWith(prefix)) substring(prefix.length) else null

    private fun String.countGtpMoves(): Int = split(" ").count { it == "P1" || it == "P2" }

    private fun runEngine(action: suspend (field: Field) -> Unit) {
        runBlocking {
            val field = Field.create(
                Rules.create(8, 8,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Cross,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )
            assertIs<FullSync>(defaultEngine.sync(field))
            action(field)
        }
    }
}