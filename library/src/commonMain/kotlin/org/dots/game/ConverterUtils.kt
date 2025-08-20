package org.dots.game

import org.dots.game.core.AppInfo

fun String.convertAppInfo(): AppInfo {
    // Handle escaping
    var colonIndex = -1
    do {
        colonIndex = indexOf(':', colonIndex + 1)
        if (colonIndex == -1) break
        if (elementAtOrNull(colonIndex - 1) != '\\') break
    } while (true)

    val name: String
    val version: String?
    if (colonIndex != -1) {
        name = subSequence(0, colonIndex) .convertSimpleText()
        version = subSequence(colonIndex + 1, length).convertSimpleText()
    } else {
        name = this
        version = null
    }

    return AppInfo(name, version)
}

fun CharSequence.convertSimpleText(): String = convertTextInternal(simpleText = true)

fun CharSequence.convertText(): String = convertTextInternal(simpleText = false)

private fun CharSequence.convertTextInternal(simpleText: Boolean): String {
    return buildString {
        var index = 0

        fun skipNewLineIfNeeded() {
            if (this@convertTextInternal.elementAtOrNull(index + 1) == '\n') {
                index++
            }
        }

        while (index < this@convertTextInternal.length) {
            val char = this@convertTextInternal[index]
            when (char) {
                '\\' -> {
                    val nextChar = this@convertTextInternal.elementAtOrNull(index + 1)
                    if (nextChar == '\r') {
                        index++
                        skipNewLineIfNeeded()
                    } else if (nextChar == '\n') {
                        index++
                    }
                }
                '\r' -> {
                    if (simpleText) {
                        append(" ")
                        skipNewLineIfNeeded()
                    } else {
                        append(char)
                    }
                }
                '\n' -> {
                    if (simpleText) {
                        append(" ")
                    } else {
                        append(char)
                    }
                }
                else -> append(char)
            }
            index++
        }
    }
}

data class LineColumn(val line: Int, val column: Int) {
    override fun toString(): String {
        return "$line:$column"
    }
}

fun CharSequence.buildLineOffsets(): List<Int> {
    return buildList {
        var index = 0
        add(0)
        while (index < this@buildLineOffsets.length) {
            when (this@buildLineOffsets[index]) {
                '\r' -> {
                    index++
                    if (this@buildLineOffsets.elementAtOrNull(index) == '\n') {
                        index++
                    }
                    add(index)
                }
                '\n' -> {
                    index++
                    add(index)
                }
                else -> {
                    index++
                }
            }
        }
    }
}

/**
 * The behavior is unspecified for out-of-bound input (for internal usage)
 */
fun Int.getLineColumn(lineOffsets: List<Int>): LineColumn {
    lineOffsets.binarySearch { it.compareTo(this) }.let { lineOffset ->
        return if (lineOffset < 0) {
            val line = -lineOffset - 2
            LineColumn(line + 1, this - lineOffsets[line] + 1)
        } else {
            LineColumn(lineOffset + 1, 1)
        }
    }
}

/**
 * Useful for printing score-related things
 * If there is no Komi, it's quite strange to see fractional numbers even 0.0
 */
fun Double.toNeatNumber(): Number {
    return if (this % 1 == 0.0) toInt() else this
}