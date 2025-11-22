package org.dots.game.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.UiSettings
import org.dots.game.core.DoubleRange
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

    fun calculateKomiRange(): DoubleRange {
        return initPosType.selected.calculateAcceptableKomiRange(width, height, considerDraws = true)
    }

    fun Double.doubleKomiToInt(): Int = (this * 2.0).toInt()
    fun Int.intKomiToDouble(): Double = this * 0.5

    var komiRange by remember { mutableStateOf(calculateKomiRange()) }

    var integerKomi by remember { mutableStateOf(
            // Only negative Komi makes sense for a single initial pos
            // Otherise, it allows instant grounding move that makes the game senseless because the first player always wins
        (when {
                uiSettings.developerMode -> rules.komi
                rules.initPosType == InitPosType.Single -> {
                    if (rules.komi >= 0.0) 0.0 else -0.5
                }
                else -> {
                    if (rules.komi > 0.0) +0.5 else 0.0
                }
            }).doubleKomiToInt()
        )
    }
    var drawIsAllowed by remember { mutableStateOf(integerKomi == 0) }

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
                    komiRange = calculateKomiRange()
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
                        DiscreteSliderConfig(strings.komi, integerKomi,
                            komiRange.start.doubleKomiToInt(), komiRange.endInclusive.doubleKomiToInt(),
                            valueRenderer = { it.intKomiToDouble().toString() }
                        ) {
                            integerKomi = it
                        }
                    } else {
                        Text(strings.drawIsAllowed, Modifier.fillMaxWidth(configKeyTextFraction))
                        Checkbox(drawIsAllowed, onCheckedChange = { drawIsAllowed = it })
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
                                if (uiSettings.developerMode) {
                                    integerKomi.intKomiToDouble()
                                } else {
                                    when {
                                        drawIsAllowed -> 0.0
                                        initPosType.selected == InitPosType.Single -> -0.5
                                        else -> 0.5
                                    }
                                }
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

