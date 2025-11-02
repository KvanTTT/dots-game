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
    var messages by remember { mutableStateOf(listOf<Diagnostic>()) }

    suspend fun validate() {
        val newMessages = mutableListOf<Diagnostic>()

        kataGoDotsEngine = KataGoDotsEngine.initialize(
            KataGoDotsSettings(exePath, modelPath, configPath),
            onMessage = {
                newMessages.add(it)
            }
        )

        messages = newMessages
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(800.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Exe path", Modifier.fillMaxWidth(configKeyTextFraction))
                    TextField(
                        exePath, {
                            exePath = it
                            kataGoDotsEngine = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        maxLines = 1,
                        singleLine = true,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Model path", Modifier.fillMaxWidth(configKeyTextFraction))
                    TextField(
                        modelPath, {
                            modelPath = it
                            kataGoDotsEngine = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        maxLines = 1,
                        singleLine = true,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Config path", Modifier.fillMaxWidth(configKeyTextFraction))
                    TextField(
                        configPath, {
                            configPath = it
                            kataGoDotsEngine = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        maxLines = 1,
                        singleLine = true,
                    )
                }


                if (messages.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(messages.joinToString("\n"), {}, Modifier.fillMaxWidth(), readOnly = true)
                    }
                }

                Button(
                    onClick = {
                        kataGoDotsEngine?.let {
                            onSettingsChange(it)
                        } ?: run {
                            coroutineScope.launch {
                                validate()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp),
                ) {
                    Text(if (kataGoDotsEngine == null) "Check" else "Save")
                }
            }
        }
    }
}