package org.dots.game.core

class PositionsList private constructor(val internalArray: ShortArray, initialSize: Int, val fieldStride: Int) {
    companion object {
        val EMPTY: PositionsList = PositionsList(0, 0)
    }

    var size: Int = initialSize
        private set

    fun clear() {
        size = 0
    }

    constructor(maxSize: Int, fieldStride: Int) : this(ShortArray(maxSize), 0, fieldStride)

    fun add(position: Position) {
        internalArray[size++] = position.value
    }

    fun addAll(otherPositionsList: PositionsList) {
        require(fieldStride == otherPositionsList.fieldStride)
        otherPositionsList.iterate { add(it) }
    }

    fun get(index: Int): Position {
        return Position(internalArray[index])
    }

    fun pop(): Position {
        return Position(internalArray[--size])
    }

    internal inline fun iterate(func: (position: Position) -> Unit) {
        for (index in 0..<size) {
            func(Position(internalArray[index]))
        }
    }

    internal inline fun iterateWithIndex(func: (index: Int, position: Position) -> Unit) {
        for (index in 0..<size) {
            func(index, Position(internalArray[index]))
        }
    }

    internal inline fun <R : Comparable<R>> minOf(selector: (Position) -> R): R {
        return internalArray.minOf {
            selector(Position(it))
        }
    }

    internal inline fun map(transformer: (Position) -> Position): PositionsList {
        return PositionsList(internalArray.size, fieldStride).also { newList ->
            iterate {
                newList.add(transformer(it))
            }
        }
    }

    fun copy(): PositionsList {
        return PositionsList(internalArray.copyOf(size), size, fieldStride)
    }

    fun toList(): List<Position> {
        return ArrayList<Position>(internalArray.size).also { result ->
            iterate { result.add(it) }
        }
    }

    fun toHashSet(): HashSet<Position> {
        return HashSet<Position>(internalArray.size).also { result ->
            iterate { result.add(it) }
        }
    }

    override fun toString(): String {
        return buildString {
            iterate {
                append(it.toXY(fieldStride).toString())
                append("; ")
            }
        }
    }
}

class DotStatesList private constructor(val internalArray: ByteArray, initialSize: Int) {
    var size: Int = initialSize
        private set

    constructor(maxSize: Int) : this(ByteArray(maxSize), 0)

    fun add(dotState: DotState) {
        internalArray[size++] = dotState.value
    }

    fun get(index: Int): DotState {
        return DotState(internalArray[index])
    }

    internal inline fun iterate(func: (dotState: DotState) -> Unit) {
        for (index in 0 until size) {
            func(DotState(internalArray[index]))
        }
    }

    fun copy(): DotStatesList {
        return DotStatesList(internalArray.copyOf(size), size)
    }

    override fun toString(): String {
        return buildString {
            iterate {
                append(it.toString())
                append("; ")
            }
        }
    }
}
