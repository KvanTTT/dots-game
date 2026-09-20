package org.dots.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The engine the app is shipped with is looked up where the packaging puts it, see
 * [KataGoDotsEngine.defaultSettings].
 */
class ShippedEngineTests {
    private val appResourcesProperty = "compose.application.resources.dir"
    private val previousValue: String? = System.getProperty(appResourcesProperty)

    @AfterTest
    fun restoreProperty() {
        if (previousValue != null) {
            val _ = System.setProperty(appResourcesProperty, previousValue)
        } else {
            val _ = System.clearProperty(appResourcesProperty)
        }
    }

    /**
     * The files are stated relative to the directory the app keeps them in, so that the settings of a
     * user who never touched them keep pointing at the engine of the app they are used with, whichever
     * place that app is installed in.
     */
    @Test
    fun theShippedEngineIsTakenFromThePackagedResources() {
        val _ = packageResources("katago", "katago.exe", "model.bin.gz", "analysis_dots.cfg")

        val settings = KataGoDotsEngine.defaultSettings
        val engineDirectory = KataGoDotsEngine.KATA_GO_DOTS_APP_NAME
        assertEquals(Path(engineDirectory, if (isWindows) "katago.exe" else "katago").toString(), settings.exePath)
        assertEquals(Path(engineDirectory, "model.bin.gz").toString(), settings.modelPath)
        assertEquals(Path(engineDirectory, "analysis_dots.cfg").toString(), settings.configPath)
    }

    /** A relative path leads to the file of this very app, wherever the app is installed. */
    @Test
    fun aShippedFileIsReachedWhereThisAppKeepsIt() {
        val resources = packageResources("model.bin.gz")

        assertEquals(
            resources.resolve("model.bin.gz").toString(),
            KataGoDotsEngine.resolveShippedPath(Path(KataGoDotsEngine.KATA_GO_DOTS_APP_NAME, "model.bin.gz").toString()),
        )
    }

    /** A path of the user is their own business and is taken as it is. */
    @Test
    fun aPathOfTheUserIsLeftAlone() {
        val resources = packageResources("model.bin.gz")
        val ownPath = resources.resolve("of-the-user.bin.gz").toString()

        assertEquals(ownPath, KataGoDotsEngine.resolveShippedPath(ownPath))
        assertEquals("", KataGoDotsEngine.resolveShippedPath(""))
    }

    /** An app that ships no engine at all leaves it switched off rather than half configured. */
    @Test
    fun anAppThatShipsNoEngineIsLeftWithNoSettings() {
        val _ = packageResources()

        assertEquals(KataGoDotsSettings.Default, KataGoDotsEngine.defaultSettings)
    }

    /** The model alone is no engine either. */
    @Test
    fun theEngineIsTakenOnlyWhenEveryFileOfItIsThere() {
        val _ = packageResources("model.bin.gz", "analysis_dots.cfg")

        assertEquals(KataGoDotsSettings.Default, KataGoDotsEngine.defaultSettings)
    }

    /** Lays [fileNames] out the way the packaging does and points the app at them. */
    private fun packageResources(vararg fileNames: String): Path {
        val resources = Files.createTempDirectory("appResources")
            .resolve(KataGoDotsEngine.KATA_GO_DOTS_APP_NAME)
            .also { it.createDirectories() }
        for (fileName in fileNames) {
            resources.resolve(fileName).writeText("")
        }
        val _ = System.setProperty(appResourcesProperty, resources.parent.toString())
        return resources
    }
}
