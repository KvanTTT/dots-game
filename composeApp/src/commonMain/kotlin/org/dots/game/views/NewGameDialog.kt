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
import org.dots.game.maxFieldDimension
import org.dots.game.minFieldDimension
import kotlin.random.Random

@Composable
fun NewGameDialog(
    rules: Rules,
    uiSettings: UiSettings,
    onDismiss: () -> Unit,
    onConfirmation: (newGameRules: Rules) -> Unit,
) {
    val strings by remember { mutableStateOf(uiSettings.language.getStrings()) }
    var width by remember { mutableStateOf(rules.width.coerceIn(minFieldDimension, maxFieldDimension)) }
    var height by remember { mutableStateOf(rules.height.coerceIn(minFieldDimension, maxFieldDimension)) }
    var captureByBorder by remember { mutableStateOf(rules.captureByBorder) }

    var initPosType by remember { mutableStateOf(EnumMode(selected = rules.initPosType)) }
    var initPosIsRandom by remember { mutableStateOf(rules.initPosIsRandom) }
    var baseMode by remember { mutableStateOf(EnumMode(selected = rules.baseMode)) }
    var suicideAllowed by remember { mutableStateOf(rules.suicideAllowed) }
    val normalizedKomi = when {
        uiSettings.developerMode -> rules.komi
        rules.komi <= 0.0 -> 0.0
        else -> 0.5
    }
    var integerKomi by remember { mutableStateOf((normalizedKomi * 2).toInt()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(470.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                DiscreteSliderConfig(strings.width, width, minFieldDimension, maxFieldDimension) {
                    width = it
                }
                DiscreteSliderConfig(strings.height, height, minFieldDimension, maxFieldDimension) {
                    height = it
                }

                ModeConfig(
                    initPosType,
                    ignoredEntries = setOf(InitPosType.Custom),
                    nameRenderer = { strings.initPosType },
                    valueRenderer = { strings.initPosTypeLabel(it) }
                ) {
                    initPosType = it
                }
                ModeConfig(
                    baseMode,
                    nameRenderer = { strings.baseMode },
                    valueRenderer = { strings.baseModeLabel(it) }
                ) {
                    baseMode = it
                }

                if (initPosType.selected == InitPosType.QuadrupleCross) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.randomStartPosition, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(initPosIsRandom, onCheckedChange = { initPosIsRandom = it })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.captureByBorder, Modifier.fillMaxWidth(configKeyTextFraction))
                    Checkbox(captureByBorder, onCheckedChange = { captureByBorder = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.suicideAllowed, Modifier.fillMaxWidth(configKeyTextFraction))
                    Checkbox(suicideAllowed, onCheckedChange = { suicideAllowed = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiSettings.developerMode) {
                        DiscreteSliderConfig(strings.komi, integerKomi, -5, 5,
                            valueRenderer = { (it.toDouble() / 2.0).toString() }
                        ) {
                            integerKomi = it
                        }
                    } else {
                        Text(strings.roundDraw, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(integerKomi == 0, onCheckedChange = { integerKomi = if (it) 0 else 1  })
                    }
                }

                Button(
                    onClick = {
                        onConfirmation(
                            Rules.create(
                                width,
                                height,
                                captureByBorder,
                                baseMode.selected,
                                suicideAllowed,
                                initPosType.selected,
                                Random.takeIf { initPosIsRandom },
                                (integerKomi.toDouble() / 2.0)
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(strings.createNewGame)
                }
            }
        }
    }
}

