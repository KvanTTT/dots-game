import org.dots.game.core.Games
import org.dots.game.sgf.SgfWriter
import java.io.File

class SgfFileWriter(val sgfsFileOrDirectory: File) {
    val isDirectory: Boolean = sgfsFileOrDirectory.isDirectory || (!sgfsFileOrDirectory.exists() && sgfsFileOrDirectory.extension.isEmpty())
    private var fileInitialized = false

    fun add(games: Games, content: String?, fileName: String) {
        val result = (content ?: SgfWriter.write(games, ignoreSpaces = true)).trim()
        if (isDirectory) {
            val outputFile = File(sgfsFileOrDirectory, fileName)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(result)
        } else {
            if (!fileInitialized) {
                sgfsFileOrDirectory.writeText(result)
                fileInitialized = true
            } else {
                sgfsFileOrDirectory.appendText(result)
            }
            sgfsFileOrDirectory.appendText("\n")
        }
    }
}