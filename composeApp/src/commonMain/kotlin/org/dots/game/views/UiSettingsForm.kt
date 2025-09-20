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
import org.dots.game.UiSettings

@Composable
fun UiSettingsForm(
    uiSettings: UiSettings,
    onUiSettingsChange: (UiSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var connectionDrawMode by remember { mutableStateOf(EnumMode(uiSettings.connectionDrawMode)) }
    var baseDrawMode by remember { mutableStateOf(EnumMode(uiSettings.baseDrawMode)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(470.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeConfig(connectionDrawMode) {
                        connectionDrawMode = it
                        onUiSettingsChange(uiSettings.copy(connectionDrawMode = connectionDrawMode.selected))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeConfig(baseDrawMode) {
                        baseDrawMode = it
                        onUiSettingsChange(uiSettings.copy(baseDrawMode = it.selected))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagonal Connections", Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.showDiagonalConnections, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(showDiagonalConnections = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Threats", Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.showThreats, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(showThreats = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Surroundings", Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.showSurroundings, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(showSurroundings = it))
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Developer Mode", Modifier.fillMaxWidth(configKeyTextFraction))
                    Switch(uiSettings.developerMode, onCheckedChange = {
                        onUiSettingsChange(uiSettings.copy(developerMode = it))
                    })
                }
            }
        }
    }
}