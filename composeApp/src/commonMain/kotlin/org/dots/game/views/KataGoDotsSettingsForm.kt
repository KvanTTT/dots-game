package org.dots.game.views

import androidx.compose.runtime.Composable
import org.dots.game.KataGoDotsEngine
import org.dots.game.KataGoDotsSettings
import org.dots.game.OS
import org.dots.game.localization.Strings
import org.dots.game.platform

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
    Config;

    // Initialize lazily to prevent potential initialization order issues
    val extensions by lazy {
        when (this) {
            Exe -> listOf(if (platform.os == OS.Windows) "exe" else "")
            Model -> listOf("bin", "bin.gz")
            Config -> listOf("cfg")
        }
    }
}