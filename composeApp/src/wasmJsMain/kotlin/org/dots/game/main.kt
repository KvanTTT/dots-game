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

    val gameSettingsFromUrl = GameSettings.parseUrlParams(window.location.search, 0)
    if (gameSettingsFromUrl.let { it.path != null || it.sgf != null || it.game != null || it.node != null }) {
        saveClassSettings(gameSettingsFromUrl)
        window.location.search = ""
    }

    ComposeViewport(document.body!!) {
        val gameSettings = remember {
            loadClassSettings(GameSettings.Default)
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
