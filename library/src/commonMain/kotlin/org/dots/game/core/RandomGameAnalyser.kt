import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.core.unmakeAllMovesAndCheck
import kotlin.random.Random

object RandomGameAnalyser {
    fun process(
        rules: Rules,
        gamesCount: Int,
        seed: Long,
        checkRollback: Boolean,
        measureNanos: () -> Long,
        formatDouble: (Double) -> String,
        outputStream: (String) -> Unit
    ) {
        val random = if (seed == 0L) Random.Default else Random(seed)

        outputStream("Games count: $gamesCount")
        outputStream("Field size: ${rules.width};${rules.height}")
        outputStream("Initial position: ${rules.initialPositionType}")
        outputStream("Capture empty bases: ${rules.baseMode == BaseMode.AnySurrounding}")
        outputStream("Random seed: $seed" + (if (seed == 0L) " (Timestamp)" else ""))
        outputStream("Check rollback: $checkRollback")
        outputStream("")

        var firstPlayerWins = 0
        var secondPlayerWins = 0
        var drawCount = 0
        var movesCount = 0
        var basesCount = 0
        var capturedDotsCount = 0
        var freedDotsCount = 0
        var emptyBasesCount = 0

        var totalTimeNs = 0L

        val startNanos = measureNanos()

        val randomMoves = buildList {
            for (y in 1..rules.height) {
                for (x in 1..rules.width) {
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

            val field = Field.create(rules) { moveInfo: MoveInfo, _: Boolean, moveNumber: Int ->
                outputStream("Incorrect initial move #$moveNumber at ${moveInfo.position} (${moveInfo.player})")
            }

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
                field.unmakeAllMovesAndCheck { outputStream(it) }
            }
        }

        totalTimeNs += measureNanos() - startNanos

        outputStream("Total time: ${totalTimeNs / nanosInMs} ms")
        outputStream("First player wins: $firstPlayerWins")
        outputStream("Second player wins: $secondPlayerWins")
        outputStream("Draw count: $drawCount")
        outputStream("First wins ratio: ${formatDouble(firstPlayerWins.toDouble() / gamesCount)}")
        outputStream("Draw ratio: ${formatDouble(drawCount.toDouble() / gamesCount)}")
        outputStream(
            "Average time per game: ${formatDouble(totalTimeNs.toDouble() / gamesCount / nanosInMs)} ms"
        )
        outputStream("Average games count per second: ${(gamesCount.toDouble() / totalTimeNs * nanosInSec).toLong()}")
        outputStream("Average moves count per second: ${(movesCount.toDouble() / totalTimeNs * nanosInSec).toLong()}")
        outputStream("Moves count: $movesCount")
        outputStream("Bases count: $basesCount")
        outputStream("Captured dots count: $capturedDotsCount")
        outputStream("Freed dots count: $freedDotsCount")
        outputStream("Empty bases count: $emptyBasesCount")
    }
}

const val nanosInMs: Long = 1_000_000
const val nanosInSec: Long = 1_000_000_000