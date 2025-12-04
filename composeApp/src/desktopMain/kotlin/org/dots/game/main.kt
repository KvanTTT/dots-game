package org.dots.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.dots.game.core.Games

fun main() = application {
    val previousWindowsState = loadWindowsState()
    val windowState = rememberWindowState(
        placement = previousWindowsState.placement,
        position = previousWindowsState.position,
        size = previousWindowsState.size,
    )

    val gameSettings by remember { mutableStateOf(loadClassSettings(GameSettings.Default)) }
    var games: Games? by remember { mutableStateOf(null) }

    Window(
        onCloseRequest = {
            saveWindowsState(windowState)
            saveClassSettings(gameSettings.update(games))
            exitApplication()
        },
        title = "Dots Game",
        state = windowState
    ) {
        App(gameSettings) {
            games = it
        }
    }
}