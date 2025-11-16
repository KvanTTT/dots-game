package org.dots.game

import kotlin.math.abs

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

fun Double.isAlmostEqual(other: Double): Boolean {
    return abs(this - other) < 1e-9
}