package org.dots.game

/**
 * What the engine of the app is up to, which the indicator of it displays, see `EngineStateView`.
 *
 * Starting an engine is no instant matter: it reads a model of tens of megabytes and compiles the
 * shaders of its backend before it answers anything, which the very first start of it on a machine
 * spends the most time on, so it tells what it is doing along the way.
 */
sealed interface EngineState {
    /** No engine is asked for: the app carries none and the settings point at none either. */
    object NotConfigured : EngineState

    /** The engine is starting, [step] being the last thing it reported doing, `null` at the very start. */
    data class Starting(val step: String? = null) : EngineState

    /** The engine answers. */
    object Ready : EngineState

    /** The engine didn't start, [reason] being the last thing it said before it gave up, if it said any. */
    data class Failed(val reason: String) : EngineState
}
