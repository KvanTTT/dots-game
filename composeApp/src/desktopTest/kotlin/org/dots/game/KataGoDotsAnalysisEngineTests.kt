package org.dots.game

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.dots.game.core.BaseMode
import org.dots.game.core.Field
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

const val KataGoDotsEngineKey = "KataGoDotsEngine"
const val KataGoDotsModelKey = "KataGoDotsModel"
const val KataGoDotsConfigKey = "KataGoDotsConfig"
const val KataGoDotsGtpConfigKey = "KataGoDotsGtpConfig"

@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfEnvironmentVariable(named = KataGoDotsEngineKey, matches = ".*")
@EnabledIfEnvironmentVariable(named = KataGoDotsModelKey, matches = ".*")
@EnabledIfEnvironmentVariable(named = KataGoDotsConfigKey, matches = ".*")
class KataGoDotsAnalysisEngineTests {
    companion object {
        val TEST_ENGINE: String = System.getenv(KataGoDotsEngineKey)!!
        val TEST_MODEL: String = System.getenv(KataGoDotsModelKey)!!
        val TEST_CONFIG: String = System.getenv(KataGoDotsConfigKey)!!
    }

    private val testRandom = Random(2)

    private val diagnostics = mutableListOf<Diagnostic>()

    /** Every engine is a process of its own, and a test class instance is created per test. */
    private val engines = mutableListOf<KataGoDotsEngine>()

    private val defaultEngine = initialize(KataGoDotsSettings(
        TEST_ENGINE,
        TEST_MODEL,
        TEST_CONFIG
    ))!!

    private fun initialize(kataGoDotsSettings: KataGoDotsSettings): KataGoDotsEngine? {
        return runBlocking {
            KataGoDotsEngine.initialize(kataGoDotsSettings) {
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
    fun unsupportedRulesAreNotEvenQueried() {
        runBlocking {
            val fieldWithUnsupportedRules = createField(captureByBorder = true)

            assertNull(defaultEngine.analyze(fieldWithUnsupportedRules, Player.First))
            assertNull(defaultEngine.generateMove(fieldWithUnsupportedRules, Player.First))
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

            val moveInfo2 = defaultEngine.generateMove(it, Player.Second)!!
            assertIs<LegalMove>(it.makeMove(moveInfo2))
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

            // The very same query reports the move the engine would play, so that no second engine is needed
            val chosenMove = assertNotNull(analysis.chosenMove)
            assertEquals(Player.First, chosenMove.player)
            assertNotNull(analysis.moveAt(assertNotNull(chosenMove.positionXY)))

            assertEquals(Player.Second, defaultEngine.analyze(it, Player.Second)!!.player)
            assertEquals(Player.First, defaultEngine.analyze(it, player = null)!!.player)
        }
    }

    /**
     * A whole game is analyzed by a single query: the engine reports every turn of it on its own,
     * and the turn of a response tells which position it is about.
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
                val position = assertNotNull(analyzedTurns.getValue(turnNumber).position, "turn $turnNumber")
                assertTrue(position.winRate in 0.0..1.0, "The win rate of the turn $turnNumber is ${position.winRate}")
                assertTrue(position.visits > 0)
            }
        }
    }

    /**
     * A query carries the whole position rather than the difference from the previous one, so nothing
     * of the previous query may leak into the next one, an undone move included.
     */
    @Test
    fun everyQueryIsAnalyzedOnItsOwn() {
        runEngine { field ->
            val analyzedMove = assertNotNull(defaultEngine.analyze(field, Player.First)!!.best).positionXY

            assertIs<LegalMove>(field.makeMove(analyzedMove.x, analyzedMove.y, Player.First))
            // The position is taken now, thus it's no longer a candidate
            assertNull(defaultEngine.analyze(field, Player.Second)!!.moveAt(analyzedMove))

            assertIs<LegalMove>(field.unmakeMove())
            assertNotNull(defaultEngine.analyze(field, Player.First)!!.moveAt(analyzedMove))
        }
    }

    /**
     * The engine searches several queries at once, so the responses are matched to the queries by their id
     * rather than by their order.
     */
    @Test
    fun severalQueriesAreAnsweredAtOnce() {
        runEngine { field ->
            val analyses = runBlocking {
                listOf(
                    async { defaultEngine.analyze(field, Player.First) },
                    async { defaultEngine.analyze(field, Player.Second) },
                    async { defaultEngine.analyze(field, Player.First, withOwnership = true) },
                ).awaitAll()
            }

            assertEquals(listOf(Player.First, Player.Second, Player.First), analyses.map { it?.player })
            assertNull(analyses[0]?.ownership)
            assertEquals(field.width * field.height, analyses[2]?.ownership?.size)
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
     * The engine installs the start position of its config (`CROSS` by default) on its own,
     * so an empty start position has to be dropped from a query explicitly.
     */
    @Test
    fun anEmptyStartPositionIsNotFilledByTheEngine() {
        runBlocking {
            val field = createField(initPosType = InitPosType.Empty)
            assertEquals(0, field.initialMovesCount)

            // The very position the engine would fill with a dot of its own start position: it would reject
            // the move as an illegal one, and the whole query along with it
            assertIs<LegalMove>(field.makeMove(4, 4, Player.First))

            assertNotNull(defaultEngine.analyze(field, Player.Second))
        }
    }

    /**
     * A loaded game may contain setup dots that don't fit the recognized [Rules.initPosType] pattern.
     * They land in [Rules.remainingInitMoves], they still belong to the start position, and replaying them
     * as ordinary moves would collide with the dots already on the board, thus the engine would reject
     * the whole query.
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

            val analysis = assertNotNull(defaultEngine.analyze(field, Player.First))
            // Every dot of the start position is on the board of the engine as well, so none of them is a candidate
            for (setupMove in crossMoves + extraSetupMove) {
                assertNull(analysis.moveAt(setupMove.positionXY!!), "${setupMove.positionXY} is occupied")
            }
        }
    }

    /**
     * A field that is wider than it is high is the regular one of Dots (the engine holds up to 39x32),
     * and the symmetries of such a board can't be transposed: a transposed 39 wide board would be 39 high.
     */
    @Test
    fun aWideFieldIsAnalyzed() {
        runBlocking {
            val wideField = Field.create(
                Rules.create(39, 32,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Cross,
                    random = testRandom,
                    initPosGenType = InitPosGenType.Static,
                    komi = 0.0
                )
            )

            val analysis = assertNotNull(defaultEngine.analyze(wideField, Player.First, withOwnership = true))
            assertEquals(wideField.width * wideField.height, analysis.ownership?.size)
            assertNotNull(analysis.chosenMove)
        }
    }

    /**
     * The app allows fields up to 39x39, while the engine is compiled for 39x32 at most,
     * so a higher field makes it reject the query.
     */
    @Test
    fun aRejectedQueryIsReportedInsteadOfCrashing() {
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

            // Nothing may be computed on a position the engine rejects, but it stays usable for the next query
            assertNull(defaultEngine.analyze(tooHighField, Player.First))
            assertNull(defaultEngine.generateMove(tooHighField, Player.First))

            val errors = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
            assertTrue(errors.any { "boardYSize" in it.message }, "Reported diagnostics: $diagnostics")

            assertNotNull(defaultEngine.analyze(createField(), Player.First))
        }
    }

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
            action(createField())
        }
    }
}
