package org.dots.game

val BuildInfo.version: String
    get() = "$majorVersion.$minorVersion.$buildNumber"

val BuildInfo.isLocal: Boolean
    get() = buildNumber == 65535

val BuildInfo.dateTimeString: String
    get() = buildDateTime.toString()

val BuildInfo.date: String
    get() = dateTimeString.substringBefore('T')

val BuildInfo.dateTimeShort: String
    get() = dateTimeString.substringBefore('.')

fun BuildInfo.render(): String {
    return buildString {
        append("${BuildInfo::version.name}: $version, ")
        append("${BuildInfo::buildDateTime.name}: $dateTimeShort, ")
        if (buildHash.isNotEmpty()) {
            append("${BuildInfo::buildHash.name}: $buildHash")
        }
    }
}

const val THIS_APP_LOCAL_URL = "http://localhost:8080"
const val THIS_APP_SERVER_URL = "https://kvanttt.github.io/dots-game"

val thisAppUrl by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (BuildInfo.isLocal) THIS_APP_LOCAL_URL else THIS_APP_SERVER_URL
}