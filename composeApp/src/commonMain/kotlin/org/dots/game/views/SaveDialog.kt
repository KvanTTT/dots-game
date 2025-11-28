package org.dots.game.views

import org.dots.game.dump.DumpParameters
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.SaveFileDialog
import org.dots.game.UiSettings
import org.dots.game.core.Field
import org.dots.game.core.Games
import org.dots.game.dump.render
import org.dots.game.sgf.SgfWriter

@Composable
fun SaveDialog(
    games: Games,
    field: Field,
    path: String?,
    dumpParameters: DumpParameters,
    uiSettings: UiSettings,
    onDismiss: (dumpParameters: DumpParameters, newPath: String?) -> Unit,
) {
    val strings by remember { mutableStateOf(uiSettings.language.getStrings()) }
    var minX = field.realWidth - 1
    var maxX = 0
    var minY = field.realHeight - 1
    var maxY = 0

    if (field.moveSequence.isNotEmpty()) {
        for (move in field.moveSequence) {
            val (x, y) = move.position.toXY(field.realWidth)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    } else {
        minX = field.realWidth / 2
        minY = field.realHeight / 2
        maxX = minX
        maxY = minY
    }

    val maxPadding = maxOf(minX, minY, field.realWidth - 1 - maxX, field.realHeight - 1 - maxY)

    var printNumbers by remember { mutableStateOf(dumpParameters.printNumbers) }
    var padding by remember { mutableStateOf(dumpParameters.padding.coerceAtMost(maxPadding)) }
    var printCoordinates by remember { mutableStateOf(dumpParameters.printCoordinates) }
    var debugInfo by remember { mutableStateOf(dumpParameters.debugInfo) }

    var isSgf by remember { mutableStateOf(dumpParameters.isSgf) }
    var fieldRepresentation by remember { mutableStateOf("") }

    var path by remember { mutableStateOf(path ?: "") }
    var showSaveDialog by remember { mutableStateOf(false) }

    fun createDumpParameters(): DumpParameters {
        return DumpParameters(
            printNumbers = printNumbers,
            padding = padding,
            printCoordinates = printCoordinates,
            debugInfo = debugInfo,
            isSgf = isSgf
        )
    }

    if (showSaveDialog) {
        SaveFileDialog(
            title = strings.saveDialogTitle(isSgf),
            selectedFile = path,
            extension = if (isSgf) "sgf" else "txt",
            onFileSelected = {
                if (it != null) {
                    path = it
                    onDismiss(createDumpParameters(), path)
                }
                showSaveDialog = false
            },
            content = fieldRepresentation,
        )
    }

    fun updateFieldRepresentation() {
        fieldRepresentation = if (isSgf) {
            SgfWriter.write(games)
        } else {
            field.render(DumpParameters(printNumbers, padding, printCoordinates, debugInfo, isSgf))
        }
    }

    updateFieldRepresentation()

    Dialog(onDismissRequest = {
        onDismiss(createDumpParameters(), null)
    }) {
        Card(modifier = Modifier.wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.sgf, Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(isSgf, onCheckedChange = {
                        isSgf = it
                        updateFieldRepresentation()
                    })
                }

                Text(strings.fieldRepresentation)

                TextField(
                    fieldRepresentation,
                    {},
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    maxLines = 20,
                )

                if (!isSgf) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.printNumbers, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(printNumbers, onCheckedChange = {
                            printNumbers = it
                            updateFieldRepresentation()
                        })
                    }

                    DiscreteSliderConfig(strings.padding, padding, 0, maxPadding) {
                        padding = it
                        updateFieldRepresentation()
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.printCoordinates, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(printCoordinates, onCheckedChange = {
                            printCoordinates = it
                            updateFieldRepresentation()
                        })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.debugInfo, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(debugInfo, onCheckedChange = {
                            debugInfo = it
                            updateFieldRepresentation()
                        })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.path, Modifier.fillMaxWidth(0.1f))
                    TextField(path, {
                        path = it
                    },
                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 10.dp),
                        maxLines = 1,
                        singleLine = true,
                    )
                    Button(
                        onClick = { showSaveDialog = true },
                        Modifier.padding(horizontal = 10.dp),
                    ) {
                        Text(strings.save)
                    }
                }
            }
        }
    }
}