package org.dots.game

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_ai_move
import dotsgame.composeapp.generated.resources.ic_ai_settings
import dotsgame.composeapp.generated.resources.ic_browse
import dotsgame.composeapp.generated.resources.ic_copy
import dotsgame.composeapp.generated.resources.ic_ground
import dotsgame.composeapp.generated.resources.ic_load_game
import dotsgame.composeapp.generated.resources.ic_new_game
import dotsgame.composeapp.generated.resources.ic_next
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
        Res.drawable.ic_browse -> strings.browse
        Res.drawable.ic_copy -> strings.copy
        else -> error("Unbound icon $icon")
    }
}