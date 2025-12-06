package org.dots.game

expect object Gzip {
    fun compress(input: ByteArray): ByteArray
    fun decompress(input: ByteArray): ByteArray
}

val emptyByteArray = ByteArray(0)