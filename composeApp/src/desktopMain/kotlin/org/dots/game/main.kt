package org.dots.game

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val previousWindowsState = loadWindowsState()
    val windowState = rememberWindowState(
        placement = previousWindowsState.placement,
        position = previousWindowsState.position,
        size = previousWindowsState.size,
    )

    Window(
        onCloseRequest = {
            saveWindowsState(windowState)
            exitApplication()
        },
        title = "Dots Game",
        state = windowState
    ) {
        App()
    }
}