package org.dots.game.views

import androidx.compose.runtime.Composable
import org.dots.game.KataGoDotsEngine
import org.dots.game.KataGoDotsSettings
import org.dots.game.localization.Strings

@Composable
expect fun KataGoDotsSettingsForm(
    kataGoDotsSettings: KataGoDotsSettings,
    strings: Strings,
    onSettingsChange: (KataGoDotsEngine) -> Unit,
    onDismiss: () -> Unit,
)

enum class KataGoDotsSettingsFileType {
    Exe,
    Model,
    Config,
}