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
        val gameSettings by remember { mutableStateOf(loadClassSettings(GameSettings.Default)) }

        val gameSettingsFromUrl = GameSettings.parseUrlParams(window.location.search, 0)
        with(gameSettingsFromUrl) {
            path?.let { gameSettings.path = it }
            sgf?.let { gameSettings.sgf = it }
            game?.let { gameSettings.game = it }
            node?.let { gameSettings.node = it }
        }

        var games: Games? by remember { mutableStateOf(null) }

        window.addEventListener("beforeunload") { _ ->
            saveClassSettings(gameSettings.update(games))
        }

        App(gameSettings) {
            games = it
        }
    }
}
