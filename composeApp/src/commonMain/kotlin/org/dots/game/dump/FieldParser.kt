package org.dots.game.dump

import org.dots.game.Diagnostic
import org.dots.game.core.EMPTY_POSITION
import org.dots.game.core.FIRST_PLAYER_MARKER
import org.dots.game.core.Field
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.SECOND_PLAYER_MARKER
import org.dots.game.sgf.TextSpan

object FieldParser {
    fun parseAndConvertWithNoInitialMoves(data: String, diagnosticReporter: (Diagnostic) -> Unit = { error(it.toString()) }): Field {
        return parseAndConvert(data, { width, height ->
            Rules(
                width,
                height,
                initialMoves = emptyList()
            )
        }, diagnosticReporter)
    }

    fun parseAndConvert(
        data: String,
        initializeRules: (Int, Int) -> Rules = { width, height -> Rules(width, height) },
        diagnosticReporter: (Diagnostic) -> Unit = { error(it.toString()) },
    ): Field {
        val (width, height, allMoves) = parse(data, diagnosticReporter)

        return Field(initializeRules(width, height)).apply {
            for ((number, move) in allMoves) {
                val position = move.position
                if (makeMoveUnsafe(position, move.player) == null) {
                    diagnosticReporter(Diagnostic("Can't make move #$number to $position", move.textSpan))
                }
            }
        }
    }

    fun parse(data: String, diagnosticReporter: (Diagnostic) -> Unit = { error(it.toString()) }): Triple<Int, Int, LinkedHashMap<Int, LightMove>> {
        val numberedMoves = mutableMapOf<Int, LightMove>()
        val unnumberedMoves = mutableListOf<LightMove>()

        var currentWidth = 0
        var maxWidth = 0
        var charIndex = 0
        var lineIndex = 0

        while (charIndex < data.length) {
            val char = data[charIndex]
            when (char) {
                ' ', '\t' -> {
                    charIndex++
                }
                '\r', '\n' -> {
                    charIndex++
                    if (char == '\r' && data.elementAtOrNull(charIndex) == '\n') {
                        charIndex++
                    }

                    if (currentWidth > maxWidth) {
                        maxWidth = currentWidth
                    }

                    if (currentWidth > 0) { // Skip empty lines
                        lineIndex++
                        currentWidth = 0
                    }
                }
                FIRST_PLAYER_MARKER, SECOND_PLAYER_MARKER -> {
                    val moveStartIndex = charIndex

                    charIndex++

                    val player = if (char == FIRST_PLAYER_MARKER) Player.First else Player.Second

                    var digitIndex = charIndex
                    while (data.elementAtOrNull(digitIndex)?.isDigit() == true) {
                        digitIndex++
                    }

                    val move = LightMove(
                        Position(currentWidth + Field.OFFSET, lineIndex + Field.OFFSET),
                        player,
                        TextSpan.fromBounds(moveStartIndex, digitIndex)
                    )

                    val unnumberedMove = if (digitIndex - charIndex > 0) {
                        val parsedMoveNumber = data.substring(charIndex, digitIndex).toUIntOrNull()?.toInt()

                        if (parsedMoveNumber != null) {
                            if (!numberedMoves.containsKey(parsedMoveNumber)) {
                                numberedMoves[parsedMoveNumber] = move
                                null
                            } else {
                                diagnosticReporter(
                                    Diagnostic(
                                        "The move with number $parsedMoveNumber is already in use.",
                                        textSpan = TextSpan.fromBounds(charIndex, digitIndex)
                                    )
                                )
                                move
                            }
                        } else {
                            diagnosticReporter(
                                Diagnostic(
                                    "Incorrect cell move's number.",
                                    textSpan = TextSpan.fromBounds(charIndex, digitIndex)
                                )
                            )
                            move
                        }
                    } else {
                        move
                    }

                    charIndex = digitIndex

                    unnumberedMove?.let { unnumberedMoves.add(it) }

                    currentWidth++
                }
                EMPTY_POSITION -> {
                    charIndex++

                    while (data.elementAtOrNull(charIndex) == EMPTY_POSITION) {
                        charIndex++
                    }

                    currentWidth++
                }
                else -> {
                    diagnosticReporter(
                        Diagnostic(
                            "The marker should be either `$FIRST_PLAYER_MARKER` (first player), `$SECOND_PLAYER_MARKER` (second player) or `$EMPTY_POSITION`.",
                            TextSpan(charIndex, 1)
                        )
                    )

                    currentWidth++
                    charIndex++
                }
            }
        }

        if (currentWidth > 0) {
            lineIndex++
        }

        val allMoves = mergeMoves(numberedMoves, unnumberedMoves, diagnosticReporter)

        return Triple(maxWidth, lineIndex, allMoves)
    }

    private fun mergeMoves(
        numberedMap: MutableMap<Int, LightMove>,
        unnumberedMoves: MutableList<LightMove>,
        diagnosticReporter: (Diagnostic) -> Unit
    ): LinkedHashMap<Int, LightMove> {
        val sortedMoves = numberedMap.entries.sortedBy { it.key }
        var moveNumberInUnnumberedMoves = 0
        var previousMoveNumber = -1

        return linkedMapOf<Int, LightMove>().apply {
            for ((moveNumber, move) in sortedMoves) {
                val maxMoveCountToInsert = moveNumber - previousMoveNumber - 1
                if (maxMoveCountToInsert > 0) {
                    val moveCountToInsert =
                        minOf(unnumberedMoves.size - moveNumberInUnnumberedMoves, maxMoveCountToInsert)
                    var insertedMoveNumber = previousMoveNumber + 1
                    (0 until moveCountToInsert).forEach { _ ->
                        this[insertedMoveNumber++] = unnumberedMoves[moveNumberInUnnumberedMoves++]
                    }
                    if (moveNumber - insertedMoveNumber > 0) {
                        var reportError = true
                        val singleValueOrRange = if (size == moveNumber - 1) {
                            reportError = isNotEmpty() // Allow moves sequence to start both since `0` and `1`
                            size
                        } else {
                            IntRange(size, moveNumber - 1)
                        }
                        if (reportError) {
                            diagnosticReporter(
                                Diagnostic("The following moves are missing: $singleValueOrRange", textSpan = null)
                            )
                        }
                    }
                }
                this[moveNumber] = move
                previousMoveNumber = moveNumber
            }

            var insertedMoveNumber = previousMoveNumber + 1
            while (moveNumberInUnnumberedMoves < unnumberedMoves.size) {
                this[insertedMoveNumber++] = unnumberedMoves[moveNumberInUnnumberedMoves++]
            }
        }
    }

    data class LightMove(
        val position: Position,
        val player: Player,
        val textSpan: TextSpan,
    )
}