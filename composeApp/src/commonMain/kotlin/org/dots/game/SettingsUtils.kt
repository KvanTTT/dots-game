package org.dots.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

context(settings: Settings, klass: KClass<*>)
inline fun <reified E : Enum<E>> getEnumSetting(property: KProperty<*>, default: E): E {
    return settings.getStringOrNull(klass.getSettingName(property))?.let { enumValueOf<E>(it)} ?: default
}

context(settings: Settings, klass: KClass<*>)
inline fun <reified E : Enum<E>> setEnumSetting(property: KProperty<*>, value: E) {
    settings.putString(klass.getSettingName(property), value.name)
}

context(settings: Settings, klass: KClass<*>)
inline fun <reified T> getSetting(property: KProperty<*>, default: T): T {
    val settingName = klass.getSettingName(property)
    return (when {
        T::class == Color::class -> {
            settings.getLongOrNull(settingName)?.let { Color(it.toULong()) } as? T
        }
        T::class == Dp::class -> {
            settings.getFloatOrNull(settingName)?.let { Dp(it) } as? T
        }
        else -> {
            settings[settingName]
        }
    }) ?: default
}

context(settings: Settings, klass: KClass<*>)
inline fun <reified T> setSetting(property: KProperty<*>, value: T) {
    val settingName = klass.getSettingName(property)
    when {
        T::class == Color::class -> settings.putLong(settingName, (value as Color).value.toLong())
        T::class == Dp::class -> settings.putFloat(settingName, (value as Dp).value)
        else -> settings[settingName] = value
    }
}

fun KClass<*>.getSettingName(property: KProperty<*>): String {
    return "${this.simpleName!!}.${property.name}"
}