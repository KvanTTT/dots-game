package org.dots.game.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.UiSettings
import org.dots.game.core.InitPosType
import org.dots.game.core.Rules
import org.dots.game.core.generateDefaultInitPos

private const val minDimension = 2
private const val maxDimension = 48

@Composable
fun NewGameDialog(
    rules: Rules,
    uiSettings: UiSettings,
    onDismiss: () -> Unit,
    onConfirmation: (newGameRules: Rules) -> Unit,
) {
    var width by remember { mutableStateOf(rules.width.coerceIn(minDimension, maxDimension)) }
    var height by remember { mutableStateOf(rules.height.coerceIn(minDimension, maxDimension)) }
    var captureByBorder by remember { mutableStateOf(rules.captureByBorder) }

    var initPosType by remember { mutableStateOf(EnumMode(selected = rules.initPosType)) }
    var randomSeed by remember { mutableStateOf(rules.randomSeed) }
    var baseMode by remember { mutableStateOf(EnumMode(selected = rules.baseMode)) }
    var suicideAllowed by remember { mutableStateOf(rules.suicideAllowed) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(470.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                IntegerSlider("Width", width, minDimension, maxDimension) {
                    width = it
                }
                IntegerSlider("Height", height, minDimension, maxDimension) {
                    height = it
                }

                Mode(initPosType, ignoredEntries = setOf(InitPosType.Custom)) {
                    initPosType = it
                }
                Mode(baseMode) {
                    baseMode = it
                }

                if (initPosType.selected == InitPosType.QuadrupleCross) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiSettings.developerMode) {
                            IntegerSlider("Random seed", randomSeed, -1, 8) {
                                randomSeed = it
                            }
                        } else {
                            Text("Random start position", Modifier.fillMaxWidth(textFraction))
                            Checkbox(randomSeed >= 0, onCheckedChange = { randomSeed = if (it) 0 else -1 })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Capture by border", Modifier.fillMaxWidth(textFraction))
                    Checkbox(captureByBorder, onCheckedChange = { captureByBorder = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Suicide allowed", Modifier.fillMaxWidth(textFraction))
                    Checkbox(suicideAllowed, onCheckedChange = { suicideAllowed = it })
                }

                Button(
                    onClick = {
                        val initialMoves = initPosType.selected.generateDefaultInitPos(width, height, randomSeed)!!
                        onConfirmation(Rules(width, height, captureByBorder, baseMode.selected, suicideAllowed, randomSeed, initialMoves))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Create new game")
                }
            }
        }
    }
}

