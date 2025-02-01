package org.dots.game.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.core.InitialPositionType
import org.dots.game.core.Rules
import org.dots.game.core.generateDefaultInitialPositions
import kotlin.math.round

@Composable
fun NewGameDialog(
    onDismiss: () -> Unit,
    onConfirmation: (newGameRules: Rules) -> Unit,
) {
    val minWidth = 3
    val maxWidth = 48
    val minHeight = 3
    val maxHeight = 48
    val standardRules = Rules.Standard

    var width by remember { mutableStateOf(standardRules.width) }
    var height by remember { mutableStateOf(standardRules.height) }
    var captureByBorder by remember { mutableStateOf(standardRules.captureByBorder) }
    var captureEmptyBase by remember { mutableStateOf(standardRules.captureEmptyBase) }

    var initialPositionExpanded by remember { mutableStateOf(false) }
    val initialPositionTypeItems = InitialPositionType.entries
    var initialPositionTypeSelected by remember { mutableStateOf(InitialPositionType.Cross) }

    @Composable
    fun SetupDimension(isWidth: Boolean) {
        val minDimension: Int
        val maxDimension: Int
        if (isWidth) {
            minDimension = minWidth
            maxDimension = maxWidth
        } else {
            minDimension = minHeight
            maxDimension = maxHeight
        }

        val range = maxDimension - minDimension
        fun getDimension() = if (isWidth) width else height

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (isWidth) "Width" else "Height", Modifier.fillMaxWidth(0.5f))
            Slider(
                value = (getDimension() - minDimension).toFloat() / range,
                onValueChange = {
                    val newDimensionValue = round(it * range + minDimension).toInt()
                    if (isWidth)
                        width = newDimensionValue
                    else
                        height = newDimensionValue
                },
                steps = range - 1,
                modifier = Modifier.width(150.dp)
            )
            Text(getDimension().toString())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(400.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                SetupDimension(isWidth = true)
                SetupDimension(isWidth = false)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Initial position: ", Modifier.fillMaxWidth(0.5f))
                    Box(Modifier.fillMaxWidth().background(Color.LightGray).clickable(onClick = { initialPositionExpanded = true })) {
                        Text(initialPositionTypeSelected.toString(), Modifier.align(Alignment.Center))
                        DropdownMenu(
                            initialPositionExpanded,
                            onDismissRequest = { initialPositionExpanded = false },
                        ) {
                            initialPositionTypeItems.forEachIndexed { _, initialPositionType ->
                                DropdownMenuItem(onClick = {
                                    initialPositionTypeSelected = initialPositionType
                                    initialPositionExpanded = false
                                }) {
                                    Text(initialPositionType.toString())
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Capture by border", Modifier.fillMaxWidth(0.5f))
                    Checkbox(captureByBorder, onCheckedChange = { captureByBorder = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Capture empty base", Modifier.fillMaxWidth(0.5f))
                    Checkbox(captureEmptyBase, onCheckedChange = { captureEmptyBase = it })
                }

                TextButton(
                    onClick = {
                        val initialMoves = initialPositionTypeSelected.generateDefaultInitialPositions(width, height)!!
                        onConfirmation(Rules(width, height, captureByBorder, captureEmptyBase, initialMoves))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Create new game")
                }
            }
        }
    }
}