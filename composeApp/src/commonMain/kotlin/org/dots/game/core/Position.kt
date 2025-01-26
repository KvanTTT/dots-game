package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class Position private constructor(val position: Int) {
    companion object {
        val ZERO = Position(0, 0)
        const val COORDINATE_BITS_COUNT = 8
        const val MASK = (1 shl COORDINATE_BITS_COUNT) - 1
    }

    constructor(x: Int, y: Int) : this((x shl COORDINATE_BITS_COUNT) or (y and MASK)) {
        require(x >= 0 && y >= 0)
    }

    override fun toString(): String = "($x;$y)"

    val x: Int get() = position shr COORDINATE_BITS_COUNT

    val y: Int get() = position and MASK

    operator fun component1(): Int = x
    operator fun component2(): Int = y
}

infix fun Int.x(that: Int): Position = Position(this, that)