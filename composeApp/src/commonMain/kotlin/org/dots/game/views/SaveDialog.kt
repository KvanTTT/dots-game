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
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_save
import org.dots.game.GameSettings
import org.dots.game.IconButton
import org.dots.game.InputType
import org.dots.game.InputTypeDetector
import org.dots.game.SaveFileDialog
import org.dots.game.UiSettings
import org.dots.game.core.Field
import org.dots.game.dump.render
import org.dots.game.thisAppUrl

@Composable
fun SaveDialog(
    field: Field,
    gameSettings: GameSettings,
    dumpParameters: DumpParameters,
    uiSettings: UiSettings,
    onDismiss: (dumpParameters: DumpParameters, newPath: String?) -> Unit,
) {
    val strings by remember { mutableStateOf(uiSettings.language.getStrings()) }
    val sgfContent = gameSettings.sgf
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

    val inputTypeForGameSettings by remember { mutableStateOf(InputTypeDetector.tryGetInputTypeForPath(gameSettings.path ?: "")) }

    val refinedLink by remember {
        val refinedGameSettings = when (val inputType = inputTypeForGameSettings) {
            // Extract refined path for client URL
            is InputType.SgfClientUrl -> gameSettings.copy(path = inputType.refinedPath)
            else -> gameSettings
        }
        mutableStateOf(thisAppUrl + refinedGameSettings.toUrlParams())
    }

    var path by remember {
        val refinedPath = when (val inputType = inputTypeForGameSettings) {
            // Don't use a link as a file name because it's useless
            is InputType.Url -> {
                inputType.name + if (!InputTypeDetector.sgfExtensionRegex.matches(inputType.name) && isSgf) ".sgf" else ""
            }
            is InputType.InputTypeWithPath -> inputType.refinedPath
            else -> ""
        }
        mutableStateOf(refinedPath)
    }

    val link by remember(path) {
        val inputTypeWithPath = InputTypeDetector.tryGetInputTypeForPath(path)
        val newLink = when (inputTypeWithPath) {
            // Don't use a local absolute path for links
            is InputType.SgfFile -> thisAppUrl + gameSettings.copy(path = inputTypeWithPath.name).toUrlParams()
            else -> refinedLink
        }
        mutableStateOf(newLink)
    }

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
        fieldRepresentation = if (isSgf && sgfContent != null) {
            sgfContent
        } else {
            field.render(
                DumpParameters(
                    printNumbers = printNumbers,
                    padding = padding,
                    printCoordinates = printCoordinates,
                    printBorders = false,
                    debugInfo = debugInfo,
                    isSgf = isSgf,
                )
            )
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
                    Text(strings.path, Modifier.fillMaxWidth(0.2f))
                    TextField(path, {
                        path = it
                    },
                    modifier = Modifier.fillMaxWidth(0.8f).padding(end = 5.dp),
                        maxLines = 1,
                        singleLine = true,
                    )
                    with(strings) {
                        IconButton(Res.drawable.ic_save) {
                            showSaveDialog = true
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.link, Modifier.fillMaxWidth(0.2f))
                    TextField(
                        link, { },
                        modifier = Modifier.fillMaxWidth(0.8f).padding(end = 5.dp),
                        readOnly = true,
                        singleLine = true
                    )
                }
            }
        }
    }
}