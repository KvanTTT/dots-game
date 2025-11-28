package org.dots.game

import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_ai_settings
import dotsgame.composeapp.generated.resources.ic_load_game
import dotsgame.composeapp.generated.resources.ic_new_game
import dotsgame.composeapp.generated.resources.ic_reset
import dotsgame.composeapp.generated.resources.ic_save_game
import dotsgame.composeapp.generated.resources.ic_settings
import org.dots.game.localization.Strings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

context(strings: Strings)
@Composable
fun IconButton(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val text = textForIcon(icon, strings)
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = text,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun textForIcon(icon: DrawableResource, strings: Strings): String {
    return when (icon) {
        Res.drawable.ic_new_game -> strings.new
        Res.drawable.ic_reset -> strings.reset
        Res.drawable.ic_load_game -> strings.load
        Res.drawable.ic_save_game -> strings.save
        Res.drawable.ic_settings -> strings.settings
        Res.drawable.ic_ai_settings -> strings.aiSettings
        else -> error("Unbound icon $icon")
    }
}