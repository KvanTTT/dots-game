package org.dots.game.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dotsgame.composeapp.generated.resources.*
import org.dots.game.Platform
import org.dots.game.Tooltip
import org.dots.game.UiSettings
import org.dots.game.core.*
import org.dots.game.localization.Strings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ControlButton(
    text: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Tooltip(text) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            Icon(
                painterResource(icon),
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun GameControlButtons(
    strings: Strings,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    onNewGame: () -> Unit,
    onReset: () -> Unit,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    onAiSettings: () -> Unit,
    showAiSettings: Boolean
) {
    Row(modifier) {
        ControlButton(strings.new, Res.drawable.ic_new_game, onNewGame, buttonModifier)
        ControlButton(strings.reset, Res.drawable.ic_refresh, onReset, buttonModifier)
        ControlButton(strings.load, Res.drawable.ic_folder_open, onLoad, buttonModifier)
        ControlButton(strings.save, Res.drawable.ic_save, onSave, buttonModifier)
        ControlButton(strings.settings, Res.drawable.ic_settings, onSettings, buttonModifier)
        if (showAiSettings) {
            ControlButton(strings.aiSettings, Res.drawable.ic_smart_toy, onAiSettings, buttonModifier)
        }
    }
}

@Composable
fun MoveControlButtons(
    moveMode: MoveMode,
    uiSettings: UiSettings,
    strings: Strings,
    isGameOver: Boolean,
    engineIsCalculating: Boolean,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    onMoveModeChange: (MoveMode) -> Unit,
    onEndMove: (ExternalFinishReason) -> Unit
) {
    val selectedModeButtonColor = Color.Magenta
    val playerColorIconModifier = Modifier.size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape)

    Row(modifier) {
        Tooltip(strings.nextMove) {
            Button(
                onClick = { onMoveModeChange(MoveMode.Next) },
                modifier = buttonModifier,
                colors = if (moveMode == MoveMode.Next) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
            ) {
                Box {
                    Box(
                        modifier = Modifier.offset((-5).dp).size(16.dp)
                            .border(1.dp, Color.White, CircleShape).clip(CircleShape)
                            .background(uiSettings.playerFirstColor)
                    )
                    Box(
                        modifier = Modifier.offset(5.dp).size(16.dp).border(1.dp, Color.White, CircleShape)
                            .clip(CircleShape).background(uiSettings.playerSecondColor)
                    )
                }
            }
        }
        Tooltip(strings.player1) {
            Button(
                onClick = { onMoveModeChange(MoveMode.First) },
                modifier = buttonModifier,
                colors = if (moveMode == MoveMode.First) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
            ) {
                Box(
                    modifier = playerColorIconModifier.background(uiSettings.playerFirstColor)
                )
            }
        }
        Tooltip(strings.player2) {
            Button(
                onClick = { onMoveModeChange(MoveMode.Second) },
                modifier = buttonModifier,
                colors = if (moveMode == MoveMode.Second) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
            ) {
                Box(
                    modifier = playerColorIconModifier.background(uiSettings.playerSecondColor)
                )
            }
        }

        ControlButton(
            strings.grounding,
            Res.drawable.ic_grounding,
            { onEndMove(ExternalFinishReason.Grounding) },
            buttonModifier,
            enabled = !isGameOver && !engineIsCalculating
        )
        ControlButton(
            strings.resign,
            Res.drawable.ic_resign,
            { onEndMove(ExternalFinishReason.Resign) },
            buttonModifier,
            enabled = !isGameOver && !engineIsCalculating
        )
    }
}

@Composable
fun GameNavigationButtons(
    gamesCount: Int,
    strings: Strings,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    engineIsCalculating: Boolean,
    onSwitchGame: (Boolean) -> Unit
) {
    if (gamesCount > 1) {
        Row(modifier) {
            ControlButton(
                strings.previousGame,
                Res.drawable.ic_skip_previous,
                { onSwitchGame(false) },
                buttonModifier,
                enabled = !engineIsCalculating
            )
            ControlButton(
                strings.nextGame,
                Res.drawable.ic_skip_next,
                { onSwitchGame(true) },
                buttonModifier,
                enabled = !engineIsCalculating
            )
        }
    }
}

@Composable
fun AiControlButtons(
    isEngineAvailable: Boolean,
    isEngineCalculating: Boolean,
    isGameOver: Boolean,
    isRulesSupported: Boolean,
    autoMove: Boolean,
    strings: Strings,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    onMakeAiMove: () -> Unit,
    onAutoMoveChange: (Boolean) -> Unit
) {
    if (isEngineAvailable) {
        Row(modifier) {
            val text = if (isEngineCalculating) strings.thinking else strings.aiMove
            ControlButton(
                text,
                Res.drawable.ic_memory,
                onMakeAiMove,
                buttonModifier,
                enabled = !isGameOver && !isEngineCalculating && isRulesSupported
            )
            Text("Auto", Modifier.align(Alignment.CenterVertically))
            Checkbox(autoMove, onCheckedChange = onAutoMoveChange)
        }
    }
}
