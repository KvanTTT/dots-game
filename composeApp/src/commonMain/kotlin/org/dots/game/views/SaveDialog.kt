package org.dots.game.views

import org.dots.game.dump.DumpParameters
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.dots.game.Clipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_copy
import dotsgame.composeapp.generated.resources.ic_save
import org.dots.game.GameSettings
import org.dots.game.IconButton
import org.dots.game.InputType
import org.dots.game.InputTypeDetector
import org.dots.game.SaveFileDialog
import org.dots.game.UiSettings
import org.dots.game.core.Field
import org.dots.game.dateTimeShort
import org.dots.game.dump.render
import org.dots.game.getGameLink
import org.dots.game.platform
import org.dots.game.Platform
import kotlin.time.Clock

@Composable
fun SaveDialog(
    field: Field,
    gameSettings: GameSettings,
    dumpParameters: DumpParameters,
    uiSettings: UiSettings,
    onDismiss: (dumpParameters: DumpParameters, newPath: String?) -> Unit,
) {
    val strings = remember { uiSettings.language.getStrings() }
    val sgfContent = remember { gameSettings.sgf!! }

    val maxPadding = remember {
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

        maxOf(minX, minY, field.realWidth - 1 - maxX, field.realHeight - 1 - maxY)
    }

    var printNumbers by remember { mutableStateOf(dumpParameters.printNumbers) }
    var padding by remember { mutableStateOf(dumpParameters.padding.coerceAtMost(maxPadding)) }
    var printCoordinates by remember { mutableStateOf(dumpParameters.printCoordinates) }
    var debugInfo by remember { mutableStateOf(dumpParameters.debugInfo) }

    var isSgf by remember { mutableStateOf(dumpParameters.isSgf) }
    var fieldRepresentation by remember(isSgf, printNumbers, padding, printCoordinates, debugInfo) {
        mutableStateOf(if (isSgf) {
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
        })
    }

    val inputTypeForGameSettings = remember { InputTypeDetector.tryGetInputTypeForPath(gameSettings.path ?: "") }

    val refinedLink = remember {
        val refinedGameSettings = when (val inputType = inputTypeForGameSettings) {
            // Extract refined path for client URL
            is InputType.SgfClientUrl -> gameSettings.copy(path = inputType.refinedPath)
            else -> gameSettings
        }
        getGameLink(refinedGameSettings)
    }

    var path by remember {
        val refinedPath = when (val inputType = inputTypeForGameSettings) {
            // Don't use a link as a file name because it's useless
            is InputType.Url -> inputType.name
            is InputType.File -> inputType.refinedPath
            null -> ""
        }.let {
            val resultPath = it.ifBlank { Clock.System.now().dateTimeShort.replace(':', '-') }
            resultPath + if (!InputTypeDetector.sgfExtensionRegex.matches(resultPath)) ".sgf" else ""
        }
        mutableStateOf(refinedPath)
    }

    val link = remember(path) {
        val inputTypeWithPath = InputTypeDetector.tryGetInputTypeForPath(path)
        val newLink = when (inputTypeWithPath) {
            // Don't use a local absolute path for links
            is InputType.SgfFile -> getGameLink(gameSettings.copy(path = inputTypeWithPath.name))
            else -> refinedLink
        }
        newLink
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
            title = strings.saveDialogTitle,
            selectedFile = path,
            extension = "sgf",
            onFileSelected = {
                if (it != null) {
                    path = it
                    if (platform !is Platform.Web) {
                        onDismiss(createDumpParameters(), path)
                    }
                }
                showSaveDialog = false
            },
            content = sgfContent,
        )
    }

    Dialog(onDismissRequest = {
        onDismiss(createDumpParameters(), null)
    }) {
        Card(modifier = Modifier.wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (uiSettings.developerMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.sgf, Modifier.fillMaxWidth(configKeyTextFraction))
                        Switch(isSgf, onCheckedChange = {
                            isSgf = it
                        })
                    }
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
                        })
                    }

                    DiscreteSliderConfig(strings.padding, padding, 0, maxPadding) {
                        padding = it
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.printCoordinates, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(printCoordinates, onCheckedChange = {
                            printCoordinates = it
                        })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.debugInfo, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(debugInfo, onCheckedChange = {
                            debugInfo = it
                        })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
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

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(strings.link, Modifier.fillMaxWidth(0.2f))
                    TextField(
                        link, { },
                        modifier = Modifier.fillMaxWidth(0.8f).padding(end = 5.dp),
                        readOnly = true,
                        singleLine = true
                    )
                    with(strings) {
                        IconButton(Res.drawable.ic_copy) {
                            Clipboard.copyTo(link)
                        }
                    }
                }
            }
        }
    }
}