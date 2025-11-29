package org.dots.game.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_browse
import kotlinx.coroutines.launch
import org.dots.game.Diagnostic
import org.dots.game.IconButton
import org.dots.game.KataGoDotsEngine
import org.dots.game.KataGoDotsSettings
import org.dots.game.OpenFileDialog
import org.dots.game.Tooltip
import org.dots.game.localization.Strings

@Composable
actual fun KataGoDotsSettingsForm(
    kataGoDotsSettings: KataGoDotsSettings,
    strings: Strings,
    onSettingsChange: (KataGoDotsEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var exePath by remember { mutableStateOf(kataGoDotsSettings.exePath) }
    var modelPath by remember { mutableStateOf(kataGoDotsSettings.modelPath) }
    var configPath by remember { mutableStateOf(kataGoDotsSettings.configPath) }

    var kataGoDotsEngine by remember { mutableStateOf<KataGoDotsEngine?>(null) }
    var engineIsInitializing by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<Diagnostic>() }
    var selectedFileType by remember { mutableStateOf<KataGoDotsSettingsFileType?>(null) }

    var maxTimeSeconds by remember { mutableStateOf(kataGoDotsSettings.maxTime) }
    var maxVisits by remember { mutableStateOf(kataGoDotsSettings.maxVisits) }
    var maxPlayouts by remember { mutableStateOf(kataGoDotsSettings.maxPlayouts) }

    fun clearEngineAndMessages() {
        kataGoDotsEngine = null
        messages.clear()
    }

    fun invalidatePath(newPath: String, mode: KataGoDotsSettingsFileType) {
        when (mode) {
            KataGoDotsSettingsFileType.Exe -> {
                if (exePath != newPath) {
                    exePath = newPath
                    clearEngineAndMessages()
                }
            }
            KataGoDotsSettingsFileType.Model -> {
                if (modelPath != newPath) {
                    modelPath = newPath
                    clearEngineAndMessages()
                }
            }
            KataGoDotsSettingsFileType.Config -> {
                if (configPath != newPath) {
                    configPath = newPath
                    clearEngineAndMessages()
                }
            }
        }
    }

    suspend fun validateAndInitialize() {
        messages.clear()

        engineIsInitializing = true
        kataGoDotsEngine = KataGoDotsEngine.initialize(
            KataGoDotsSettings(exePath, modelPath, configPath, maxTimeSeconds, maxVisits, maxPlayouts),
            logger = {
                messages.add(it)
            }
        )

        engineIsInitializing = false
    }

    selectedFileType?.let { fileType ->
        val selectedFile = when (fileType) {
            KataGoDotsSettingsFileType.Exe -> exePath
            KataGoDotsSettingsFileType.Model -> modelPath
            KataGoDotsSettingsFileType.Config -> configPath
        }
        OpenFileDialog(strings.aiSettingsSelectFile(fileType), selectedFile, fileType.extensions) {
            if (it != null) {
                invalidatePath(it, fileType)
            }
            selectedFileType = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(800.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                @Composable
                fun FileSelector(path: String, fileType: KataGoDotsSettingsFileType) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.aiSettingsFilePath(fileType), Modifier.fillMaxWidth(0.35f))
                        TextField(
                            path, {
                                invalidatePath(it, fileType)
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).padding(top = 5.dp, bottom = 5.dp, end = 5.dp),
                            maxLines = 1,
                            singleLine = true,
                            enabled = !engineIsInitializing,
                        )
                        with (strings) {
                            IconButton(Res.drawable.ic_browse, enabled = !engineIsInitializing) {
                                selectedFileType = fileType
                            }
                        }
                    }
                }

                FileSelector(exePath, KataGoDotsSettingsFileType.Exe)
                FileSelector(modelPath, KataGoDotsSettingsFileType.Model)
                FileSelector(configPath, KataGoDotsSettingsFileType.Config)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiscreteSliderConfig("Max time (sec)", maxTimeSeconds, 0, 60, enabled = !engineIsInitializing,
                        valueRenderer = { if (it == 0) "Default" else it.toString()}) {
                        maxTimeSeconds = it
                        clearEngineAndMessages()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiscreteSliderConfig("Max visits", maxVisits, 0, 3000, step = 100, enabled = !engineIsInitializing,
                        valueRenderer = { if (it == 0) "Default" else it.toString() }) {
                        maxVisits = it
                        clearEngineAndMessages()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DiscreteSliderConfig("Max playouts", maxPlayouts, 0, 3000, step = 100, enabled = !engineIsInitializing,
                        valueRenderer = { if (it == 0) "Default" else it.toString() }) {
                        maxPlayouts = it
                        clearEngineAndMessages()
                    }
                }

                if (messages.isNotEmpty()) {
                    val vScroll = rememberScrollState()

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(max = 300.dp).verticalScroll(vScroll)) {
                        var value by remember(messages.size) { mutableStateOf(messages.joinToString("\n")) }
                        var textFieldValue by remember(messages.size) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

                        TextField(
                            textFieldValue,
                            { textFieldValue = it },
                            Modifier.fillMaxWidth(),
                            readOnly = true,
                        )
                    }

                    LaunchedEffect(messages.size) {
                        vScroll.animateScrollTo(vScroll.maxValue)
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
                    if (engineIsInitializing) {
                        Tooltip(strings.initialization) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                        }
                    } else {
                        Text(if (kataGoDotsEngine == null) strings.initialize else strings.save)
                    }
                }
            }
        }
    }
}
