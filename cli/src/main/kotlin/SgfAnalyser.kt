import org.dots.game.buildLineOffsets
import org.dots.game.sgf.SgfConverter
import org.dots.game.sgf.SgfParser
import org.dots.game.toLineColumnDiagnostic
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.round

object SgfAnalyser {
    fun process(
        outputStream: PrintStream,
        fileOrDirectoryFile: File,
        logFile: File?,
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

            val diagnosticsLogger = { message: String ->
                if (fileOutputWriter != null) {
                    fileOutputWriter.write(message + "\n")
                } else {
                    println(message)
                }
            }
            val exceptionLogger = { file: File, exception: Exception ->
                "EXCEPTION on $file: $exception".let { message ->
                    fileOutputWriter?.write(message + "\n")
                    println(message)
                }
            }

            val filesNumber = sgfFiles.size
            println("Number of sgf or sgfs files to analyse: $filesNumber")
            println()

            var totalParserNanos = 0L
            var totalConverterNanos = 0L
            var totalFieldNanos = 0L
            var totalMovesCount = 0
            var progress = 0

            val totalTimeStart = System.nanoTime()

            for ((index, file) in sgfFiles.withIndex()) {
                val processingResult = processFile(file, diagnosticsLogger, exceptionLogger)
                if (processingResult != null) {
                    totalParserNanos += processingResult.parserNanos
                    totalConverterNanos += processingResult.converterNanos
                    totalFieldNanos += processingResult.fieldNanos
                    totalMovesCount += processingResult.movesCount
                }

                val currentProgress = round((index.toDouble() / filesNumber) * 100).toInt()
                if (currentProgress > progress && isDirectory) {
                    fileOutputWriter?.flush()
                    progress = currentProgress
                    println("Progress: $progress")
                }
            }

            fileOutputWriter?.close()

            val totalSgfNanos = (totalParserNanos + totalConverterNanos + totalFieldNanos).toDouble()
            val totalTime = System.nanoTime() - totalTimeStart

            if (isDirectory) {
                fun printTime(name: String, value: Long) {
                    println("$name time: ${TimeUnit.NANOSECONDS.toMillis(value)} ms (${(value * 100 / totalSgfNanos).toInt()} %)")
                }

                println()
                printTime("Parser", totalParserNanos)
                printTime("Converter", totalConverterNanos)
                printTime("Field", totalFieldNanos)
                println("Total time: ${TimeUnit.NANOSECONDS.toMillis(totalTime)} ms")
                println("Total files count: ${sgfFiles.size}")
                println("Total moves count: $totalMovesCount")
                println("Field moves per second: ${(totalMovesCount.toDouble() / totalFieldNanos * nanosInSec).toInt()}")
                println("Fields per second: ${(sgfFiles.size.toDouble() / totalFieldNanos * nanosInSec).toInt()}")
                println(
                    "Millis per field: ${
                        String.format(Locale.ENGLISH, "%.4f", totalFieldNanos.toDouble() / sgfFiles.size / nanosInMs)
                    }"
                )
                println("Average number of moves per field: ${(totalMovesCount.toDouble() / sgfFiles.size).toInt()}")
            }
        }
    }

    private fun processFile(file: File, diagnosticsLogger: (String) -> Unit, exceptionLogger: (File, Exception) -> Unit): ProcessingResult? {
        try {
            val content = file.readText()
            val lineOffsets by lazy { content.buildLineOffsets() }
            var diagnosticsCount = 0

            val parserStartNanos = System.nanoTime()
            val sgfParseTree = SgfParser.parse(content) { parseDiagnostic ->
                diagnosticsLogger(parseDiagnostic.toLineColumnDiagnostic(lineOffsets).toString())
                diagnosticsCount++
            }
            val parserElapsedNanos = System.nanoTime() - parserStartNanos

            val converterAndFieldStartNanos = System.nanoTime()
            val sgfConverter = SgfConverter(sgfParseTree, warnOnMultipleGames = false, measureNanos = {
                System.nanoTime()
            }) { convertDiagnostic ->
                diagnosticsLogger(convertDiagnostic.toLineColumnDiagnostic(lineOffsets).toString())
                diagnosticsCount++
            }
            val games = sgfConverter.convert()
            val movesCount = games.sumOf { it.gameTree.allNodesCount }
            val converterAndFieldElapsedNanos = System.nanoTime() - converterAndFieldStartNanos

            val fieldNanos = sgfConverter.fieldNanos
            val converterNanos = converterAndFieldElapsedNanos - fieldNanos

            if (diagnosticsCount > 0 && games.isNotEmpty()) {
                diagnosticsLogger("$file contains errors or warnings and has the following rules: ${games.first().comment}")
            }

            return ProcessingResult(parserElapsedNanos, converterNanos, fieldNanos, movesCount)
        } catch (e: Exception) {
            exceptionLogger(file, e)
        }
        return null
    }

    private data class ProcessingResult(
        val parserNanos: Long,
        val converterNanos: Long,
        val fieldNanos: Long,
        val movesCount: Int,
    )
}