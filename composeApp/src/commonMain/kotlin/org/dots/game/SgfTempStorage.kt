package org.dots.game

/**
 * Utility for handling large SGF content that may exceed storage limits (e.g., JVM Preferences 8192 char limit).
 *
 * Strategy:
 * - When SGF content is saved, it's automatically written to temp.sgf file
 * - Returns the path to temp.sgf to be stored in settings instead of the content
 * - When loading, GameLoader.openOrLoad() handles the path transparently
 */
object SgfTempStorage {
    /**
     * Saves SGF content to temp.sgf and returns the path.
     * This path can be stored in settings and will be used to load the game on next startup.
     *
     * @param content SGF content to save
     * @return Path to temp.sgf file
     */
    fun saveToTempFile(content: String): String {
        val tempPath = getTempSgfPath()
        writeFileText(tempPath, content)
        return tempPath
    }

    /**
     * Checks if the given path is the temp.sgf file.
     *
     * @param path Path to check
     * @return true if this is temp.sgf path
     */
    fun isTempFile(path: String?): Boolean {
        if (path == null) return false
        val tempPath = getTempSgfPath()
        return path == tempPath || path.endsWith("/temp.sgf") || path.endsWith("\\temp.sgf")
    }
}
