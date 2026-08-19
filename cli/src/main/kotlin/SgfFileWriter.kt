import org.dots.game.core.Games
import org.dots.game.sgf.SgfWriter
import java.io.File

class SgfFileWriter(val sgfsFileOrDirectory: File) {
    val isDirectory: Boolean = sgfsFileOrDirectory.isDirectory

    init {
        if (!isDirectory) {
            sgfsFileOrDirectory.writeText("")
        }
    }

    fun add(games: Games, content: String?, fileName: String) {
        val result = (content ?: SgfWriter.write(games, ignoreSpaces = true)).trim()
        if (isDirectory) {
            File(sgfsFileOrDirectory, fileName).writeText(result)
        } else {
            sgfsFileOrDirectory.appendText(result)
            sgfsFileOrDirectory.appendText("\n")
        }
    }
}