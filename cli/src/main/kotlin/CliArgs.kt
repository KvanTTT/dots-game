import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.restrictTo
import org.dots.game.DiagnosticSeverity
import org.dots.game.core.BaseMode
import org.dots.game.core.Field
import org.dots.game.core.Games
import org.dots.game.core.InitPosType
import org.dots.game.core.Rules
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.reflect.KProperty

class CliArgs : CliktCommand() {
    private val captureEmptyBasesOption = "--empty-bases"

    val path: File? by option()
        .file(mustExist = true, mustBeReadable = true)
        .help("If specified, the tool handles the provided directory with SGF or a single SGF")
    val logFile: File? by option()
        .file(mustExist = true, mustBeWritable = true)
        .help("If specified, the tool dumps log to the file")
    val gamesCount: Int by option("-c", "--count")
        .int()
        .restrictTo(1)
        .default(10000)
        .help("Number of games to process")
    val gamesCountToDrop: Int? by option()
        .int()
        .restrictTo(0)
        .help("Number of games to drop")
    val width: Int? by option("-w", "--width")
        .int()
        .restrictTo(5, Field.MAX_SIZE)
        .help("Field width")
    val height: Int? by option("-h", "--height")
        .int()
        .restrictTo(5, Field.MAX_SIZE)
        .help("Field height")
    val captureEmptyBases: Boolean? by option(captureEmptyBasesOption)
        .boolean()
        .help("If enabled, base is created even if it doesn't have enemy dots inside")
    val initPosType: InitPosType? by option()
        .enum<InitPosType>()
        .help("The initial position type, allowed values: ${
            InitPosType.entries.filter { it != InitPosType.Custom }.joinToString(", ") { it.name }
        }")
        .check { it != InitPosType.Custom }
    val seed: Long? by option("-s", "--seed")
        .long()
        .help("Seed. Use `0` value for timestamp-based seed")
    val checkRollback: Boolean by option("--check")
        .boolean()
        .default(false)
        .help("Enabled extra checks (for instance, on rollback)")
    val outputFileOrDirectory: File? by option()
        .file()
        .help("If specified, merge games and write the result to file or directory at the provided path. Erase the file at the beginning if it exists")
    val refineOutput: Boolean by option()
        .boolean()
        .default(true)
        .help("If specified, filter out invalid games and normalize sgf to KataGoDots format (relevant if only use --output-file)")
    val minDiagnosticSeverity: DiagnosticSeverity by option("--diag-severity")
        .enum<DiagnosticSeverity>()
        .default(DiagnosticSeverity.Error)
        .help("If specified, filter out diagnostics with severity lower than the provided value")

    override fun run() {
        val outputStream = PrintStream(System.out, true, UTF_8)

        val sgfFileWriter = outputFileOrDirectory?.let { SgfFileWriter(it) }

        val path = path
        if (path != null) {
            outputStream.println("SGF Directory or File mode activated...")
            outputStream.reportSpecifiedButUnusedParameter(::width, width)
            outputStream.reportSpecifiedButUnusedParameter(::height, height)
            outputStream.reportSpecifiedButUnusedParameter(captureEmptyBasesOption, captureEmptyBases)
            outputStream.reportSpecifiedButUnusedParameter(::initPosType, initPosType)
            outputStream.reportSpecifiedButUnusedParameter(::seed, seed)
            SgfAnalyser.process(outputStream, path, logFile, sgfFileWriter, refineOutput, minDiagnosticSeverity, numberOfFilesToProcess = gamesCount)
        } else {
            outputStream.println("Random games mode activated...")
            outputStream.reportSpecifiedButUnusedParameter(::gamesCountToDrop, gamesCountToDrop)
            val fieldWidth = width ?: Rules.Standard.width
            val fieldHeight = height ?: Rules.Standard.height
            val baseMode = if (captureEmptyBases ?: false) BaseMode.AnySurrounding else BaseMode.AtLeastOneOpponentDot
            val warmUpGamesCount = 10000

            outputStream.println("Start warm-up on $warmUpGamesCount games...")
            RandomGameAnalyser.process(
                fieldWidth,
                fieldHeight,
                initPosType = initPosType ?: InitPosType.Empty,
                baseMode = baseMode,
                gamesCount = warmUpGamesCount,
                seed ?: 0L,
                checkRollback = true,
                finalGameStateHandler = {},
                formatDouble = { it.toString() },
                outputStream = { },
            )
            outputStream.println("Warm-up is finished.")
            outputStream.println()

            outputStream.println("Start main loop...")
            RandomGameAnalyser.process(
                fieldWidth,
                fieldHeight,
                initPosType = initPosType ?: InitPosType.Empty,
                baseMode = baseMode,
                gamesCount,
                seed ?: 0L,
                checkRollback,
                finalGameStateHandler = {
                    sgfFileWriter?.add(Games.fromField(it), content = null, fileName = LocalDateTime.now().format(dateTimeFormatter))
                },
                formatDouble = { String.format(Locale.ENGLISH, "%.4f", it) },
                outputStream = { outputStream.println(it) },
            )
            outputStream.println("Main loop is finished.")
        }
    }

    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

    fun PrintStream.reportSpecifiedButUnusedParameter(property: KProperty<*>, value: Any?) {
        return reportSpecifiedButUnusedParameter("--" + property.name, value)
    }

    fun PrintStream.reportSpecifiedButUnusedParameter(optionName: String, value: Any?) {
        if (value != null) {
            println("⚠ The parameter `${optionName}` is specified but unused")
        }
    }
}