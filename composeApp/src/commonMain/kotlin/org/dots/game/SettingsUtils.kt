package org.dots.game

import org.dots.game.dump.DumpParameters
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import org.dots.game.core.ClassSettings
import org.dots.game.core.Rules
import org.dots.game.views.OpenGameSettings
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

expect class SettingsWrapper<T : ClassSettings<T>> {
    companion object {
        fun <T : ClassSettings<T>> initialize(obj: T, directory: String?, loading: Boolean): SettingsWrapper<T>

        /** Drops every setting the app has kept, see [resetSettings]. */
        fun reset()
    }

    val obj: T

    val settings: Settings?

    fun save()
}

/**
 * Drops every setting of the app, so that it is started the way it is started for the very first time
 * the next time it is started: the engine it is shipped with, the rules and the look it comes with, and
 * no game of the last time at all.
 *
 * Nothing is saved for the rest of this run of the app either, because the app saves the game it is left
 * with and the window it is left in when it is closed, and a first start is a start with neither.
 */
fun resetSettings() {
    settingsAreReset = true
    SettingsWrapper.reset()
}

/** @see resetSettings */
internal var settingsAreReset = false

@IgnorableReturnValue
fun <T : ClassSettings<T>> saveClassSettings(settingsObj: T, directory: String? = null): Boolean {
    if (settingsAreReset) return false

    try {
        val settingsWrapper = SettingsWrapper.initialize(settingsObj, directory, loading = false)
        val settings = settingsWrapper.settings ?: return false
        when (settingsObj) {
            is Rules -> {
                context(settings, settingsObj) {
                    setSetting(Rules::width)
                    setSetting(Rules::height)
                    setSetting(Rules::captureByBorder)
                    setSetting(Rules::baseMode)
                    setSetting(Rules::suicideAllowed)
                    setSetting(Rules::initPosType)
                    setSetting(Rules::initPosGenType)
                    setSetting(Rules::komi)
                }
            }
            is DumpParameters -> {
                context(settings, settingsObj) {
                    setSetting(DumpParameters::printNumbers)
                    setSetting(DumpParameters::padding)
                    setSetting(DumpParameters::printCoordinates)
                    setSetting(DumpParameters::debugInfo)
                    setSetting(DumpParameters::format)
                    setSetting(DumpParameters::refineSgf)
                }
            }
            is UiSettings -> {
                context(settings, settingsObj) {
                    setSetting(UiSettings::playerFirstColor)
                    setSetting(UiSettings::playerSecondColor)
                    setSetting(UiSettings::connectionDrawMode)
                    setSetting(UiSettings::baseDrawMode)
                    setSetting(UiSettings::showDiagonalConnections)
                    setSetting(UiSettings::showThreats)
                    setSetting(UiSettings::showSurroundings)
                    setSetting(UiSettings::developerMode)
                    setSetting(UiSettings::experimentalMode)
                    setSetting(UiSettings::language)
                    setSetting(UiSettings::showWinRateGraph)
                    setSetting(UiSettings::showScoreGraph)
                    setSetting(UiSettings::showWeightGraph)
                    setSetting(UiSettings::showVisitsGraph)
                    setSetting(UiSettings::showCandidateMoves)
                    setSetting(UiSettings::showOwnership)
                    setSetting(UiSettings::showGameAnalysis)
                }
            }
            is OpenGameSettings -> {
                context(settings, settingsObj) {
                    setSetting(OpenGameSettings::pathOrContent)
                    setSetting(OpenGameSettings::rewindToEnd)
                    setSetting(OpenGameSettings::addFinishingMove)
                }
            }
            is GameSettings -> {
                context(settings, settingsObj) {
                    setSetting(GameSettings::path)
                    setSetting(GameSettings::sgf)
                    setSetting(GameSettings::game)
                    setSetting(GameSettings::node)
                }
            }
            is TimeSettings -> {
                context(settings, settingsObj) {
                    setSetting(TimeSettings::mainTimeSeconds)
                    setSetting(TimeSettings::turnTimeSeconds)
                }
            }
            is KataGoDotsSettings -> {
                context(settings, settingsObj) {
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
    } catch (ex: Exception) {
        println(getErrorMessage(settingsObj, ex, loading = false))
        return false
    }
}

fun <T : ClassSettings<T>> loadClassSettings(defaultSettingsObj: T, directory: String? = null): T {
    try {
        val settingsWrapper = SettingsWrapper.initialize(defaultSettingsObj, directory, loading = true)
        val settings = settingsWrapper.settings ?: return defaultSettingsObj

        @Suppress("UNCHECKED_CAST")
        return when (defaultSettingsObj) {
            is Rules -> {
                context(settings, defaultSettingsObj) {
                    Rules.create(
                        getSetting(Rules::width),
                        getSetting(Rules::height),
                        captureByBorder = getSetting(Rules::captureByBorder),
                        baseMode = getEnumSetting(Rules::baseMode),
                        suicideAllowed = getSetting(Rules::suicideAllowed),
                        initPosType = getEnumSetting(Rules::initPosType),
                        random = Rules.Standard.random,
                        initPosGenType = getEnumSetting(Rules::initPosGenType),
                        komi = getSetting(Rules::komi),
                    )
                }
            }
            is DumpParameters -> {
                context(settings, defaultSettingsObj) {
                    DumpParameters(
                        printNumbers = getSetting(DumpParameters::printNumbers),
                        padding = getSetting(DumpParameters::padding),
                        printCoordinates = getSetting(DumpParameters::printCoordinates),
                        debugInfo = getSetting(DumpParameters::debugInfo),
                        format = getEnumSetting(DumpParameters::format),
                        refineSgf = getSetting(DumpParameters::refineSgf),
                    )
                }
            }
            is UiSettings -> {
                context(settings, defaultSettingsObj) {
                    UiSettings(
                        playerFirstColor = getSetting(UiSettings::playerFirstColor),
                        playerSecondColor = getSetting(UiSettings::playerSecondColor),
                        connectionDrawMode = getEnumSetting(UiSettings::connectionDrawMode),
                        baseDrawMode = getEnumSetting(UiSettings::baseDrawMode),
                        showDiagonalConnections = getSetting(UiSettings::showDiagonalConnections),
                        showThreats = getSetting(UiSettings::showThreats),
                        showSurroundings = getSetting(UiSettings::showSurroundings),
                        developerMode = getSetting(UiSettings::developerMode),
                        experimentalMode = getSetting(UiSettings::experimentalMode),
                        language = getEnumSetting(UiSettings::language),
                        showWinRateGraph = getSetting(UiSettings::showWinRateGraph),
                        showScoreGraph = getSetting(UiSettings::showScoreGraph),
                        showWeightGraph = getSetting(UiSettings::showWeightGraph),
                        showVisitsGraph = getSetting(UiSettings::showVisitsGraph),
                        showCandidateMoves = getSetting(UiSettings::showCandidateMoves),
                        showOwnership = getSetting(UiSettings::showOwnership),
                        showGameAnalysis = getSetting(UiSettings::showGameAnalysis),
                    )
                }
            }
            is OpenGameSettings -> {
                context(settings, defaultSettingsObj) {
                    OpenGameSettings(
                        pathOrContent = getSetting(OpenGameSettings::pathOrContent),
                        addFinishingMove = getSetting(OpenGameSettings::addFinishingMove),
                        rewindToEnd = getSetting(OpenGameSettings::rewindToEnd),
                    )
                }
            }
            is GameSettings -> {
                context(settings, defaultSettingsObj) {
                    GameSettings(
                        path = getSetting(GameSettings::path),
                        sgf = getSetting(GameSettings::sgf),
                        game = getSetting(GameSettings::game),
                        node = getSetting(GameSettings::node),
                    )
                }
            }
            is TimeSettings -> {
                context(settings, defaultSettingsObj) {
                    TimeSettings(
                        mainTimeSeconds = getSetting(TimeSettings::mainTimeSeconds),
                        turnTimeSeconds = getSetting(TimeSettings::turnTimeSeconds),
                    )
                }
            }
            is KataGoDotsSettings -> {
                context(settings, defaultSettingsObj) {
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
    catch (ex: Exception) {
        println(getErrorMessage(defaultSettingsObj, ex, loading = true))
        return defaultSettingsObj
    }
}

context(settings: Settings, defaultObject: K)
inline fun <reified E : Enum<E>, reified K : ClassSettings<K>> getEnumSetting(property: KProperty1<K, E>): E {
    return settings.getStringOrNull(K::class.getSettingName(property))?.let { enumValueOf<E>(it)} ?: property.get(defaultObject)
}

context(settings: Settings, defaultObject: K)
inline fun <reified V, reified K : ClassSettings<K>> getSetting(property: KProperty1<K, V>): V {
    val settingName = K::class.getSettingName(property)
    val loadedValue: V? = when {
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
    return loadedValue ?: property.get(defaultObject)
}

context(settings: Settings, obj: K)
inline fun <reified V, reified K : ClassSettings<K>> setSetting(property: KProperty1<K, V>) {
    val settingName = K::class.getSettingName(property)
    val value = property.get(obj)
    when {
        V::class == Color::class -> settings.putLong(settingName, (value as Color).value.toLong())
        V::class == Dp::class -> settings.putFloat(settingName, (value as Dp).value)
        value is Enum<*> -> settings.putString(settingName, (value as Enum<*>).name)
        else -> settings[settingName] = value
    }
}

fun <T : ClassSettings<T>> getErrorMessage(settingsObj: T, ex: Exception, loading: Boolean): String {
    val trimmedExMessage = ex.message?.trimMessageIfNecessary()
    return "Unable to ${if (loading) "load" else "save"} ${settingsObj::class.simpleName} settings" +
            if (trimmedExMessage != null) ": $trimmedExMessage" else ""
}

fun KClass<*>.getSettingName(property: KProperty<*>): String {
    return "${simpleName!!}.${property.name}"
}