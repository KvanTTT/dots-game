package org.dots.game.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.dots.game.EngineState
import org.dots.game.Tooltip
import org.dots.game.localization.Strings

private val indicatorSize = 20.dp
private val stateSize = 12.dp

/** An engine that answers, which is no state of an error and no color of the theme either. */
private val readyColor = Color(0xFF4CAF50)

/**
 * The state of the engine, and what it is busy with when it is: a single indicator of both, so that
 * the row of the engine neither jumps around nor holds two spinners at once, see [EngineState].
 *
 * A state of an engine that was never asked for displays nothing at all: an app that is played without
 * the engine has nothing to say about it.
 *
 * @param busyWith what the engine is doing right now, `null` when it waits for something to do.
 */
@Composable
fun EngineStateView(state: EngineState, busyWith: String?, strings: Strings, modifier: Modifier = Modifier) {
    Box(modifier.size(indicatorSize), contentAlignment = Alignment.Center) {
        when {
            busyWith != null -> Spinner(busyWith)
            state is EngineState.Starting -> Spinner(
                // What it is loading right now is the whole of the progress it reports
                strings.engineIsStarting + (state.step?.let { "\n$it" } ?: "")
            )
            state is EngineState.Failed -> State(
                MaterialTheme.colors.error,
                strings.engineDidNotStart + (state.reason.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
            )
            state is EngineState.Ready -> State(readyColor, strings.engineIsReady)
            else -> {}
        }
    }
}

@Composable
private fun Spinner(description: String) {
    Tooltip(description) {
        CircularProgressIndicator(Modifier.size(indicatorSize))
    }
}

@Composable
private fun State(color: Color, description: String) {
    Tooltip(description) {
        Box(Modifier.size(stateSize).border(1.dp, Color.White, CircleShape).clip(CircleShape).background(color))
    }
}
