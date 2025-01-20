package org.dots.game.sgf

fun String.convertSimpleText(): String = convertTextInternal(simpleText = true)

fun String.convertText(): String = convertTextInternal(simpleText = false)

private fun String.convertTextInternal(simpleText: Boolean): String {
    return buildString {
        var index = 0

        fun skipNextIfNeeded(charToCheck: Char) {
            if (this@convertTextInternal.elementAtOrNull(index + 1) == charToCheck) {
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
                        skipNextIfNeeded('\n')
                    } else if (nextChar == '\n') {
                        index++
                        skipNextIfNeeded('\r')
                    }
                }
                '\r' -> {
                    if (simpleText) {
                        append(" ")
                        skipNextIfNeeded('\n')
                    } else {
                        append(char)
                    }
                }
                '\n' -> {
                    if (simpleText) {
                        append(" ")
                        skipNextIfNeeded('\r')
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
                        add(index)
                    } else {
                        add(index)
                    }
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