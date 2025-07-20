package org.dots.game.field

import RandomGameAnalyser
import org.dots.game.core.InitialPositionType
import org.dots.game.core.Rules
import org.dots.game.core.generateDefaultInitialPositions
import java.util.Locale
import kotlin.test.Test
import kotlin.test.fail

class StressTests {
    @Test
    fun standardField() {
        testStandardField(InitialPositionType.Empty)
    }

    @Test
    fun standardFieldWithCrossOpening() {
        testStandardField(InitialPositionType.Cross)
    }

    fun testStandardField(initialPositionType: InitialPositionType) {
        var errorIsEncountered = false
        RandomGameAnalyser.process(
            Rules(39, 32, initialMoves = initialPositionType.generateDefaultInitialPositions(39, 32)!!),
            gamesCount = 10000,
            seed = 1,
            checkRollback = true,
            measureNanos = { System.nanoTime() },
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