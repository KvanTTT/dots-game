package org.dots.game.views

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.dots.game.splitByUppercase
import kotlin.math.round

const val configKeyTextFraction = 0.4f

@Composable
fun DiscreteSliderConfig(
    name: String,
    currentValue: Int,
    minValue: Int,
    maxValue: Int,
    step: Int = 1,
    valueRenderer: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit
) {
    val range = (maxValue - minValue) / step

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.fillMaxWidth(configKeyTextFraction))
        Slider(
            value = (currentValue.toFloat() - minValue) / step / range,
            onValueChange = {
                onValueChange(round(it * range).toInt() * step + minValue)
            },
            steps = range - 1,
            modifier = Modifier.width(200.dp)
        )
        Text(valueRenderer(currentValue))
    }
}

data class EnumMode<E: Enum<E>>(val expanded: Boolean, val selected: E) {
    constructor(selected: E) : this(expanded = false, selected = selected)
}

@Composable
inline fun <reified E : Enum<E>> ModeConfig(
    enumMode: EnumMode<E>,
    ignoredEntries: Set<E> = emptySet(),
    noinline typeLabelProvider: (() -> String)? = null,
    noinline labelProvider: ((E) -> String)? = null,
    crossinline onChange: (newMode: EnumMode<E>) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
        val typeLabel = typeLabelProvider?.invoke() ?: splitByUppercase(E::class.simpleName!!)
        Text("$typeLabel ", Modifier.fillMaxWidth(configKeyTextFraction))
        Column(Modifier.fillMaxWidth().height(30.dp)
            .border(1.dp, Color.hsv(0f, 0f, 0.4f))
            .clickable(onClick = { onChange(enumMode.copy(expanded = true)) }),
            verticalArrangement = Arrangement.Center,
        ) {
            val selectedLabel = labelProvider?.invoke(enumMode.selected) ?: splitByUppercase(enumMode.selected.toString())
            Text(selectedLabel, Modifier.align(Alignment.CenterHorizontally))
            DropdownMenu(
                enumMode.expanded,
                onDismissRequest = { onChange(enumMode.copy(expanded = false)) },
            ) {
                enumValues<E>().filterNot { ignoredEntries.contains(it) }.forEach { entry ->
                    val entryLabel = labelProvider?.invoke(entry) ?: splitByUppercase(entry.toString())
                    DropdownMenuItem(onClick = { onChange(enumMode.copy(expanded = false, selected = entry)) }) {
                        Text(entryLabel)
                    }
                }
            }
        }
    }
}