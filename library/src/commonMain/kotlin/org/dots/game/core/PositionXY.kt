package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class PositionXY internal constructor(val position: Int) : Comparable<PositionXY> {
    companion object {
        const val COORDINATE_BITS_COUNT = 6
        const val MASK = (1 shl COORDINATE_BITS_COUNT) - 1
    }

    constructor(x: Int, y: Int) : this((x shl COORDINATE_BITS_COUNT) or (y and MASK))

    override fun toString(): String = "($x;$y)"

    val x: Int get() = position shr COORDINATE_BITS_COUNT

    val y: Int get() = position and MASK

    operator fun component1(): Int = x
    operator fun component2(): Int = y

    operator fun minus(other: PositionXY): Pair<Int, Int> {
        return this.x - other.x to this.y - other.y
    }

    override fun compareTo(other: PositionXY): Int {
        return position.compareTo(other.position)
    }
}
