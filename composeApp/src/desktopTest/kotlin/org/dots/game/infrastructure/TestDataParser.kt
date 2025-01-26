package org.dots.game.infrastructure

import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position
import kotlin.collections.iterator

object TestDataParser {
    const val EMPTY_CELL_MARKER = '.'
    const val FIRST_PLAYER_MARKER = '*'
    const val SECOND_PLAYER_MARKER = '+'
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun parse(data: String): TestDataField {
        val lines = data.trim().split("\n", "\r\n")

        if (lines.isEmpty()) error("Field should have at least one cell")

        val movesWithNumberMap = mutableMapOf<Int, TestMove>()
        val movesWithoutNumbersList = mutableListOf<TestMove>()
        var width = 0

        for ((lineIndex, line) in lines.withIndex()) {
            val cells = line.trim().split(WHITESPACE_REGEX)
            if (cells.size > width) {
                width = cells.size
            }

            if (cells.isEmpty()) error("Empty line at $lineIndex")

            for ((cellIndex, cell) in cells.withIndex()) {
                if (cell.all { it == EMPTY_CELL_MARKER }) continue

                val player = when (cell.first()) {
                    FIRST_PLAYER_MARKER -> Player.First
                    SECOND_PLAYER_MARKER -> Player.Second
                    else -> {
                        error("Incorrect cell at ($cellIndex,$lineIndex). The marker should be either $FIRST_PLAYER_MARKER (first player) or $SECOND_PLAYER_MARKER (second player).")
                    }
                }

                val testMove = TestMove(Position(cellIndex + Field.OFFSET, lineIndex + Field.OFFSET), player)

                if (cell.length > 1) {
                    val parsedMoveNumber = cell.drop(1).toUIntOrNull()?.toInt()
                        ?: error("Incorrect cell move's number at ($cellIndex,$lineIndex).")

                    if (movesWithNumberMap.containsKey(parsedMoveNumber)) {
                        error("The move with number $parsedMoveNumber is already in use.")
                    }

                    movesWithNumberMap[parsedMoveNumber] = testMove
                } else {
                    movesWithoutNumbersList.add(testMove)
                }
            }
        }

        val allMoves = movesWithNumberMap.toSortedMap()
        var currentMoveNumber = 0
        for (move in movesWithoutNumbersList) {
            while (allMoves.containsKey(currentMoveNumber)) {
                currentMoveNumber++
            }
            allMoves[currentMoveNumber++] = move
        }

        val testMoves = buildList {
            var lastMoveNumber: Int? = null
            for ((moveNumber, move) in allMoves) {
                if (lastMoveNumber != null && moveNumber - lastMoveNumber > 1) {
                    error("The moves are missing: ${IntRange(lastMoveNumber + 1, moveNumber - 1)}")
                }

                add(move)
                lastMoveNumber = moveNumber
            }
        }

        val height = lines.size

        return TestDataField(width, height, testMoves)
    }
}

data class TestDataField(
    val width: Int,
    val height: Int,
    val moves: List<TestMove>,
)

data class TestMove(
    val position: Position,
    val player: Player,
) {
    constructor(x: Int, y: Int, player: Player) : this(Position(x, y), player)
}