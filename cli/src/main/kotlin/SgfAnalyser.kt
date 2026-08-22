import org.dots.game.Diagnostic
import org.dots.game.DiagnosticSeverity
import org.dots.game.buildLineOffsets
import org.dots.game.core.GameResult
import org.dots.game.core.Games
import org.dots.game.core.Player
import org.dots.game.sgf.SgfConverter
import org.dots.game.sgf.SgfParser
import org.dots.game.sgf.SgfRefiner
import org.dots.game.sgf.SgfRoot
import org.dots.game.toLineColumnDiagnostic
import org.dots.game.toNeatNumber
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime

object SgfAnalyser {
    fun process(
        outputStream: PrintStream,
        fileOrDirectoryFile: File,
        logFile: File?,
        sgfFileWriter: SgfFileWriter?,
        refineOutput: Boolean,
        minDiagnosticSeverity: DiagnosticSeverity,
        numberOfFilesToDrop: Int = 0,
        numberOfFilesToProcess: Int = Int.MAX_VALUE
    ) {
        with(outputStream) {
            println("Analysed file or directory: ${fileOrDirectoryFile.absolutePath}")
            println("Logger: ${logFile?.absoluteFile ?: "Console"}")
            if (numberOfFilesToDrop > 0) {
                println("Skipped files count: $numberOfFilesToDrop")
            }

            val isDirectory: Boolean
            val sgfFiles = if (fileOrDirectoryFile.isDirectory) {
                isDirectory = true
                fileOrDirectoryFile.walkTopDown()
                    .filter { it.isFile && it.extension.let { ext -> ext == "sgf" || ext == "sgfs" } }
                    .drop(numberOfFilesToDrop)
                    .take(numberOfFilesToProcess)
                    .toList()
                    .takeIf { it.isNotEmpty() } ?: run {
                    println("The directory ${fileOrDirectoryFile.absolutePath} does not contain sgf or sgfs files")
                    return
                }
            } else {
                isDirectory = false
                fileOrDirectoryFile.takeIf { it.extension.let { ext -> ext == "sgf" || ext == "sgfs" } }?.let { listOf(it) } ?: run {
                    println("The file ${fileOrDirectoryFile.absolutePath} does not have sgf or sgfs extension")
                    return
                }
            }

            val fileOutputWriter = if (logFile != null) {
                FileOutputStream(logFile, false).bufferedWriter()
            } else {
                null
            }

            val writeMessage = { message: String ->
                fileOutputWriter?.write(message + "\n")
                println(message)
            }

            val exceptionLogger = { file: File, exception: Exception ->
                "EXCEPTION on $file: $exception".let { message ->
                    writeMessage(message)
                }
            }

            val filesNumber = sgfFiles.size
            println("Number of sgf or sgfs files to analyse: $filesNumber")
            println()

            var totalParserElapsed = Duration.ZERO
            var totalConverterElapsed = Duration.ZERO
            var totalFieldElapsed = Duration.ZERO
            val allMovesCount = mutableListOf<Int>()
            var movesBySizeRatioSum = 0.0
            var gamesCount = 0
            val sizes = mutableMapOf<Pair<Int, Int>, Int>()
            val gameResults = mutableListOf<GameResult>()
            var progress = 0

            val totalTimeTimeMark = TimeSource.Monotonic.markNow()

            for ((index, file = value) in sgfFiles.withIndex()) {
                val processingResult = processFile(file, exceptionLogger)
                if (processingResult != null) {
                    totalParserElapsed += processingResult.parserElapsed
                    totalConverterElapsed += processingResult.converterElapsed
                    totalFieldElapsed += processingResult.fieldElapsed

                    val refinerDiagnostics = mutableListOf<Diagnostic>()
                    val finalGamesToAccount: Games?
                    val content: String?
                    if (refineOutput) {
                        content = null
                        finalGamesToAccount = SgfRefiner.refine(processingResult.games, processingResult.diagnostics) {
                            refinerDiagnostics += it
                        }
                    } else {
                        content = processingResult.content
                        finalGamesToAccount = processingResult.games
                    }

                    if (finalGamesToAccount != null) {
                        gamesCount += finalGamesToAccount.size

                        val currentGameMovesCount = finalGamesToAccount.sumOf {
                            var counter = 0
                            it.gameTree.forEachDepthFirst {
                                counter++
                                true
                            }
                            counter
                        }
                        allMovesCount.add(currentGameMovesCount)

                        finalGamesToAccount.forEach { game ->
                            game.result?.let {
                                gameResults.add(it)
                            }
                            sizes[game.size] = sizes.getOrPut(game.size) { 0 } + 1

                            movesBySizeRatioSum += currentGameMovesCount.toDouble() / (game.size.first * game.size.second)
                        }

                        if (sgfFileWriter != null) {
                            val fileName = if (isDirectory) {
                                file.relativeTo(fileOrDirectoryFile).path
                            } else {
                                file.name
                            }
                            sgfFileWriter.add(finalGamesToAccount, content, fileName)
                        }
                    }

                    val diagnosticsToReport = (processingResult.diagnostics + refinerDiagnostics).filter {
                        it.severity >= minDiagnosticSeverity
                    }

                    if (diagnosticsToReport.isNotEmpty()) {
                        val lineOffsets = processingResult.content.buildLineOffsets()
                        writeMessage("File $file contains diagnostics:")
                        for (diagnostic in diagnosticsToReport) {
                            writeMessage(diagnostic.toLineColumnDiagnostic(lineOffsets).toString())
                        }
                    }
                }

                val currentProgress = round((index.toDouble() / filesNumber) * 100).toInt()
                if (currentProgress > progress && isDirectory) {
                    fileOutputWriter?.flush()
                    progress = currentProgress
                    println("Progress: $progress")
                }
            }

            fileOutputWriter?.close()

            val totalSgfElapsed = (totalParserElapsed + totalConverterElapsed + totalFieldElapsed)
            val totalFieldElapsedNanos = totalFieldElapsed.inWholeNanoseconds.toDouble()
            val totalTime = totalTimeTimeMark.elapsedNow()

            if (isDirectory) {
                fun printTime(name: String, value: Duration) {
                    println("$name time: ${value.inWholeMilliseconds} ms (${(value * 100 / totalSgfElapsed).toInt()} %)")
                }

                val totalMovesCount = allMovesCount.sum()

                println()
                printTime("Parser", totalParserElapsed)
                printTime("Converter", totalConverterElapsed)
                printTime("Field", totalFieldElapsed)
                println("Total time: ${totalTime.inWholeMilliseconds} ms")
                println("Total files count: ${sgfFiles.size}")
                println("Total moves count: $totalMovesCount")
                println("Game moves per second: ${(totalMovesCount.toDouble() / totalFieldElapsedNanos * nanosInSec).toInt()}")
                println("Games per second: ${(gamesCount.toDouble() / totalFieldElapsedNanos * nanosInSec).toInt()}")
                println(
                    "Millis per game: ${
                        String.format(Locale.ENGLISH, "%.4f", totalFieldElapsedNanos / gamesCount / nanosInMs)
                    }"
                )

                println()
                println("Average number of moves per game: ${(totalMovesCount.toDouble() / gamesCount).toInt()}")
                println("Median number of moves per game: ${allMovesCount.median()?.toNeatNumber()}")
                println("Max number of moves per game: ${allMovesCount.maxOrNull()}")
                println("Average moves by size ratio: ${String.format(Locale.ENGLISH, "%.4f", movesBySizeRatioSum / gamesCount)}")
                println("Sizes: ${sizes.map { "${it.key} : ${it.value}" }.joinToString("; ")}")
                println("Blue wins: ${gameResults.count { (it as? GameResult.WinGameResult)?.winner == Player.First }}")
                println("Red wins: ${gameResults.count { (it as? GameResult.WinGameResult)?.winner == Player.Second }}")
                println("Draws: ${gameResults.count { it is GameResult.Draw }}")
                println("Lack of time results: ${gameResults.count { it is GameResult.TimeWin }}")
            }
        }
    }

    private fun processFile(file: File, exceptionLogger: (File, Exception) -> Unit): ProcessingResult? {
        try {
            val content = file.readText()
            val diagnostics = mutableListOf<Diagnostic>()

            val sgfParseTree: SgfRoot
            val parserElapsed = measureTime {
                sgfParseTree = SgfParser.parse(content) { parseDiagnostic ->
                    diagnostics.add(parseDiagnostic)
                }
            }

            val sgfConverter: SgfConverter
            val games: Games
            val converterAndFieldStartElapsed = measureTime {
                sgfConverter = SgfConverter(sgfParseTree, useEndingMove = false, warnOnMultipleGames = false) { convertDiagnostic ->
                    diagnostics.add(convertDiagnostic)
                }
                games = sgfConverter.convert()
            }

            val fieldTimeElapsed = sgfConverter.fieldTime
            val converterElapsed = converterAndFieldStartElapsed - fieldTimeElapsed

            return ProcessingResult(parserElapsed, converterElapsed, fieldTimeElapsed, content, games, diagnostics)
        } catch (e: Exception) {
            exceptionLogger(file, e)
        }
        return null
    }

    private data class ProcessingResult(
        val parserElapsed: Duration,
        val converterElapsed: Duration,
        val fieldElapsed: Duration,
        val content: String,
        val games: Games,
        val diagnostics: List<Diagnostic>,
    )

    fun <T : Number> Iterable<T>.median(): Double? {
        val list = this.toList()
        if (list.isEmpty()) return null

        // Sort the list based on double values
        val sorted = list.sortedBy { it.toDouble() }
        val middle = sorted.size / 2

        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }
}