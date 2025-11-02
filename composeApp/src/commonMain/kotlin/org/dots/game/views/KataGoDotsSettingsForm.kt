package org.dots.game.views

import androidx.compose.runtime.Composable
import org.dots.game.KataGoDotsEngine
import org.dots.game.KataGoDotsSettings

@Composable
expect fun KataGoDotsSettingsForm(
    kataGoDotsSettings: KataGoDotsSettings,
    onSettingsChange: (KataGoDotsEngine) -> Unit,
    onDismiss: () -> Unit,
)
