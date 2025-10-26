package org.dots.game

import DumpParameters
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.sgf.SgfWriter
import org.dots.game.views.OpenGameSettings
import kotlin.random.Random

fun loadRules(): Rules {
    val settings = appSettings ?: return Rules.Standard
    val ruleClass = Rules::class // TODO: inline after KT-80853
    context (settings, ruleClass, Rules.Standard) {
        return Rules.create(
            getSetting(Rules::width),
            getSetting(Rules::height),
            captureByBorder = getSetting(Rules::captureByBorder),
            baseMode = getEnumSetting(Rules::baseMode),
            suicideAllowed = getSetting(Rules::suicideAllowed),
            initPosType = getEnumSetting(Rules::initPosType),
            random = Random.takeIf { getSetting(Rules::initPosIsRandom) },
            komi = getSetting(Rules::komi),
        )
    }
}

fun saveRules(rules: Rules) {
    val settings = appSettings ?: return
    val ruleClass = Rules::class // TODO: inline after KT-80853
    context(settings, ruleClass, rules) {
        setSetting(Rules::width)
        setSetting(Rules::height)
        setSetting(Rules::captureByBorder)
        setSetting(Rules::baseMode)
        setSetting(Rules::suicideAllowed)
        setSetting(Rules::initPosType)
        setSetting(Rules::initPosIsRandom)
        setSetting(Rules::komi)
    }
}

fun loadDumpParameters(): DumpParameters {
    val settings = appSettings ?: return DumpParameters()
    val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
    context (settings, dumpParametersClass, DumpParameters.DEFAULT) {
        return DumpParameters(
            printNumbers = getSetting(DumpParameters::printNumbers),
            padding = getSetting(DumpParameters::padding),
            printCoordinates = getSetting(DumpParameters::printCoordinates),
            debugInfo = getSetting(DumpParameters::debugInfo),
            isSgf = getSetting(DumpParameters::isSgf),
        )
    }
}

fun saveDumpParameters(dumpParameters: DumpParameters) {
    val settings = appSettings ?: return
    val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
    context (settings, dumpParametersClass, dumpParameters) {
        setSetting(DumpParameters::printNumbers)
        setSetting(DumpParameters::padding)
        setSetting(DumpParameters::printCoordinates)
        setSetting(DumpParameters::debugInfo)
        setSetting(DumpParameters::isSgf)
    }
}

fun loadUiSettings(): UiSettings {
    val settings = appSettings ?: return UiSettings.Standard
    val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
    context (settings, uiSettingsClass,  UiSettings.Standard) {
        return UiSettings(
            playerFirstColor = getSetting(UiSettings::playerFirstColor),
            playerSecondColor = getSetting(UiSettings::playerSecondColor),
            connectionDrawMode = getEnumSetting(UiSettings::connectionDrawMode),
            baseDrawMode = getEnumSetting(UiSettings::baseDrawMode),
            showDiagonalConnections = getSetting(UiSettings::showDiagonalConnections),
            showThreats = getSetting(UiSettings::showThreats),
            showSurroundings = getSetting(UiSettings::showSurroundings),
            developerMode = getSetting(UiSettings::developerMode),
        )
    }
}

fun saveUiSettings(uiSettings: UiSettings) {
    val settings = appSettings ?: return
    val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
    context (settings, uiSettingsClass, uiSettings) {
        setSetting(UiSettings::playerFirstColor)
        setSetting(UiSettings::playerSecondColor)
        setSetting(UiSettings::connectionDrawMode)
        setSetting(UiSettings::baseDrawMode)
        setSetting(UiSettings::showDiagonalConnections)
        setSetting(UiSettings::showThreats)
        setSetting(UiSettings::showSurroundings)
        setSetting(UiSettings::developerMode)
    }
}

fun loadOpenGameSettings(): OpenGameSettings {
    val settings = appSettings ?: return OpenGameSettings.Default
    val openGameSettingsClass = OpenGameSettings::class // TODO: inline after KT-80853
    context (settings, openGameSettingsClass, OpenGameSettings.Default) {
        return OpenGameSettings(
            pathOrContent = getSetting(OpenGameSettings::pathOrContent),
            addFinishingMove = getSetting(OpenGameSettings::addFinishingMove),
            rewindToEnd = getSetting(OpenGameSettings::rewindToEnd),
        )
    }
}

fun saveOpenGameSettings(openGameSettings: OpenGameSettings) {
    val settings = appSettings ?: return
    val openGameSettingsClass = OpenGameSettings::class // TODO: inline after KT-80853
    context (settings, openGameSettingsClass, openGameSettings) {
        setSetting(OpenGameSettings::pathOrContent)
        setSetting(OpenGameSettings::rewindToEnd)
        setSetting(OpenGameSettings::addFinishingMove)
    }
}

fun loadCurrentGameSettings(): CurrentGameSettings {
    val settings = appSettings ?: return CurrentGameSettings.Default
    val currentGameSettingsClass = CurrentGameSettings::class // TODO: inline after KT-80853
    context (settings, currentGameSettingsClass, CurrentGameSettings.Default) {
        return CurrentGameSettings(
            path = getSetting(CurrentGameSettings::path),
            content = getSetting(CurrentGameSettings::content),
            currentGameNumber = getSetting(CurrentGameSettings::currentGameNumber),
            currentNodeNumber = getSetting(CurrentGameSettings::currentNodeNumber),
        )
    }
}

fun saveCurrentGameSettings(currentGameSettings: CurrentGameSettings, games: Games?) {
    val settings = appSettings ?: return
    if (games != null) {
        currentGameSettings.content = SgfWriter.write(games)
        val currentGame = games.elementAtOrNull(currentGameSettings.currentGameNumber)
        if (currentGame != null) {
            currentGameSettings.currentNodeNumber = currentGame.gameTree.getCurrentNodeDepthFirstIndex()
        }
    }
    val currentGameSettingsClass = CurrentGameSettings::class // TODO: inline after KT-80853
    context (settings, currentGameSettingsClass, currentGameSettings) {
        setSetting(CurrentGameSettings::path)
        setSetting(CurrentGameSettings::content)
        setSetting(CurrentGameSettings::currentGameNumber)
        setSetting(CurrentGameSettings::currentNodeNumber)
    }
}