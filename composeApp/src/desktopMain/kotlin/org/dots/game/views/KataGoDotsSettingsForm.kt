package org.dots.game.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.dots.game.Diagnostic
import org.dots.game.KataGoDotsEngine
import org.dots.game.KataGoDotsSettings
import org.dots.game.OpenFileDialog
import org.dots.game.Platform
import org.dots.game.platform

@Composable
actual fun KataGoDotsSettingsForm(
    kataGoDotsSettings: KataGoDotsSettings,
    onSettingsChange: (KataGoDotsEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var exePath by remember { mutableStateOf(kataGoDotsSettings.exePath) }
    var modelPath by remember { mutableStateOf(kataGoDotsSettings.modelPath) }
    var configPath by remember { mutableStateOf(kataGoDotsSettings.configPath) }

    var kataGoDotsEngine by remember { mutableStateOf<KataGoDotsEngine?>(null) }
    var engineIsInitializing by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<Diagnostic>()) }
    var selectedFileMode by remember { mutableStateOf<FileMode?>(null) }

    var maxTimeSeconds by remember { mutableStateOf(kataGoDotsSettings.maxTime) }
    var maxVisits by remember { mutableStateOf(kataGoDotsSettings.maxVisits) }
    var maxPlayouts by remember { mutableStateOf(kataGoDotsSettings.maxPlayouts) }

    fun invalidatePath(newPath: String, mode: FileMode) {
        when (mode) {
            FileMode.Exe -> {
                if (exePath != newPath) {
                    exePath = newPath
                    kataGoDotsEngine = null
                }
            }
            FileMode.Model -> {
                if (modelPath != newPath) {
                    modelPath = newPath
                    kataGoDotsEngine = null
                }
            }
            FileMode.Config -> {
                if (configPath != newPath) {
                    configPath = newPath
                    kataGoDotsEngine = null
                }
            }
        }
    }

    suspend fun validateAndInitialize() {
        val newMessages = mutableListOf<Diagnostic>()

        messages = mutableListOf()

        engineIsInitializing = true
        kataGoDotsEngine = KataGoDotsEngine.initialize(
            KataGoDotsSettings(exePath, modelPath, configPath, maxTimeSeconds, maxVisits, maxPlayouts),
            logger = {
                newMessages.add(it)
            }
        )
        engineIsInitializing = false

        messages = newMessages
    }

    if (selectedFileMode != null) {
        val allowedExtensions = when (selectedFileMode) {
            FileMode.Exe -> listOf(if (platform == Platform.DESKTOP_WINDOWS) "exe" else "")
            FileMode.Model -> listOf("bin.gz")
            FileMode.Config -> listOf("cfg")
            null -> emptyList() // TODO: it shouldn't be a warning, see KT-82211
        }
        val selectedFile = when (selectedFileMode) {
            FileMode.Exe -> exePath
            FileMode.Model -> modelPath
            FileMode.Config -> configPath
            null -> null // TODO: it shouldn't be a warning, see KT-82211
        }
        OpenFileDialog("Select ${selectedFileMode!!.name.lowercase()} file", selectedFile, allowedExtensions) {
            if (it != null) {
                invalidatePath(it, selectedFileMode!!)
            }
            selectedFileMode = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(800.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                @Composable
                fun FileSelector(path: String, fileMode: FileMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$fileMode path", Modifier.fillMaxWidth(0.3f))
                        TextField(
                            path, {
                                invalidatePath(it, fileMode)
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 10.dp),
                            maxLines = 1,
                            singleLine = true,
                            enabled = !engineIsInitializing,
                        )
                        Button(
                            onClick = { selectedFileMode = fileMode },
                            Modifier.padding(horizontal = 10.dp),
                            enabled = !engineIsInitializing,
                        ) {
                            Text("...")
                        }
                    }
                }

                FileSelector(exePath, FileMode.Exe)
                FileSelector(modelPath, FileMode.Model)
                FileSelector(configPath, FileMode.Config)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiscreteSliderConfig("Max time (sec)", maxTimeSeconds, 0, 60, enabled = !engineIsInitializing,
                        valueRenderer = { if (it == 0) "Default" else it.toString()}) {
                        maxTimeSeconds = it
                        kataGoDotsEngine = null
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiscreteSliderConfig("Max visits", maxVisits, 0, 3000, step = 100, enabled = !engineIsInitializing,
                        valueRenderer = { if (it == 0) "Default" else it.toString() }) {
                        maxVisits = it
                        kataGoDotsEngine = null
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiscreteSliderConfig("Max playouts", maxPlayouts, 0, 3000, step = 100, enabled = !engineIsInitializing,
                        valueRenderer = { if (it == 0) "Default" else it.toString() }) {
                        maxPlayouts = it
                        kataGoDotsEngine = null
                    }
                }

                if (messages.isNotEmpty() && kataGoDotsEngine != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            messages.joinToString("\n"),
                            {},
                            Modifier.fillMaxWidth(),
                            readOnly = true,
                            maxLines = 10,
                        )
                    }
                }

                Button(
                    onClick = {
                        kataGoDotsEngine?.let {
                            onSettingsChange(it)
                        } ?: run {
                            coroutineScope.launch {
                                validateAndInitialize()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp),
                    enabled = !engineIsInitializing,
                ) {
                    Text(if (kataGoDotsEngine == null) {
                        if (engineIsInitializing) "Checking..." else "Check"
                    } else {
                        "Save"
                    })
                }
            }
        }
    }
}

private enum class FileMode {
    Exe,
    Model,
    Config,
}