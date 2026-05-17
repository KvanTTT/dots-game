package org.dots.game.field.features

import org.dots.game.core.Player
import org.dots.game.core.features.getCrosses
import org.dots.game.dump.FieldParser
import org.junit.jupiter.api.assertAll
import kotlin.test.Test

class CrossesTests {
    @Test
    fun bounds() {
        checkCrosses("""
x  o
o  x
""",

"""
xo xo
xo xo
""",
            )
    }

    @Test
    fun noCrossIfOpponentBase() {
        checkCrosses("""
.  .  .  .  .
.  x  o  x  .
.  o  x  o  .
.  x  o  x  .
.  .  .  .  .
""",
            crossesData = null,
        )
    }

    @Test
    fun differentCases() {
        checkCrosses("""
.  .  .  .  .  .
.  x  o  .  o  x
.  o  .  .  x  o
.  .  .  .  .  .
.  o  x  o  .  .
.  x  o  x  .  .
""",
            crossesData = """
.  .  .  .  .  .
.  .  .  .  xo xo
.  .  .  .  xo xo
.  .  .  .  .  .
.  xo xo xo .  .
.  xo xo xo .  .
""",
        )
    }

    private fun checkCrosses(fieldData: String, crossesData: String?) {
        val field = FieldParser.parseAndConvertWithNoInitialMoves(fieldData)
        val crosses = field.getCrosses().associateWith { Player.WallOrBoth }
        checkFeatures(
            field,
            expectedPositionsData = crossesData,
            crosses,
            "Mismatched crosses"
        )?.let {
            assertAll(it)
        }
    }
}