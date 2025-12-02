package org.dots.game

import kotlin.time.Instant

data class BuildInfo(val majorVersion: Int, val minorVersion: Int, val number: Int, val dateTime: Instant, val hash: String) {
    val version: String
        get() = "$majorVersion.$minorVersion.$number"

    val isLocal: Boolean = number == 65535

    val dateTimeString: String by lazy(LazyThreadSafetyMode.PUBLICATION) { dateTime.toString() }

    val date: String by lazy(LazyThreadSafetyMode.PUBLICATION) { dateTimeString.substringBefore('T') }

    val dateTimeShort: String by lazy(LazyThreadSafetyMode.PUBLICATION) { dateTimeString.substringBefore('.') }

    override fun toString(): String {
        return buildString {
            append("${BuildInfo::version.name}: $version, ")
            append("${BuildInfo::dateTime.name}: $dateTimeShort, ")
            append("${BuildInfo::hash.name}: ${hash.ifEmpty { "local" }}")
        }
    }
}

val buildInfo: BuildInfo = BuildInfo(
    majorVersion,
    minorVersion,
    buildNumber,
    buildDateTime,
    buildHash,
)
