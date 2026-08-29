package org.dots.game

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_ai_move
import dotsgame.composeapp.generated.resources.ic_ai_settings
import dotsgame.composeapp.generated.resources.ic_browse
import dotsgame.composeapp.generated.resources.ic_candidate_moves
import dotsgame.composeapp.generated.resources.ic_copy
import dotsgame.composeapp.generated.resources.ic_ground
import dotsgame.composeapp.generated.resources.ic_load_game
import dotsgame.composeapp.generated.resources.ic_new_game
import dotsgame.composeapp.generated.resources.ic_next
import dotsgame.composeapp.generated.resources.ic_ownership
import dotsgame.composeapp.generated.resources.ic_previous
import dotsgame.composeapp.generated.resources.ic_reset
import dotsgame.composeapp.generated.resources.ic_resign
import dotsgame.composeapp.generated.resources.ic_save
import dotsgame.composeapp.generated.resources.ic_save_as
import dotsgame.composeapp.generated.resources.ic_settings
import org.dots.game.localization.Strings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

val defaultButtonModifier = Modifier.padding(start = 3.dp, end = 3.dp)
val defaultIconModifier = Modifier.size(20.dp)
val selectedModeButtonColor = Color.Magenta

context(strings: Strings)
@Composable
fun IconButton(
    icon: DrawableResource,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val text = textForIcon(icon, strings)
    Tooltip(text) {
        Button(
            onClick = onClick,
            modifier = defaultButtonModifier,
            enabled = enabled,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = text,
                modifier = defaultIconModifier
            )
        }
    }
}

/**
 * An [IconButton] of an option that's switched on and off, highlighted with [selectedModeButtonColor] while it's on.
 *
 * @param description an explanation of the option, shown in the tooltip under the name of the option,
 * because its icon alone conveys neither.
 */
context(strings: Strings)
@Composable
fun ToggleIconButton(
    icon: DrawableResource,
    checked: Boolean,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val text = textForIcon(icon, strings)
    Tooltip(if (description != null) "$text\n$description" else text) {
        Button(
            onClick = onClick,
            modifier = defaultButtonModifier.semantics {
                selected = checked
                role = Role.Switch
            },
            enabled = enabled,
            colors = if (checked)
                ButtonDefaults.buttonColors(selectedModeButtonColor)
            else
                ButtonDefaults.buttonColors(),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = text,
                modifier = defaultIconModifier
            )
        }
    }
}

/**
 * A [Button] that reports a long press in addition to a regular click.
 *
 * A `combinedClickable` modifier is of no use here, because the click handling of [Button] itself shadows it,
 * so the press is timed on the [MutableInteractionSource] the button reports its presses to.
 */
@Composable
fun LongPressButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    checked: Boolean = false,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    // The effect below outlives the passed lambda, because it's restarted by nothing but the interaction source
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    // Set while the press is still held, so that the click of the very same press doesn't act on top of it
    var longPressHandled by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction !is PressInteraction.Press) return@collectLatest
            longPressHandled = false
            // The release (or the cancellation) of the press cancels the waiting, because it stops the collection
            delay(longPressTimeout)
            longPressHandled = true
            currentOnLongClick()
        }
    }

    Button(
        onClick = { if (longPressHandled) longPressHandled = false else onClick() },
        modifier = defaultButtonModifier.semantics {
            selected = checked
            role = Role.Switch
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        content = content,
    )
}

@Composable
fun textForIcon(icon: DrawableResource, strings: Strings): String {
    return when (icon) {
        Res.drawable.ic_new_game -> strings.new
        Res.drawable.ic_reset -> strings.reset
        Res.drawable.ic_load_game -> strings.load
        Res.drawable.ic_save -> strings.save
        Res.drawable.ic_save_as -> strings.saveAs
        Res.drawable.ic_settings -> strings.settings
        Res.drawable.ic_ai_settings -> strings.aiSettings
        Res.drawable.ic_ground -> strings.ground
        Res.drawable.ic_resign -> strings.resign
        Res.drawable.ic_next -> strings.nextGame
        Res.drawable.ic_previous -> strings.previousGame
        Res.drawable.ic_ai_move -> strings.aiMove
        Res.drawable.ic_candidate_moves -> strings.candidateMoves
        Res.drawable.ic_ownership -> strings.ownership
        Res.drawable.ic_browse -> strings.browse
        Res.drawable.ic_copy -> strings.copy
        else -> error("Unbound icon $icon")
    }
}