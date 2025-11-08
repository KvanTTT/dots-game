package org.dots.game

import DumpParameters
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import org.dots.game.core.ClassSettings
import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.sgf.SgfWriter
import org.dots.game.views.OpenGameSettings
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

expect var appSettings: Settings?

expect class SettingsWrapper<T : ClassSettings<T>> {
    companion object {
        fun <T : ClassSettings<T>> initialize(obj: T, directory: String?, loading: Boolean): SettingsWrapper<T>
    }

    val obj: T

    val settings: Settings?

    fun save()
}

fun <T : ClassSettings<T>> saveClassSettings(settingsObj: T, extraObj: Any? = null, directory: String? = null): Boolean {
    val settingsWrapper = SettingsWrapper.initialize(settingsObj, directory, loading = false)
    val settings = settingsWrapper.settings ?: return false
    when (settingsObj) {
        is Rules -> {
            val ruleClass = Rules::class // TODO: inline after KT-80853
            context(settings, ruleClass, settingsObj) {
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
        is DumpParameters -> {
            val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
            context (settings, dumpParametersClass, settingsObj) {
                setSetting(DumpParameters::printNumbers)
                setSetting(DumpParameters::padding)
                setSetting(DumpParameters::printCoordinates)
                setSetting(DumpParameters::debugInfo)
                setSetting(DumpParameters::isSgf)
            }
        }
        is UiSettings -> {
            val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
            context (settings, uiSettingsClass, settingsObj) {
                setSetting(UiSettings::playerFirstColor)
                setSetting(UiSettings::playerSecondColor)
                setSetting(UiSettings::connectionDrawMode)
                setSetting(UiSettings::baseDrawMode)
                setSetting(UiSettings::showDiagonalConnections)
                setSetting(UiSettings::showThreats)
                setSetting(UiSettings::showSurroundings)
                setSetting(UiSettings::developerMode)
                setSetting(UiSettings::language)
            }
        }
        is OpenGameSettings -> {
            val openGameSettingsClass = OpenGameSettings::class // TODO: inline after KT-80853
            context (settings, openGameSettingsClass, settingsObj) {
                setSetting(OpenGameSettings::pathOrContent)
                setSetting(OpenGameSettings::rewindToEnd)
                setSetting(OpenGameSettings::addFinishingMove)
            }
        }
        is CurrentGameSettings -> {
            val games = extraObj as? Games
            if (games != null) {
                settingsObj.content = SgfWriter.write(games)
                val currentGame = games.elementAtOrNull(settingsObj.currentGameNumber)
                if (currentGame != null) {
                    settingsObj.currentNodeNumber = currentGame.gameTree.getCurrentNodeDepthFirstIndex()
                }
            }
            val currentGameSettingsClass = CurrentGameSettings::class // TODO: inline after KT-80853
            context (settings, currentGameSettingsClass, settingsObj) {
                setSetting(CurrentGameSettings::path)
                setSetting(CurrentGameSettings::content)
                setSetting(CurrentGameSettings::currentGameNumber)
                setSetting(CurrentGameSettings::currentNodeNumber)
            }
        }
        is KataGoDotsSettings -> {
            val kataGoDotsSettingsClass = KataGoDotsSettings::class // TODO: inline after KT-80853
            context (settings, kataGoDotsSettingsClass, settingsObj) {
                setSetting(KataGoDotsSettings::exePath)
                setSetting(KataGoDotsSettings::modelPath)
                setSetting(KataGoDotsSettings::configPath)
                setSetting(KataGoDotsSettings::maxTime)
                setSetting(KataGoDotsSettings::maxPlayouts)
                setSetting(KataGoDotsSettings::maxVisits)
                setSetting(KataGoDotsSettings::autoMove)
            }
        }
        else -> {
            error("Saver for $settingsObj is not implemented")
        }
    }
    settingsWrapper.save()
    return true
}

fun <T : ClassSettings<T>> loadClassSettings(defaultSettingsObj: T, directory: String? = null): T {
    val settingsWrapper = SettingsWrapper.initialize(defaultSettingsObj, directory, loading = true)
    val settings = settingsWrapper.settings ?: return defaultSettingsObj

    @Suppress("UNCHECKED_CAST")
    return when (defaultSettingsObj) {
        is Rules -> {
            val ruleClass = Rules::class // TODO: inline after KT-80853
            context (settings, ruleClass, defaultSettingsObj) {
                Rules.create(
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
        is DumpParameters -> {
            val dumpParametersClass = DumpParameters::class // TODO: inline after KT-80853
            context (settings, dumpParametersClass, defaultSettingsObj) {
                DumpParameters(
                    printNumbers = getSetting(DumpParameters::printNumbers),
                    padding = getSetting(DumpParameters::padding),
                    printCoordinates = getSetting(DumpParameters::printCoordinates),
                    debugInfo = getSetting(DumpParameters::debugInfo),
                    isSgf = getSetting(DumpParameters::isSgf),
                )
            }
        }
        is UiSettings -> {
            val uiSettingsClass = UiSettings::class // TODO: inline after KT-80853
            context (settings, uiSettingsClass,  defaultSettingsObj) {
                UiSettings(
                    playerFirstColor = getSetting(UiSettings::playerFirstColor),
                    playerSecondColor = getSetting(UiSettings::playerSecondColor),
                    connectionDrawMode = getEnumSetting(UiSettings::connectionDrawMode),
                    baseDrawMode = getEnumSetting(UiSettings::baseDrawMode),
                    showDiagonalConnections = getSetting(UiSettings::showDiagonalConnections),
                    showThreats = getSetting(UiSettings::showThreats),
                    showSurroundings = getSetting(UiSettings::showSurroundings),
                    developerMode = getSetting(UiSettings::developerMode),
                    language = getEnumSetting(UiSettings::language),
                )
            }
        }
        is OpenGameSettings -> {
            val openGameSettingsClass = OpenGameSettings::class // TODO: inline after KT-80853
            context (settings, openGameSettingsClass, defaultSettingsObj) {
                OpenGameSettings(
                    pathOrContent = getSetting(OpenGameSettings::pathOrContent),
                    addFinishingMove = getSetting(OpenGameSettings::addFinishingMove),
                    rewindToEnd = getSetting(OpenGameSettings::rewindToEnd),
                )
            }
        }
        is CurrentGameSettings -> {
            val currentGameSettingsClass = CurrentGameSettings::class // TODO: inline after KT-80853
            context (settings, currentGameSettingsClass, defaultSettingsObj) {
                CurrentGameSettings(
                    path = getSetting(CurrentGameSettings::path),
                    content = getSetting(CurrentGameSettings::content),
                    currentGameNumber = getSetting(CurrentGameSettings::currentGameNumber),
                    currentNodeNumber = getSetting(CurrentGameSettings::currentNodeNumber),
                )
            }
        }
        is KataGoDotsSettings -> {
            val kataGoDotsSettingsClass = KataGoDotsSettings::class // TODO: inline after KT-80853
            context (settings, kataGoDotsSettingsClass,  defaultSettingsObj) {
                KataGoDotsSettings(
                    exePath = getSetting(KataGoDotsSettings::exePath),
                    modelPath = getSetting(KataGoDotsSettings::modelPath),
                    configPath = getSetting(KataGoDotsSettings::configPath),
                    maxTime = getSetting(KataGoDotsSettings::maxTime),
                    maxPlayouts = getSetting(KataGoDotsSettings::maxPlayouts),
                    maxVisits = getSetting(KataGoDotsSettings::maxVisits),
                    autoMove = getSetting(KataGoDotsSettings::autoMove),
                )
            }
        }
        else -> {
            error("Loader for $defaultSettingsObj is not implemented")
        }
    } as T
}

context(settings: Settings, klass: KClass<K>, defaultObject: K)
inline fun <reified E : Enum<E>, reified K : Any> getEnumSetting(property: KProperty1<K, E>): E {
    return settings.getStringOrNull(klass.getSettingName(property))?.let { enumValueOf<E>(it)} ?: property.get(defaultObject)
}

context(settings: Settings, klass: KClass<K>, defaultObject: K)
inline fun <reified V, reified K : Any> getSetting(property: KProperty1<K, V>): V {
    val settingName = klass.getSettingName(property)
    val loadedValue: V? = try {
        when {
            V::class == Color::class -> {
                settings.getLongOrNull(settingName)?.let { Color(it.toULong()) } as? V
            }
            V::class == Dp::class -> {
                settings.getFloatOrNull(settingName)?.let { Dp(it) } as? V
            }
            else -> {
                settings[settingName]
            }
        }
    } catch (e: Exception) {
        println(getErrorMessage(settingName, e, reading = true))
        null
    }
    return loadedValue ?: property.get(defaultObject)
}

context(settings: Settings, klass: KClass<K>, obj: K)
inline fun <reified V, reified K : ClassSettings<K>> setSetting(property: KProperty1<K, V>) {
    val settingName = klass.getSettingName(property)
    val value = property.get(obj)
    try {
        when {
            V::class == Color::class -> settings.putLong(settingName, (value as Color).value.toLong())
            V::class == Dp::class -> settings.putFloat(settingName, (value as Dp).value)
            value is Enum<*> -> settings.putString(settingName, (value as Enum<*>).name)
            else -> settings[settingName] = value
        }
    } catch (e: Exception) {
        println(getErrorMessage(settingName, e, reading = false))
    }
}

private const val MAX_MESSAGE_LENGTH = 400

fun getErrorMessage(settingName: String, ex: Exception, reading: Boolean): String {
    val exMessage = ex.message
    val trimmedExMessage = if (exMessage != null && exMessage.length > MAX_MESSAGE_LENGTH)
        exMessage.substring(0, MAX_MESSAGE_LENGTH) + "..."
    else
        exMessage
    return "Unable to ${if (reading) "get" else "set"} setting $settingName" +
            if (trimmedExMessage != null) ": $trimmedExMessage" else ""
}

fun KClass<*>.getSettingName(property: KProperty<*>): String {
    return "${simpleName!!}.${property.name}"
}