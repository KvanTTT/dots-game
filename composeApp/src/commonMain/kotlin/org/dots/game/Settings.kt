package org.dots.game

import DumpParameters
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules

private const val firstLevelSeparator = ","
private const val secondLevelSeparator = ";"

fun loadRules(): Rules {
    val settings = appSettings ?: return Rules.Standard
    val ruleClass = Rules::class // TODO: inline after KT-80853
    context (settings, ruleClass) {
        return Rules(
            getSetting(Rules::width, Rules.Standard.width),
            getSetting(Rules::height, Rules.Standard.height),
            captureByBorder = getSetting(Rules::captureByBorder, Rules.Standard.captureByBorder),
            baseMode = getEnumSetting(Rules::baseMode, Rules.Standard.baseMode),
            suicideAllowed = getSetting(Rules::suicideAllowed, Rules.Standard.suicideAllowed),
            initialMoves = getSetting(Rules::initialMoves, "").let { initialMovesData ->
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
        setSetting(Rules::width, rules.width)
        setSetting(Rules::height, rules.height)
        setSetting(Rules::captureByBorder, rules.captureByBorder)
        setEnumSetting(Rules::baseMode, rules.baseMode)
        setSetting(Rules::suicideAllowed, rules.suicideAllowed)
        setSetting(
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
            printNumbers = getSetting(DumpParameters::printNumbers, DumpParameters.DEFAULT.printNumbers),
            padding = getSetting(DumpParameters::padding, DumpParameters.DEFAULT.padding),
            printCoordinates = getSetting(DumpParameters::printCoordinates, DumpParameters.DEFAULT.printCoordinates),
            debugInfo = getSetting(DumpParameters::debugInfo, DumpParameters.DEFAULT.debugInfo)
        )
    }
}

fun saveDumpParameters(dumpParameters: DumpParameters) {
    val settings = appSettings ?: return
    val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
    context (settings, dumpParametersClass) {
        setSetting(DumpParameters::printNumbers, dumpParameters.printNumbers)
        setSetting(DumpParameters::padding, dumpParameters.padding)
        setSetting(DumpParameters::printCoordinates, dumpParameters.printCoordinates)
        setSetting(DumpParameters::debugInfo, dumpParameters.debugInfo)
    }
}

fun loadUiSettings(): UiSettings {
    val settings = appSettings ?: return UiSettings.Standard
    val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
    context (settings, uiSettingsClass) {
        return UiSettings(
            playerFirstColor = getSetting(UiSettings::playerFirstColor, UiSettings.Standard.playerFirstColor),
            playerSecondColor = getSetting(UiSettings::playerSecondColor, UiSettings.Standard.playerSecondColor),
            connectionDrawMode = getEnumSetting(UiSettings::connectionDrawMode, UiSettings.Standard.connectionDrawMode),
            baseDrawMode = getEnumSetting(UiSettings::baseDrawMode, UiSettings.Standard.baseDrawMode),
            showDiagonalConnections = getSetting(UiSettings::showDiagonalConnections, UiSettings.Standard.showDiagonalConnections),
            showThreats = getSetting(UiSettings::showThreats, UiSettings.Standard.showThreats),
            showSurroundings = getSetting(UiSettings::showSurroundings, UiSettings.Standard.showSurroundings)
        )
    }
}


fun saveUiSettings(uiSettings: UiSettings) {
    val settings = appSettings ?: return
    val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
    context (settings, uiSettingsClass) {
        setSetting(UiSettings::playerFirstColor, uiSettings.playerFirstColor)
        setSetting(UiSettings::playerSecondColor, uiSettings.playerSecondColor)
        setEnumSetting(UiSettings::connectionDrawMode, uiSettings.connectionDrawMode)
        setEnumSetting(UiSettings::baseDrawMode, uiSettings.baseDrawMode)
        setSetting(UiSettings::showDiagonalConnections, uiSettings.showDiagonalConnections)
        setSetting(UiSettings::showThreats, uiSettings.showThreats)
        setSetting(UiSettings::showSurroundings, uiSettings.showSurroundings)
    }
}