package org.dots.game.core

abstract class ClassSettings<T : ClassSettings<T>> {
    abstract val Default : ClassSettings<T>
}