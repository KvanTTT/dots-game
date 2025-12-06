package org.dots.game.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Card
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.dots.game.BuildInfo
import org.dots.game.UiSettings
import org.dots.game.date
import org.dots.game.version

@Composable
fun UiSettingsForm(
    uiSettings: UiSettings,
    onUiSettingsChange: (UiSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var connectionDrawMode by remember { mutableStateOf(EnumMode(uiSettings.connectionDrawMode)) }
    var baseDrawMode by remember { mutableStateOf(EnumMode(uiSettings.baseDrawMode)) }
    var language by remember { mutableStateOf(EnumMode(uiSettings.language)) }
    var strings by remember { mutableStateOf(uiSettings.language.getStrings())}

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(470.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeConfig(
                        connectionDrawMode,
                        nameRenderer = { strings.connectionDrawMode },
                        valueRenderer = { strings.connectionDrawModeLabel(it) }
                    ) {
                        connectionDrawMode = it
                        onUiSettingsChange(uiSettings.copy(connectionDrawMode = connectionDrawMode.selected))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeConfig(
                        baseDrawMode,
                        nameRenderer = { strings.polygonDrawMode },
                        valueRenderer = { strings.polygonDrawModeLabel(it) }
                    ) {
                        baseDrawMode = it
                        onUiSettingsChange(uiSettings.copy(baseDrawMode = it.selected))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.diagonalConnections, Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.showDiagonalConnections, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(showDiagonalConnections = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.threats, Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.showThreats, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(showThreats = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.surroundings, Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.showSurroundings, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(showSurroundings = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.developerMode, Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.developerMode, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(developerMode = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.experimentalMode, Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.experimentalMode, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(experimentalMode = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeConfig(
                        language,
                        nameRenderer = { strings.language },
                        valueRenderer = { it.displayName }
                    ) {
                        language = it
                        strings = language.selected.getStrings()
                        onUiSettingsChange(uiSettings.copy(language = it.selected))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.version, Modifier.fillMaxWidth(configKeyTextFraction))
                    Text("${BuildInfo.version} (${BuildInfo.date})")
                }
            }
        }
    }
}