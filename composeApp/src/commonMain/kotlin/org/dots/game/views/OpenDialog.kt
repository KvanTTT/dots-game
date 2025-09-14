package org.dots.game.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.dots.game.openOrLoad
import org.dots.game.Diagnostic
import org.dots.game.InputType
import org.dots.game.buildLineOffsets
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.toLineColumnDiagnostic

@Composable
fun OpenDialog(
    rules: Rules?,
    openGameSettings: OpenGameSettings,
    onDismiss: () -> Unit,
    onConfirmation: (games: Games, newOpenGameSettings: OpenGameSettings) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var pathOrContentTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var contentTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var previousInput: String? = null
    var diagnostics by remember { mutableStateOf<List<Diagnostic>>(listOf()) }
    var inputType by remember { mutableStateOf<InputType>(InputType.Other) }
    var games by remember { mutableStateOf(Games()) }
    var newOpenGameSettings by remember { mutableStateOf(openGameSettings)}

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(500.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Path or Content: ", Modifier.fillMaxWidth(0.3f))
                    TextField(
                        pathOrContentTextFieldValue,
                        {
                            pathOrContentTextFieldValue = it
                            val text = it.text
                            if (text != previousInput) {
                                previousInput = text

                                coroutineScope.launch {
                                    diagnostics = buildList {
                                        val result = openOrLoad(text, rules, addFinishingMove = newOpenGameSettings.addFinishingMove) { diagnostic ->
                                            add(diagnostic)
                                        }
                                        inputType = result.inputType
                                        if (inputType !is InputType.Content) {
                                            contentTextFieldValue = TextFieldValue(result.content ?: "")
                                        }
                                        games = result.games
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = inputType !is InputType.Content,
                        maxLines = if (inputType is InputType.Content) 5 else 1,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(newOpenGameSettings.rewindToEnd, onCheckedChange = {
                        newOpenGameSettings = newOpenGameSettings.copy(rewindToEnd = it)
                    })
                    Text("Rewind to End")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(newOpenGameSettings.addFinishingMove, onCheckedChange = {
                        newOpenGameSettings = newOpenGameSettings.copy(addFinishingMove = it)
                    })
                    Text("Add Finishing Move")
                }

                if (inputType is InputType.InputTypeWithName && diagnostics.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            contentTextFieldValue,
                            { contentTextFieldValue = it },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            maxLines = 5,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        )
                    }
                }

                fun getContextTextFieldValue() = if (inputType is InputType.Content) pathOrContentTextFieldValue else contentTextFieldValue

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { getContextTextFieldValue().text.buildLineOffsets() }
                        items(diagnostics.size) { index ->
                            val diagnostic = diagnostics[index]
                            var cardModifier = Modifier.fillMaxWidth()
                            val textSpan = diagnostic.textSpan
                            if (textSpan != null) {
                                cardModifier = cardModifier.then(Modifier.clickable(onClick = {
                                    val start = textSpan.start
                                    val end = textSpan.end
                                    val textFieldValue = getContextTextFieldValue()
                                    val newTextFieldValue = textFieldValue.copy(selection = TextRange(start,
                                        if (end == start) {
                                            if (end < textFieldValue.text.length - 1)
                                                end + 1
                                            else if (end > 0)
                                                end - 1
                                            else
                                                end
                                        } else {
                                            end
                                        }
                                    ))
                                    if (inputType is InputType.Content) {
                                        pathOrContentTextFieldValue = newTextFieldValue
                                    } else {
                                        contentTextFieldValue = newTextFieldValue
                                    }
                                }))
                            }
                            Card(
                                modifier = cardModifier,
                                elevation = 4.dp
                            ) {
                                Text(
                                    text = diagnostic.toLineColumnDiagnostic(lineOffsets).toString(),
                                    style = MaterialTheme.typography.body1,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { games.takeIf { it.isNotEmpty() }?.let { onConfirmation(it, newOpenGameSettings) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp),
                    enabled = games.isNotEmpty()
                ) {
                    Text("Open")
                }
            }
        }
    }
}

data class OpenGameSettings(
    val rewindToEnd: Boolean,
    val addFinishingMove: Boolean,
) {
    companion object {
        val Default = OpenGameSettings(rewindToEnd = true, addFinishingMove = false)
    }
}
