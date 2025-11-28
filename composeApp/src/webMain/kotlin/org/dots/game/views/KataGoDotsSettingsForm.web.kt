package org.dots.game.views

import androidx.compose.runtime.Composable
import org.dots.game.KataGoDotsEngine
import org.dots.game.KataGoDotsSettings
import org.dots.game.localization.Strings

@Composable
actual fun KataGoDotsSettingsForm(
    kataGoDotsSettings: KataGoDotsSettings,
    strings: Strings,
    onSettingsChange: (KataGoDotsEngine) -> Unit,
    onDismiss: () -> Unit
) {
}