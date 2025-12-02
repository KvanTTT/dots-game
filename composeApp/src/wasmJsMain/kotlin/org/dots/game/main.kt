package org.dots.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.dots.game.core.Games

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    window.history.pushState(null, "", window.location.href)
    window.onpopstate = {
        window.history.pushState(null, "", window.location.href)
    }

    ComposeViewport(document.body!!) {
        val currentGameSettings by remember { mutableStateOf(loadClassSettings(CurrentGameSettings.Default)) }
        var games: Games? by remember { mutableStateOf(null) }

        window.addEventListener("beforeunload") { _ ->
            saveClassSettings(currentGameSettings.update(games))
        }

        App(currentGameSettings) {
            games = it
        }
    }
}