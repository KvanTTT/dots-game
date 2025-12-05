@file:Suppress("RETURN_VALUE_NOT_USED") // TODO: remove after switching to a newer Kotlin version (KT-82363)

package org.dots.game

import kotlinx.coroutines.runBlocking
import org.dots.game.core.BaseMode
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.InitPosType
import org.dots.game.core.LegalMove
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
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

    val defaultEngine = initialize(KataGoDotsSettings(
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
                    random = null,
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
                    random = null,
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

    private fun runEngine(action: suspend (field: Field) -> Unit) {
        runBlocking {
            val field = Field.create(
                Rules.create(8, 8,
                    captureByBorder = false, baseMode = BaseMode.AtLeastOneOpponentDot,
                    suicideAllowed = true, initPosType = InitPosType.Cross,
                    random = null,
                    komi = 0.0
                )
            )
            assertIs<FullSync>(defaultEngine.sync(field))
            action(field)
        }
    }
}