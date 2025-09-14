package org.dots.game.sgf

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
        name = subSequence(0, colonIndex).convertSimpleText()
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
            when (val char = this@convertTextInternal[index]) {
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