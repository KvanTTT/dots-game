package org.dots.game.field.features

import org.dots.game.core.EMPTY_POSITION_MARKER
import org.dots.game.core.FIRST_PLAYER_MARKER
import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.SECOND_PLAYER_MARKER
import org.dots.game.dump.FieldParser
import java.util.SortedMap
import kotlin.test.assertEquals
import kotlin.test.fail

fun checkFeatures(
    originalField: Field,
    expectedPositionsData: String?,
    actualPositionsMap: Map<Position, Player>,
    assertionMessage: String,
): (() -> Unit)? {
    val expectedPositions: SortedMap<Position, Player> = if (expectedPositionsData != null) {
        val (width, height, moves) = FieldParser.parse(expectedPositionsData)
        assertEquals(originalField.width, width)
        assertEquals(originalField.height, height)
        moves.map { it.value }.associate {
            val (x, y) = it.positionXY
            Position(x, y, originalField.realWidth) to it.player
        }.toSortedMap()
    } else {
        sortedMapOf()
    }

    val actualPositions: SortedMap<Position, Player> = actualPositionsMap.toSortedMap()

    if (expectedPositions != actualPositions) {
        return {
            fun SortedMap<Position, Player>.dump(): String {
                return buildString {
                    for (y in 1..originalField.height) {
                        for (x in 1..originalField.width) {
                            append(
                                when (this@dump[Position(x, y, originalField.realWidth)]) {
                                    Player.First -> "$FIRST_PLAYER_MARKER "
                                    Player.Second -> "$SECOND_PLAYER_MARKER "
                                    Player.WallOrBoth -> "$FIRST_PLAYER_MARKER$SECOND_PLAYER_MARKER"
                                    else -> "$EMPTY_POSITION_MARKER "
                                }
                            )
                            if (x != originalField.width) {
                                append(' ')
                            }
                        }
                        if (y != originalField.height) {
                            appendLine()
                        }
                    }
                }
            }

            // Check string for a more convenient comparison
            assertEquals(
                expectedPositions.dump(),
                actualPositions.dump(),
                assertionMessage,
            )
            fail("Should not be here. Fix the comparison function")
        }
    } else {
        return null
    }
}