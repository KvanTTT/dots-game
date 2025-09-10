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

@Composable
fun IntegerSlider(name: String, currentValue: Int, minValue: Int, maxValue: Int, onValueChange: (Int) -> Unit) {
    val range = maxValue - minValue

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.fillMaxWidth(textFraction))
        Slider(
            value = (currentValue - minValue).toFloat() / range,
            onValueChange = {
                onValueChange(round(it * range + minValue).toInt())
            },
            steps = range - 1,
            modifier = Modifier.width(150.dp)
        )
        Text(currentValue.toString())
    }
}

const val textFraction = 0.4f

data class EnumMode<E: Enum<E>>(val expanded: Boolean, val selected: E) {
    constructor(selected: E) : this(expanded = false, selected = selected)
}

@Composable
inline fun <reified E : Enum<E>> Mode(enumMode: EnumMode<E>, ignoredEntries: Set<E> = emptySet(), crossinline onChange: (newMode: EnumMode<E>) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
        Text("${splitByUppercase(E::class.simpleName!!)} ", Modifier.fillMaxWidth(textFraction))
        Column(Modifier.fillMaxWidth().height(30.dp)
            .border(1.dp, Color.hsv(0f, 0f, 0.4f))
            .clickable(onClick = { onChange(enumMode.copy(expanded = true)) }),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(splitByUppercase(enumMode.selected.toString()), Modifier.align(Alignment.CenterHorizontally))
            DropdownMenu(
                enumMode.expanded,
                onDismissRequest = { onChange(enumMode.copy(expanded = false)) },
            ) {
                enumValues<E>().filterNot { ignoredEntries.contains(it) }.forEach { entry ->
                    DropdownMenuItem(onClick = { onChange(enumMode.copy(expanded = false, selected = entry)) }) {
                        Text(splitByUppercase(entry.toString()))
                    }
                }
            }
        }
    }
}