import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.GameResult
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.Position
import org.dots.game.core.PositionXY
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
        outputStream: (String) -> Unit,
        errorStream: (String) -> Unit = outputStream,
    ) {
        val random = if (seed == 0L) Random.Default else Random(seed)

        outputStream("Games count: $gamesCount")
        outputStream("Field size: ${rules.width};${rules.height}")
        outputStream("Initial position: ${rules.initPosType}")
        outputStream("Capture empty bases: ${rules.baseMode == BaseMode.AnySurrounding}")
        outputStream("Random seed: $seed" + (if (seed == 0L) " (Timestamp)" else ""))
        outputStream("Check rollback: $checkRollback")
        outputStream("")

        var firstPlayerWins = 0
        var secondPlayerWins = 0
        var drawCount = 0
        var movesCount = 0
        var basesCount = 0
        var emptyBasesCount = 0

        var totalTimeNs = 0L

        val startNanos = measureNanos()

        val fieldStride = Field.getStride(rules.width)
        val randomMoves = buildList {
            for (y in 1..rules.height) {
                for (x in 1..rules.width) {
                    PositionXY(x, y).let { positionXY ->
                        if (rules.initialMoves.none { it.positionXY == positionXY }) {
                            add(Position(x, y, fieldStride))
                        }
                    }
                }
            }
        }.toTypedArray()

        for (gameNumber in 0 until gamesCount) {
            randomMoves.shuffle(random)

            try {
                val field = Field.create(rules) { moveInfo: MoveInfo, _: Boolean, moveNumber: Int ->
                    outputStream("Incorrect initial move #$moveNumber at ${moveInfo.positionXY} (${moveInfo.player})")
                }

                for (randomMove in randomMoves) {
                    val moveResult = field.makeMoveUnsafe(randomMove)

                    if (moveResult != null) {
                        movesCount++
                        if (moveResult.bases != null) {
                            for (base in moveResult.bases) {
                                if (base.isReal) {
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
                    field.unmakeAllMovesAndCheck { errorStream(it) }
                }
            } catch(ex: Exception) {
                errorStream("Exception during running the game #$gameNumber: ${ex.message}")
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
        outputStream("Empty bases count: $emptyBasesCount")
    }
}

const val nanosInMs: Long = 1_000_000
const val nanosInSec: Long = 1_000_000_000