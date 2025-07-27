package org.dots.game.views

import DumpParameters
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.core.Field
import render

@Composable
fun SaveDialog(
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

    var fieldRepresentation by remember { mutableStateOf("") }

    fun updateFieldRepresentation() {
        fieldRepresentation = field.render(DumpParameters(printNumbers, padding, printCoordinates, debugInfo))
    }

    updateFieldRepresentation()

    Dialog(onDismissRequest = {
        onDismiss(DumpParameters(printNumbers, padding, printCoordinates, debugInfo))
    }) {
        Card(modifier = Modifier.wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Field Representation")

                TextField(
                    fieldRepresentation,
                    {},
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    readOnly = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Print numbers", Modifier.fillMaxWidth(textFraction))
                    Checkbox(printNumbers, onCheckedChange = {
                        printNumbers = it
                        updateFieldRepresentation()
                    })
                }

                IntegerSlider("Padding", padding, 0, maxPadding) {
                    padding = it
                    updateFieldRepresentation()
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Print coordinates", Modifier.fillMaxWidth(textFraction))
                    Checkbox(printCoordinates, onCheckedChange = {
                        printCoordinates = it
                        updateFieldRepresentation()
                    })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Debug info", Modifier.fillMaxWidth(textFraction))
                    Checkbox(debugInfo, onCheckedChange = {
                        debugInfo = it
                        updateFieldRepresentation()
                    })
                }
            }
        }
    }
}