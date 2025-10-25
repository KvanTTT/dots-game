package org.dots.game.views

import DumpParameters
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.core.Field
import org.dots.game.core.Games
import org.dots.game.sgf.SgfWriter
import render

@Composable
fun SaveDialog(
    games: Games,
    field: Field,
    dumpParameters: DumpParameters,
    onDismiss: (DumpParameters) -> Unit,
) {
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

    fun updateFieldRepresentation() {
        fieldRepresentation = if (isSgf) {
            SgfWriter.write(games)
        } else {
            field.render(DumpParameters(printNumbers, padding, printCoordinates, debugInfo, isSgf))
        }
    }

    updateFieldRepresentation()

    Dialog(onDismissRequest = {
        onDismiss(DumpParameters(
            printNumbers = printNumbers,
            padding = padding,
            printCoordinates = printCoordinates,
            debugInfo = debugInfo,
            isSgf = isSgf
        ))
    }) {
        Card(modifier = Modifier.wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SGF", Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(isSgf, onCheckedChange = {
                        isSgf = it
                        updateFieldRepresentation()
                    })
                }

                Text("Field Representation")

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
                        Text("Print numbers", Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(printNumbers, onCheckedChange = {
                            printNumbers = it
                            updateFieldRepresentation()
                        })
                    }

                    DiscreteSliderConfig("Padding", padding, 0, maxPadding) {
                        padding = it
                        updateFieldRepresentation()
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Print coordinates", Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(printCoordinates, onCheckedChange = {
                            printCoordinates = it
                            updateFieldRepresentation()
                        })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Debug info", Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(debugInfo, onCheckedChange = {
                            debugInfo = it
                            updateFieldRepresentation()
                        })
                    }
                }
            }
        }
    }
}