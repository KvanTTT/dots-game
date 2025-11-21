@file:OptIn(ExperimentalTime::class)

package org.dots.game

import dotsgame.composeapp.generated.resources.Res
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class BuildInfo(val number: Int, val date: Instant, val hash: String) {
    companion object {
        val Local: BuildInfo = BuildInfo(
            number = -1,
            date = Clock.System.now(),
            hash = "",
        )
    }

    override fun toString(): String {
        return buildString {
            append("${BuildInfo::number.name}: ${if (number == -1) "local" else number.toString()}, ")
            append("${BuildInfo::date.name}: ${Local.date}, ")
            append("${BuildInfo::hash.name}: ${hash.ifEmpty { "local" }}")
        }
    }
}

private var buildInfo: BuildInfo? = null

suspend fun getBuildInfo(): BuildInfo {
    buildInfo?.let {
        return it
    }

    return try {
        val pieces = Res.readBytes("files/build_info").decodeToString().split(',')
        val buildNumber = pieces[0].toInt()
        val date = Instant.parse(pieces[1])
        val commit = pieces[2]
        BuildInfo(buildNumber, date, commit)
    } catch (_: Exception) {
        BuildInfo.Local
    }.also {
        buildInfo = it
    }
}

