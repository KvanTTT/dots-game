package org.dots.game

import org.dots.game.core.BaseMode
import org.dots.game.core.Rules

fun splitByUppercase(input: String): String {
    var prevUpperCase = false
    var currentWordIndex = 0

    return buildString {
        for ((index, char = value) in input.withIndex()) {
            prevUpperCase = if (char.isUpperCase()) {
                if (!prevUpperCase) {
                    if (index != 0) {
                        append(input.subSequence(currentWordIndex, index))
                        append(' ')
                    }
                    currentWordIndex = index
                }
                true
            } else {
                false
            }
        }

        append(input.subSequence(currentWordIndex, input.length))
    }
}

fun doesKataSupportRules(rules: Rules): Boolean {
    return !rules.captureByBorder && rules.baseMode != BaseMode.OnlyOpponentDots
}

const val MAX_PRACTICAL_LINK_LENGTH = 5000
private const val DEFAULT_MAX_MESSAGE_LENGTH = 400
private const val TRIMMED_MESSAGE_MARKER = "..."

fun String.trimMessageIfNecessary(maxMessageLength: Int = DEFAULT_MAX_MESSAGE_LENGTH): String {
    return if (length > maxMessageLength) {
        val endIndex = (maxMessageLength - TRIMMED_MESSAGE_MARKER.length).coerceAtLeast(0)
        substring(0, endIndex) + TRIMMED_MESSAGE_MARKER
    } else {
        this
    }
}