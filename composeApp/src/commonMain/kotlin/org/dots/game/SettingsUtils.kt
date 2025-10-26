package org.dots.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

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
inline fun <reified V, reified K : Any> setSetting(property: KProperty1<K, V>) {
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