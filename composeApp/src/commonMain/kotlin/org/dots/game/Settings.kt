package org.dots.game

import DumpParameters
import androidx.compose.ui.graphics.Color
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

private const val firstLevelSeparator = ","
private const val secondLevelSeparator = ";"

fun loadRules(): Rules {
    val settings = appSettings ?: return Rules.Standard
    val ruleClass = Rules::class // TODO: inline after KT-80853
    context (settings, ruleClass) {
        return Rules(
            get(Rules::width, Rules.Standard.width),
            get(Rules::height, Rules.Standard.height),
            captureByBorder = get(Rules::captureByBorder, Rules.Standard.captureByBorder),
            baseMode = getEnumValue(Rules::baseMode, Rules.Standard.baseMode),
            suicideAllowed = get(Rules::suicideAllowed, Rules.Standard.suicideAllowed),
            initialMoves = get(Rules::initialMoves, "").let { initialMovesData ->
                try {
                    buildList {
                        if (initialMovesData.isNotEmpty()) {
                            initialMovesData.split(secondLevelSeparator).forEach { moveInfo ->
                                val parts = moveInfo.split(firstLevelSeparator)
                                add(MoveInfo(PositionXY(parts[0].toInt(), parts[1].toInt()), Player.validateAndCreate(parts[2].toInt())))
                            }
                        }
                    }
                }
                catch (e: Exception) {
                    println("Error while reading ${Rules::class.getSettingName(Rules::initialMoves)} `${initialMovesData}` (${e.message})")
                    null
                }
            } ?: listOf()
        )
    }
}

fun saveRules(rules: Rules) {
    val settings = appSettings ?: return
    val ruleClass = Rules::class // TODO: inline after KT-80853
    context(settings, ruleClass) {
        set(Rules::width, rules.width)
        set(Rules::height, rules.height)
        set(Rules::captureByBorder, rules.captureByBorder)
        setEnumValue(Rules::baseMode, rules.baseMode)
        set(Rules::suicideAllowed, rules.suicideAllowed)
        set(
            Rules::initialMoves,
            rules.initialMoves.joinToString(secondLevelSeparator) {
                val positionXY = it.positionXY
                if (positionXY != null) {
                    "${positionXY.x}$firstLevelSeparator${positionXY.y}$firstLevelSeparator${it.player.value}"
                } else {
                    ""
                }
            })
    }
}

fun loadDumpParameters(): DumpParameters {
    val settings = appSettings ?: return DumpParameters()
    val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
    context (settings, dumpParametersClass) {
        return DumpParameters(
            printNumbers = get(DumpParameters::printNumbers, DumpParameters.DEFAULT.printNumbers),
            padding = get(DumpParameters::padding, DumpParameters.DEFAULT.padding),
            printCoordinates = get(DumpParameters::printCoordinates, DumpParameters.DEFAULT.printCoordinates),
            debugInfo = get(DumpParameters::debugInfo, DumpParameters.DEFAULT.debugInfo)
        )
    }
}

fun saveDumpParameters(dumpParameters: DumpParameters) {
    val settings = appSettings ?: return
    val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
    context (settings, dumpParametersClass) {
        set(DumpParameters::printNumbers, dumpParameters.printNumbers)
        set(DumpParameters::padding, dumpParameters.padding)
        set(DumpParameters::printCoordinates, dumpParameters.printCoordinates)
        set(DumpParameters::debugInfo, dumpParameters.debugInfo)
    }
}

fun loadUiSettings(): UiSettings {
    val settings = appSettings ?: return UiSettings.Standard
    val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
    context (settings, uiSettingsClass) {
        return UiSettings(
            playerFirstColor = get(UiSettings::playerFirstColor, UiSettings.Standard.playerFirstColor),
            playerSecondColor = get(UiSettings::playerSecondColor, UiSettings.Standard.playerSecondColor),
            connectionDrawMode = getEnumValue(UiSettings::connectionDrawMode, UiSettings.Standard.connectionDrawMode),
            baseDrawMode = getEnumValue(UiSettings::baseDrawMode, UiSettings.Standard.baseDrawMode),
            showDiagonalConnections = get(UiSettings::showDiagonalConnections, UiSettings.Standard.showDiagonalConnections),
            showThreats = get(UiSettings::showThreats, UiSettings.Standard.showThreats),
            showSurroundings = get(UiSettings::showSurroundings, UiSettings.Standard.showSurroundings)
        )
    }
}


fun saveUiSettings(uiSettings: UiSettings) {
    val settings = appSettings ?: return
    val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
    context (settings, uiSettingsClass) {
        set(UiSettings::playerFirstColor, uiSettings.playerFirstColor)
        set(UiSettings::playerSecondColor, uiSettings.playerSecondColor)
        setEnumValue(UiSettings::connectionDrawMode, uiSettings.connectionDrawMode)
        setEnumValue(UiSettings::baseDrawMode, uiSettings.baseDrawMode)
        set(UiSettings::showDiagonalConnections, uiSettings.showDiagonalConnections)
        set(UiSettings::showThreats, uiSettings.showThreats)
        set(UiSettings::showSurroundings, uiSettings.showSurroundings)
    }
}

context(settings: Settings, klass: KClass<*>)
private inline fun <reified E : Enum<E>> getEnumValue(property: KProperty<*>, default: E): E {
    return settings.getStringOrNull(klass.getSettingName(property))?.let { enumValueOf<E>(it)} ?: default
}

context(settings: Settings, klass: KClass<*>)
private inline fun <reified E : Enum<E>> setEnumValue(property: KProperty<*>, value: E) {
    settings.putString(klass.getSettingName(property), value.name)
}

context(settings: Settings, klass: KClass<*>)
private inline fun <reified T> get(property: KProperty<*>, default: T): T {
    val settingName = klass.getSettingName(property)
    return (when {
        T::class == Color::class -> {
            settings.getLongOrNull(settingName)?.let { Color(it.toULong()) } as? T
        }
        else -> {
            settings[settingName]
        }
    }) ?: default
}

context(settings: Settings, klass: KClass<*>)
private inline fun <reified T> set(property: KProperty<*>, value: T) {
    val settingName = klass.getSettingName(property)
    when {
        T::class == Color::class -> settings.putLong(settingName, (value as Color).value.toLong())
        else -> settings[settingName] = value
    }
}

private fun KClass<*>.getSettingName(property: KProperty<*>): String {
    return "${this.simpleName!!}.${property.name}"
}