import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.InitialPositionType
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.generateDefaultInitialPositions
import org.dots.game.core.unmakeAllMovesAndCheck
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object RandomGameAnalyser {
    fun process(
        outputStream: PrintStream,
        gamesCount: Int,
        fieldWidth: Int,
        fieldHeight: Int,
        initialPosition: InitialPositionType,
        captureEmptyBases: Boolean,
        seed: Long,
        checkRollback: Boolean
    ) {
        with(outputStream) {
            val rules = Rules(
                fieldWidth,
                fieldHeight,
                baseMode = if (captureEmptyBases) BaseMode.AnySurrounding else BaseMode.AtLeastOneOpponentDot,
                initialMoves = initialPosition.generateDefaultInitialPositions(fieldWidth, fieldHeight)!!
            )

            val random = if (seed == 0L) Random.Default else Random(seed)

            println("Games count: $gamesCount")
            println("Field size: ${rules.width};${rules.height}")
            println("Initial position: ${rules.initialPositionType}")
            println("Capture empty bases: ${rules.baseMode == BaseMode.AnySurrounding}")
            println("Random seed: $seed" + (if (seed == 0L) " (Timestamp)" else ""))
            println("Check rollback: $checkRollback")
            println()

            var firstPlayerWins = 0
            var secondPlayerWins = 0
            var drawCount = 0
            var movesCount = 0
            var basesCount = 0
            var capturedDotsCount = 0
            var freedDotsCount = 0
            var emptyBasesCount = 0

            var totalTimeNs = 0L

            val startNanos = System.nanoTime()

            val randomMoves = buildList {
                for (y in 1..fieldHeight) {
                    for (x in 1..fieldWidth) {
                        Position(x, y).let { position ->
                            if (rules.initialMoves.none { it.position == position }) {
                                add(position)
                            }
                        }
                    }
                }
            }.toTypedArray()

            for (gameNumber in 0 until gamesCount) {
                randomMoves.shuffle(random)

                val field = Field.create(rules)

                for (randomMove in randomMoves) {
                    val moveResult = field.makeMove(randomMove)

                    if (moveResult != null) {
                        movesCount++
                        if (moveResult.bases != null) {
                            for (base in moveResult.bases) {
                                if (base.isReal) {
                                    capturedDotsCount += base.playerDiff
                                    freedDotsCount -= base.oppositePlayerDiff
                                    basesCount++
                                } else {
                                    emptyBasesCount++
                                }
                            }
                        }
                    }
                }

                val gameResult = field.gameResult!!
                if (gameResult is GameResult.Draw) {
                    drawCount++
                } else {
                    require(gameResult is GameResult.WinGameResult)
                    if (gameResult.winner == Player.First) {
                        firstPlayerWins++
                    } else {
                        secondPlayerWins++
                    }
                }

                if (checkRollback) {
                    field.unmakeAllMovesAndCheck { println(it) }
                }
            }

            totalTimeNs += System.nanoTime() - startNanos

            println("Total time: ${TimeUnit.NANOSECONDS.toMillis(totalTimeNs)} ms")
            println("First player wins: $firstPlayerWins")
            println("Second player wins: $secondPlayerWins")
            println("Draw count: $drawCount")
            println("First wins ratio: ${formatDouble(firstPlayerWins.toDouble() / gamesCount)}")
            println("Draw ratio: ${formatDouble(drawCount.toDouble() / gamesCount)}")
            println(
                "Average time per game: ${
                    formatDouble(
                        totalTimeNs.toDouble() / gamesCount / TimeUnit.MILLISECONDS.toNanos(
                            1
                        )
                    )
                } ms"
            )
            println("Average games count per second: ${(gamesCount.toDouble() / totalTimeNs * nanosInSec).toLong()}")
            println("Average moves count per second: ${(movesCount.toDouble() / totalTimeNs * nanosInSec).toLong()}")
            println("Moves count: $movesCount")
            println("Bases count: $basesCount")
            println("Captured dots count: $capturedDotsCount")
            println("Freed dots count: $freedDotsCount")
            println("Empty bases count: $emptyBasesCount")
        }
    }
}