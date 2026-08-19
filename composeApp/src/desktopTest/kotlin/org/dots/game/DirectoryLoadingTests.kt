package org.dots.game

import kotlinx.coroutines.runBlocking
import org.dots.game.ExampleTestData.exampleSgf
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every sgf of [exampleSgf] content. */
private const val GAMES_COUNT_PER_FILE = 2

class DirectoryLoadingTests {
    private val directory: File = Files.createTempDirectory("dots-game-directory-loading").toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun inputType() {
        checkInputType(directory.path, directory.name)
        checkInputType(directory.path + File.separator, directory.name)
        checkInputType("\"${directory.path}\"", directory.name)
        checkInputType("  ${directory.path}  ", directory.name)
    }

    @Test
    fun mergeFilesRecursively() {
        writeFile("first.sgf", exampleSgf)
        writeFile("nested/second.sgfs", exampleSgf)
        writeFile("nested/deeply/third.SGF", exampleSgf)
        // Files with other extensions should be ignored
        writeFile("readme.md", "It's not an sgf at all")
        writeFile("nested/empty.sgf", "")

        val diagnostics = mutableListOf<GameLoader.GameLoaderDiagnostic>()
        val loadResult = openOrLoad(directory.path) { diagnostics += it }

        // The files are merged in the order of their sorted paths: `first.sgf`, `nested/deeply/third.SGF`, `nested/second.sgfs`
        assertEquals((exampleSgf.trim() + "\n").repeat(3), loadResult.content)
        assertEquals(GAMES_COUNT_PER_FILE * 3, loadResult.games.size)
        assertEquals(
            listOf("Info: Merged 3 sgf(s) file(s) of the directory `${directory.name}`"),
            diagnostics.map { it.toString() }
        )
    }

    @Test
    fun directoryWithoutSgfFiles() {
        writeFile("readme.md", "It's not an sgf at all")

        val diagnostics = mutableListOf<GameLoader.GameLoaderDiagnostic>()
        val loadResult = openOrLoad(directory.path) { diagnostics += it }

        assertTrue(loadResult.games.isEmpty())
        assertTrue(diagnostics.single().diagnostic.message.contains("doesn't contain .sgf or .sgfs files"))
    }

    private fun checkInputType(input: String, expectedName: String) {
        val inputType = assertIs<InputType.SgfDirectory>(InputTypeDetector.getInputType(input))
        assertEquals(expectedName, inputType.name)
    }

    private fun writeFile(relativePath: String, content: String) {
        val file = directory.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun openOrLoad(
        pathOrContent: String,
        diagnosticReporter: (GameLoader.GameLoaderDiagnostic) -> Unit = { },
    ): LoadResult = runBlocking {
        GameLoader.openOrLoad(pathOrContent, rules = null, addFinishingMove = false, diagnosticReporter)
    }
}
