package org.dots.game.core

import kotlin.jvm.JvmInline

@JvmInline
value class Position internal constructor(val value: Short) : Comparable<Position> {
    companion object {
        val GAME_OVER: Position = Position(1, 0, 0) // It's also equivalent to PASS in Go
    }

    internal constructor(x: Int, y: Int, fieldStride: Int) : this((y * fieldStride + x).toShort())

    fun getX(fieldStride: Int): Int = value % fieldStride

    fun getY(fieldStride: Int): Int = value / fieldStride

    fun toXY(fieldStride: Int): PositionXY {
        return PositionXY(getX(fieldStride), getY(fieldStride))
    }

    fun xm1y(): Position = Position((value - 1).toShort())

    fun xm1ym1(fieldStride: Int): Position = Position((value - 1 - fieldStride).toShort())

    fun xym1(fieldStride: Int): Position = Position((value - fieldStride).toShort())

    fun xp1ym1(fieldStride: Int): Position = Position((value + 1 - fieldStride).toShort())

    fun xp1y(): Position = Position((value + 1).toShort())

    fun xp1yp1(fieldStride: Int): Position = Position((value + 1 + fieldStride).toShort())

    fun xyp1(fieldStride: Int): Position = Position((value + fieldStride).toShort())

    fun xm1yp1(fieldStride: Int): Position = Position((value - 1 + fieldStride).toShort())

    val isGameOverMove: Boolean
        get() = this == GAME_OVER

    override fun compareTo(other: Position): Int {
        return value.compareTo(other.value)
    }

    override fun toString(): String {
        return value.toString()
    }
}