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
import org.dots.game.Diagnostic
import org.dots.game.GameLoader
import org.dots.game.InputType
import org.dots.game.LoadResult
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
    var pathOrContentTextFieldValue by remember { mutableStateOf(TextFieldValue(openGameSettings.path ?: openGameSettings.content ?: "")) }
    var contentTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var previousInput: String? = null
    var diagnostics by remember { mutableStateOf<List<Diagnostic>>(listOf()) }
    var loadResult by remember { mutableStateOf<LoadResult?>(null) }
    var rewindToEnd by remember { mutableStateOf(openGameSettings.rewindToEnd) }
    var addFinishingMove by remember { mutableStateOf(openGameSettings.addFinishingMove) }

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
                                        loadResult = GameLoader.openOrLoad(text, rules, addFinishingMove = addFinishingMove) { diagnostic ->
                                            add(diagnostic)
                                        }
                                        if (loadResult?.inputType is InputType.InputTypeWithPath) {
                                            contentTextFieldValue = TextFieldValue(loadResult?.content ?: "")
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = loadResult?.inputType is InputType.InputTypeWithPath,
                        maxLines = if (loadResult?.inputType is InputType.InputTypeWithPath) 1 else 5,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rewindToEnd, onCheckedChange = {
                        rewindToEnd = it
                    })
                    Text("Rewind to End")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(addFinishingMove, onCheckedChange = {
                        addFinishingMove = it
                    })
                    Text("Add Finishing Move")
                }

                if (loadResult?.inputType is InputType.InputTypeWithPath && diagnostics.isNotEmpty()) {
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

                fun getContentTextFieldValue() = if (loadResult?.inputType is InputType.InputTypeWithPath) contentTextFieldValue else pathOrContentTextFieldValue

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { getContentTextFieldValue().text.buildLineOffsets() }
                        items(diagnostics.size) { index ->
                            val diagnostic = diagnostics[index]
                            var cardModifier = Modifier.fillMaxWidth()
                            val textSpan = diagnostic.textSpan
                            if (textSpan != null) {
                                cardModifier = cardModifier.then(Modifier.clickable(onClick = {
                                    val start = textSpan.start
                                    val end = textSpan.end
                                    val textFieldValue = getContentTextFieldValue()
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
                                    if (loadResult?.inputType is InputType.InputTypeWithPath) {
                                        contentTextFieldValue = newTextFieldValue
                                    } else {
                                        pathOrContentTextFieldValue = newTextFieldValue
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
                    onClick = {
                        loadResult?.let {
                            if (it.games.isNotEmpty()) {
                                onConfirmation(
                                    it.games,
                                    OpenGameSettings(
                                        (it.inputType as? InputType.InputTypeWithPath)?.refinedPath,
                                        it.content ?: "",
                                        rewindToEnd,
                                        addFinishingMove,
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp),
                    enabled = loadResult?.games?.isNotEmpty() == true
                ) {
                    Text("Open")
                }
            }
        }
    }
}

data class OpenGameSettings(
    val path: String?,
    val content: String?,
    val rewindToEnd: Boolean,
    val addFinishingMove: Boolean,
) {
    companion object {
        val Default = OpenGameSettings(path = null, content = null, rewindToEnd = true, addFinishingMove = false)
    }
}
