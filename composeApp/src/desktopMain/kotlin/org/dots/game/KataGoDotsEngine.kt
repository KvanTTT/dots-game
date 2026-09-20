package org.dots.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dots.game.core.Field
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val userHome: String = System.getProperty("user.home")
private val osName: String = System.getProperty("os.name").lowercase()
internal val isWindows: Boolean = osName.startsWith("win")
internal val isMacOs: Boolean = osName.startsWith("mac")

/**
 * The directory Compose keeps the resources of this very platform in, the common ones being kept apart
 * from them, see `appResourcesRootDir` of the build.
 */
private val platformResourcesDir: String =
    (if (isWindows) "windows" else if (isMacOs) "macos" else "linux") +
            if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) "-arm64" else "-x64"

/**
 * Runs KataGoDots as a process of its own and asks it the questions of the app, no matter which
 * [EngineProtocol] the process is talked to by: the protocol itself is [KataGoDotsProtocol].
 */
actual class KataGoDotsEngine internal constructor(private val protocol: KataGoDotsProtocol) {
    actual companion object {
        const val KATA_GO_DOTS_APP_NAME = "KataGoDots"

        /** Where the packaged app is told to find the files it is shipped with, see `appResourcesRootDir`. */
        private const val APP_RESOURCES_PROPERTY = "compose.application.resources.dir"
        /** The directory the sources keep those very files in, laid out the way the packaging takes them. */
        private const val APP_RESOURCES_DIR = "appResources"
        private const val COMMON_RESOURCES_DIR = "common"

        private const val CONFIG_NAME = "analysis_dots.cfg"
        private const val MODEL_NAME = "model.bin.gz"
        private val EXE_NAME = if (isWindows) "katago.exe" else "katago"

        /**
         * Where the files the app is shipped with are looked up: a packaged app has all of them under the
         * single directory it is told, while a run from the sources has them under the two the packaging
         * merges into it, the common one and the one of this very platform.
         */
        private val shippedRoots: List<Path>
            get() {
                val packaged = System.getProperty(APP_RESOURCES_PROPERTY)
                return if (packaged != null) {
                    listOf(Paths.get(packaged))
                } else {
                    val sources = Paths.get(System.getProperty("user.dir"), APP_RESOURCES_DIR)
                    listOf(sources.resolve(COMMON_RESOURCES_DIR), sources.resolve(platformResourcesDir))
                }
            }

        /** @return the shipped [name] the way the settings state it, `null` if the app carries no such file. */
        private fun shipped(name: String): String? {
            val relative = Paths.get(KATA_GO_DOTS_APP_NAME, name)
            return relative.toString().takeIf { _ -> shippedRoots.any { Files.isRegularFile(it.resolve(relative)) } }
        }

        /**
         * Where a file of the settings actually is: a relative path is one of the files the app is shipped
         * with and is looked up under the directory this very app keeps them in, while a path of the user
         * is absolute and is taken as it is.
         *
         * This is what keeps an app that was updated or moved playing with the engine it comes with: the
         * settings outlive the place the app was installed in, and an absolute path of an engine that is
         * gone would leave the app with no engine at all.
         */
        fun resolveShippedPath(path: String): String {
            if (path.isEmpty()) return path
            val relative = runCatching { Paths.get(path) }.getOrNull()?.takeUnless { it.isAbsolute } ?: return path

            val roots = shippedRoots
            val resolved = roots.map { it.resolve(relative) }
            return (resolved.firstOrNull { Files.isRegularFile(it) } ?: resolved.first()).toString()
        }

        /** The settings as the files of them are actually reached, see [resolveShippedPath]. */
        private fun KataGoDotsSettings.resolved(): KataGoDotsSettings = copy(
            exePath = resolveShippedPath(exePath),
            modelPath = resolveShippedPath(modelPath),
            configPath = resolveShippedPath(configPath),
        )

        /**
         * Where the engine writes its log. An installed app is started in a directory it may not write to,
         * and the log directory a config states is relative to that one, so the engine is given a directory
         * of the user instead, see `KataGoDotsProtocol.startProcess`.
         */
        val DEFAULT_LOGS_DIR: String = when {
            isWindows -> Paths.get(System.getenv("LOCALAPPDATA") ?: userHome, KATA_GO_DOTS_APP_NAME, "logs")
            isMacOs -> Paths.get(userHome, "Library", "Logs", KATA_GO_DOTS_APP_NAME)
            else -> Paths.get(System.getenv("XDG_STATE_HOME") ?: "$userHome/.local/state", KATA_GO_DOTS_APP_NAME, "logs")
        }.toString()

        actual const val IS_SUPPORTED = true

        /**
         * The engine the app is shipped with: all three of its files or none of them, so that an app that
         * was packaged without one leaves the engine off rather than half configured.
         *
         * The files are stated relative to the directory the app keeps them in rather than by the place
         * this very app happens to be installed in, so that the settings of a user who never touched them
         * keep pointing at the engine of the app they are used with, see [resolveShippedPath].
         */
        actual val defaultSettings: KataGoDotsSettings
            get() {
                val exePath = shipped(EXE_NAME) ?: return KataGoDotsSettings.Default
                val modelPath = shipped(MODEL_NAME) ?: return KataGoDotsSettings.Default
                val configPath = shipped(CONFIG_NAME) ?: return KataGoDotsSettings.Default
                return KataGoDotsSettings.Default.copy(
                    exePath = exePath, modelPath = modelPath, configPath = configPath,
                )
            }

        actual suspend fun initialize(kataGoDotsSettings: KataGoDotsSettings, logger: (Diagnostic) -> Unit): KataGoDotsEngine? {
            if (kataGoDotsSettings.exePath.isEmpty()) {
                return null
            }

            // The engine is started by the files themselves rather than by the way the settings state them
            val settings = kataGoDotsSettings.resolved()
            try {
                return withContext(Dispatchers.IO) {
                    val protocol = when (settings.protocol) {
                        EngineProtocol.Analysis -> AnalysisProtocol.initialize(settings, logger)
                        EngineProtocol.Gtp -> GtpProtocol.initialize(settings, logger)
                    }

                    protocol?.let { KataGoDotsEngine(it) }
                }
            } catch (e: Exception) {
                logger(Diagnostic(e.message ?: e.toString(), severity = DiagnosticSeverity.Critical))
                return null
            }
        }
    }

    actual val settings: KataGoDotsSettings
        get() = protocol.settings

    actual val logger: (Diagnostic) -> Unit
        get() = protocol.logger

    /**
     * The GTP engine keeps a position of its own, which is not a part of the API of the app but is what
     * the tests of the synchronization address it by, see [GtpProtocol.sync].
     */
    internal val gtp: GtpProtocol?
        get() = protocol as? GtpProtocol

    actual suspend fun generateMove(field: Field, player: Player?, clock: PlayerClock?): MoveInfo? =
        protocol.generateMove(field, player, clock)

    actual suspend fun analyze(field: Field, player: Player?, withOwnership: Boolean): MoveAnalysis? =
        protocol.analyze(field, player, withOwnership)

    actual suspend fun analyzeGame(
        field: Field,
        moves: List<MoveInfo>,
        turnNumbers: List<Int>,
        onTurnAnalyzed: (turnNumber: Int, analysis: MoveAnalysis) -> Unit,
    ) = protocol.analyzeGame(field, moves, turnNumbers, onTurnAnalyzed)

    actual fun close() = protocol.close()
}
