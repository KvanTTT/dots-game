package org.dots.game

import org.dots.game.core.BaseMode
import org.dots.game.core.Rules

fun splitByUppercase(input: String): String {
    var prevUpperCase = false
    var currentWordIndex = 0

    return buildString {
        for ((index, char) in input.withIndex()) {
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