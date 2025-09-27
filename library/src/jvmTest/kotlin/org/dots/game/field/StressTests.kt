package org.dots.game.field

import RandomGameAnalyser
import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.InitPosType
import org.dots.game.core.Player
import org.dots.game.core.Rules
import org.dots.game.createStandardRules
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class StressTests {
    @Test
    fun standardField() {
        testStandardField(InitPosType.Empty)
    }

    @Test
    fun standardFieldWithCrossOpening() {
        testStandardField(InitPosType.Cross)
    }

    @Test
    fun standardFieldWithRandomQuadrupleCrossOpening() {
        testStandardField(InitPosType.QuadrupleCross)
    }

    @Test
    fun maxTerritory() {
        val field = Field.create(createStandardRules(initPosType = InitPosType.Empty))
        for (y in 1..field.height) {
            for (x in 1..field.width) {
                val player = if (y == 1 || y == field.height || x == 1 || x == field.width) {
                    Player.First
                } else {
                    Player.Second
                }
                val moveResult = field.makeMove(x, y, player)!!
                if (x == field.width - 1 && y == field.height) {
                    val bigBase = moveResult.bases!!.single()
                    assertEquals((field.width - 2) * (field.height - 2), bigBase.rollbackPositions.size)
                    assertEquals((field.width - 2) * 2 + (field.height - 2) * 2, bigBase.closurePositions.size)
                }
            }
        }
        assertTrue(field.isGameOver())
    }

    fun testStandardField(initPosType: InitPosType) {
        var errorIsEncountered = false
        RandomGameAnalyser.process(
            width = Rules.Standard.width,
            height = Rules.Standard.height,
            initPosType = initPosType,
            baseMode = BaseMode.AtLeastOneOpponentDot,
            gamesCount = 10000,
            seed = 1,
            checkRollback = true,
            formatDouble = { String.format(Locale.ENGLISH, "%.4f", it) },
            outputStream = { println(it) },
            errorStream = {
                println(it)
                errorIsEncountered = true
            },
        )
        if (errorIsEncountered) {
            fail("Error is encountered during stress tests")
        }
    }
}