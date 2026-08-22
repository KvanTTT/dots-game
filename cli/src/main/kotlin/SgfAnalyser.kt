import org.dots.game.Diagnostic
import org.dots.game.DiagnosticSeverity
import org.dots.game.buildLineOffsets
import org.dots.game.core.Games
import org.dots.game.sgf.SgfConverter
import org.dots.game.sgf.SgfParser
import org.dots.game.sgf.SgfRefiner
import org.dots.game.sgf.SgfRoot
import org.dots.game.toLineColumnDiagnostic
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
            var totalMovesCount = 0
            var progress = 0

            val totalTimeTimeMark = TimeSource.Monotonic.markNow()

            for ((index, file = value) in sgfFiles.withIndex()) {
                val processingResult = processFile(file, exceptionLogger)
                if (processingResult != null) {
                    totalParserElapsed += processingResult.parserElapsed
                    totalConverterElapsed += processingResult.converterElapsed
                    totalFieldElapsed += processingResult.fieldElapsed
                    totalMovesCount += processingResult.games.sumOf {
                        var counter = 0
                        it.gameTree.forEachDepthFirst {
                            counter++
                            true
                        }
                        counter
                    }

                    val refinerDiagnostics = mutableListOf<Diagnostic>()
                    if (sgfFileWriter != null) {
                        val refinedGames: Games?
                        val content: String?
                        if (refineOutput) {
                            content = null
                            refinedGames = SgfRefiner.refine(processingResult.games, processingResult.diagnostics) {
                                refinerDiagnostics += it
                            }
                        } else {
                            content = processingResult.content
                            refinedGames = processingResult.games
                        }

                        if (refinedGames != null) {
                            val fileName = if (isDirectory) {
                                file.relativeTo(fileOrDirectoryFile).path
                            } else {
                                file.name
                            }
                            sgfFileWriter.add(refinedGames, content, fileName)
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

                println()
                printTime("Parser", totalParserElapsed)
                printTime("Converter", totalConverterElapsed)
                printTime("Field", totalFieldElapsed)
                println("Total time: ${totalTime.inWholeMilliseconds} ms")
                println("Total files count: ${sgfFiles.size}")
                println("Total moves count: $totalMovesCount")
                println("Field moves per second: ${(totalMovesCount.toDouble() / totalFieldElapsedNanos * nanosInSec).toInt()}")
                println("Fields per second: ${(sgfFiles.size.toDouble() / totalFieldElapsedNanos * nanosInSec).toInt()}")
                println(
                    "Millis per field: ${
                        String.format(Locale.ENGLISH, "%.4f", totalFieldElapsedNanos / sgfFiles.size / nanosInMs)
                    }"
                )
                println("Average number of moves per field: ${(totalMovesCount.toDouble() / sgfFiles.size).toInt()}")
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
}