package org.dots.game

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.assertNotNull

class KataGoDotsEngineTests {
    companion object {
        const val DEFAULT_EXE = "F:\\GitHub\\KataGo.2\\cpp\\cmake-build-tensorrt-debug\\katago.exe"
        const val DEFAULT_MODEL =
            "F:\\GitHub\\KataGo.2\\katago_contribute\\katagodots-test\\models\\dotsgame-s7782144-d725682\\model.bin.gz"
        const val DEFAULT_CONFIG = "F:\\GitHub\\KataGo.2\\cpp\\configs\\gtp_example_dots.cfg"
    }

    @Test
    fun incorrectExe() {
        assertNotNull(initialize(KataGoDotsSettings(
            "invalid path",
            DEFAULT_MODEL,
            DEFAULT_CONFIG,
        )))
    }

    @Test
    fun incorrectModel() {
        assertNotNull(initialize(KataGoDotsSettings(
            DEFAULT_EXE,
            "invalid model",
            DEFAULT_CONFIG,
        )))
    }

    @Test
    fun incorrectConfig() {
        assertNotNull(initialize(KataGoDotsSettings(
            DEFAULT_EXE,
            DEFAULT_MODEL,
            "invalid config",
        )))
    }

    @Test
    fun correctInitialization() {
        assertNotNull(initialize(KataGoDotsSettings(
            DEFAULT_EXE,
            DEFAULT_MODEL,
            DEFAULT_CONFIG
        )))
    }

    private fun initialize(kataGoDotsSettings: KataGoDotsSettings): KataGoDotsEngine? {
        return runBlocking {
            KataGoDotsEngine.initialize(kataGoDotsSettings) {
                println(it)
            }
        }
    }

/*    @Test
    fun init() {
        val field = Field.create(Rules.create(14, 14,
            captureByBorder = Rules.Standard.captureByBorder, baseMode = Rules.Standard.baseMode,
            suicideAllowed = Rules.Standard.suicideAllowed, initPosType = Rules.Standard.initPosType,
            random = null, komi = Rules.Standard.komi)
        )

        val engine = KataGoDotsEngine()
        val move = engine.generateMove(Player.First, field.height)
        println(move)
    }*/
}