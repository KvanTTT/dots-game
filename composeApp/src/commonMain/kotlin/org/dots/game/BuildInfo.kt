@file:OptIn(ExperimentalTime::class)

package org.dots.game

import dotsgame.composeapp.generated.resources.Res
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class BuildInfo(val majorVersion: Int, val minorVersion: Int, val number: Int, val dateTime: Instant, val hash: String) {
    companion object {
        val Local: BuildInfo = BuildInfo(
            majorVersion = 1,
            minorVersion = 0,
            number = 65535,
            dateTime = Clock.System.now(),
            hash = "",
        )
    }

    val version: String
        get() = "$majorVersion.$minorVersion.$number"

    val date: String
        get() = dateTime.toString().substringBefore('T')

    override fun toString(): String {
        return buildString {
            append("${BuildInfo::version.name}: $version, ")
            append("${BuildInfo::dateTime.name}: ${dateTime}, ")
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
        var index = 0
        val majorVersion = pieces[index++].toInt()
        val minorVersion = pieces[index++].toInt()
        val buildNumber = pieces[index++].toInt()
        val date =
            pieces[index++].let { if (it.isNotEmpty()) Instant.parse(it) else Clock.System.now() }
        val commit = pieces[index]
        BuildInfo(majorVersion, minorVersion, buildNumber, date, commit)
    } catch (ex: Exception) {
        println("Incorrect build info format: ${ex.message}")
        BuildInfo.Local
    }.also {
        buildInfo = it
    }
}

