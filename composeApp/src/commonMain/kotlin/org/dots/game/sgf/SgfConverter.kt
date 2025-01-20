package org.dots.game.sgf

import org.dots.game.core.AppInfo
import org.dots.game.core.Field
import org.dots.game.core.GameInfo
import org.dots.game.core.Rules

class SgfConverter private constructor(val sgf: SgfRoot, val diagnosticReporter: (SgfDiagnostic) -> Unit) {
    companion object {
        const val GAME_MODE_KEY = "GM"
        const val FILE_FORMAT_KEY = "FF"
        const val CHARSET_KEY = "CA"
        const val SIZE_KEY = "SZ"
        const val RULES_KEY = "RU"
        const val RESULT_KEY = "RE"
        const val GAME_NAME_KEY = "GN"
        const val PLAYER1_MARKER = 'B'
        const val PLAYER2_MARKER = 'W'
        const val PLAYER1_NAME_KEY = "P${PLAYER1_MARKER}"
        const val PLAYER1_RATING_KEY = "${PLAYER1_MARKER}R"
        const val PLAYER1_TEAM_KEY = "${PLAYER1_MARKER}T"
        const val PLAYER2_NAME_KEY = "P${PLAYER2_MARKER}"
        const val PLAYER2_RATING_KEY = "${PLAYER2_MARKER}R"
        const val PLAYER2_TEAM_KEY = "${PLAYER2_MARKER}T"
        const val KOMI_KEY = "KM"
        const val DATE_KEY = "DT"
        const val GAME_COMMENT_KEY = "GC"
        const val PLACE_KEY = "PC"
        const val EVENT_KEY = "EV"
        const val OPENING_KEY = "ON"
        const val ANNOTATOR_KEY = "AN"
        const val COPYRIGHT_KEY = "CP"
        const val SOURCE_KEY = "SO"
        const val TIME_LIMIT_KEY = "TL"
        const val APP_INFO_KEY = "AP"

        val propertyNames: Map<String, String> = mapOf(
            GAME_MODE_KEY to "GameMode",
            FILE_FORMAT_KEY to "FileFormat",
            CHARSET_KEY to "Charset",
            SIZE_KEY to "Size",
            RULES_KEY to "Rules",
            RESULT_KEY to "Result",
            GAME_NAME_KEY to "GameName",
            PLAYER1_NAME_KEY to "Player1Name",
            PLAYER1_RATING_KEY to "Player1Rating",
            PLAYER1_TEAM_KEY to "Player1Team",
            PLAYER2_NAME_KEY to "Player2Name",
            PLAYER2_RATING_KEY to "Player2Rating",
            PLAYER2_TEAM_KEY to "Player2Team",
            KOMI_KEY to "Komi",
            DATE_KEY to "Date",
            GAME_COMMENT_KEY to "GameComment",
            PLACE_KEY to "Place",
            EVENT_KEY to "Event",
            OPENING_KEY to "Opening",
            COPYRIGHT_KEY to "Copyright",
            SOURCE_KEY to "Source",
            TIME_LIMIT_KEY to "TimeLimit",
            APP_INFO_KEY to "AppInfo",
        )

        /**
         * Returns `null` if a critical error occurs:
         *   * Unsupported file format (FF)
         *   * Unsupported mode (not Kropki)
         *   * Incorrect or unspecified size
         */
        fun convert(sgf: SgfRoot, diagnosticReporter: (SgfDiagnostic) -> Unit): List<GameInfo> {
            return SgfConverter(sgf, diagnosticReporter).convert()
        }
    }

    val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { sgf.text.buildLineOffsets() }

    fun convert(): List<GameInfo> {
        if (sgf.gameTree.isEmpty()) {
            reportDiagnostic("At least one game tree should be specified.", sgf.textSpan, DiagnosticSeverity.Error)
        }

        return buildList {
            for (gameTree in sgf.gameTree) {
                if (gameTree.nodes.isEmpty()) {
                    reportDiagnostic("At least one node should be specified.", TextSpan(gameTree.lParen.textSpan.end, 0), DiagnosticSeverity.Error)
                }

                for ((index, node) in gameTree.nodes.withIndex()) {
                    if (index == 0) {
                        convertGameInfo(node)?.let { add(it) }
                    } else {
                        // TODO: implement moves processing
                    }
                }
            }
        }
    }

    private fun convertGameInfo(node: Node): GameInfo? {
        var isValidFileFormat: Boolean? = null
        var isValidGameMode: Boolean? = null
        var sizeIsSpecified = false
        var width: Int? = null
        var height: Int? = null
        var gameName: String? = null
        var player1Name: String? = null
        var player1Rating: Double? = null
        var player1Team: String? = null
        var player2Name: String? = null
        var player2Rating: Double? = null
        var player2Team: String? = null
        var komi: Double? = null
        var date: String? = null // TODO: should it be typed?
        var description: String? = null
        var place: String? = null
        var event: String? = null
        var opening: String? = null
        var annotator: String? = null
        var copyright: String? = null
        var source: String? = null
        var timeLimit: Double? = null
        var appInfo: AppInfo? = null

        for (property in node.properties) {
            when (property.identifier.value) {
                GAME_MODE_KEY -> isValidGameMode = property.convertAndValidateGameMode()
                FILE_FORMAT_KEY -> isValidFileFormat = property.convertAndValidateFileFormat()
                CHARSET_KEY -> { // TODO
                }
                SIZE_KEY -> {
                    val (parsedWidth, parsedHeight) = property.convertSize() ?: continue
                    width = parsedWidth
                    height = parsedHeight
                    sizeIsSpecified = true
                }
                RULES_KEY -> { // TODO
                }
                RESULT_KEY -> {
                    // TODO
                }
                GAME_NAME_KEY -> gameName = property.convertSimpleText()

                PLAYER1_NAME_KEY -> player1Name = property.convertSimpleText()
                PLAYER1_RATING_KEY -> player1Rating = property.convertDouble()
                PLAYER1_TEAM_KEY -> player1Team = property.convertSimpleText()

                PLAYER2_NAME_KEY -> player2Name = property.convertSimpleText()
                PLAYER2_RATING_KEY -> player2Rating = property.convertDouble()
                PLAYER2_TEAM_KEY -> player2Team = property.convertSimpleText()

                KOMI_KEY -> komi = property.convertDouble()
                DATE_KEY -> date = property.convertSimpleText()
                GAME_COMMENT_KEY -> description = property.convertText()
                PLACE_KEY -> place = property.convertSimpleText()
                EVENT_KEY -> event = property.convertSimpleText()
                OPENING_KEY -> opening = property.convertSimpleText()
                ANNOTATOR_KEY -> annotator = property.convertSimpleText()
                COPYRIGHT_KEY -> copyright = property.convertSimpleText()
                SOURCE_KEY -> source = property.convertSimpleText()
                TIME_LIMIT_KEY -> timeLimit = property.convertDouble()
                APP_INFO_KEY -> appInfo = property.convertAppInfo()

                else -> {
                    property.reportPropertyDiagnostic(
                        "is unknown.",
                        property.identifier.textSpan,
                        DiagnosticSeverity.Warning,
                    )
                }
            }
        }

        if (isValidFileFormat == null) {

        }

        if (isValidGameMode == null) {

        }

        if (!sizeIsSpecified) {
            reportDiagnostic(
                generateMessageWithPropertyInfo(SIZE_KEY, "is not specified."),
                TextSpan(node.semicolon.textSpan.end, 0),
                DiagnosticSeverity.Critical
            )
        }

        if (isValidFileFormat == false || isValidGameMode == false || width == null || height == null) {
            return null
        }

        val rules = Rules(width, height)

        return GameInfo(
            gameName = gameName,
            player1Name = player1Name,
            player1Rating = player1Rating,
            player1Team = player1Team,
            player2Name = player2Name,
            player2Rating = player2Rating,
            player2Team = player2Team,
            komi = komi,
            date = date,
            description = description,
            place = place,
            event = event,
            opening = opening,
            annotator = annotator,
            copyright = copyright,
            source = source,
            timeLimit = timeLimit,
            appInfo = appInfo,
            rules = rules,
        )
    }

    /**
     * Returns `true` if the format is unspecified or incorrect (it's considered valid).
     * If it's specified, but not supported, it returns `false` (it doesn't make sense to continue converting).
     */
    private fun Property.convertAndValidateFileFormat(): Boolean {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return true
        val fileFormat = propertyValueToken.value.toIntOrNull()
            ?.let { SgfFileFormat.entries.firstOrNull { entry -> entry.value == it } }
        if (fileFormat != SgfFileFormat.Fourth) {
            reportPropertyDiagnostic(
                "has unsupported value `${propertyValueToken.value}`. The only `${SgfFileFormat.Fourth.value}` is supported.",
                propertyValueToken.textSpan,
                if (fileFormat != null) DiagnosticSeverity.Critical else DiagnosticSeverity.Error,
            )
            return false
        }
        return true
    }

    /**
     * Returns `true` if the game mode is unspecified or incorrect (it's considered valid).
     * If it's specified, but not supported, it returns `false` (it doesn't make sense to continue converting).
     */
    private fun Property.convertAndValidateGameMode(): Boolean {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return true
        val gameMode = propertyValueToken.value.toIntOrNull()
            ?.let { SgfGameMode.entries.firstOrNull { entry -> entry.value == it } }
        if (gameMode != SgfGameMode.Kropki) {
            val parsedGameMode = if (gameMode != null) {
                " (${gameMode})"
            } else {
                ""
            }
            reportPropertyDiagnostic(
                "has unsupported value `${propertyValueToken.value}`$parsedGameMode. The only `${SgfGameMode.Kropki.value}` (${SgfGameMode.Kropki}) is supported.",
                propertyValueToken.textSpan,
                if (gameMode != null) DiagnosticSeverity.Critical else DiagnosticSeverity.Error,
            )
            return false
        }
        return true
    }

    private fun Property.convertSize(): Pair<Int?, Int?>? {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return null
        val dimensions = propertyValueToken.value.split(':')
        val width: Int?
        val height: Int?
        when (dimensions.size) {
            1 -> {
                val maxDimension = minOf(Field.MAX_WIDTH, Field.MAX_HEIGHT)
                val size = dimensions[0].toIntOrNull()?.takeIf { it >= 0 && it <= maxDimension }
                if (size == null) {
                    width = null
                    height = null
                    reportPropertyDiagnostic(
                        "has invalid value `${dimensions[0]}`. Expected: 0..${maxDimension}.",
                        propertyValueToken.textSpan,
                        DiagnosticSeverity.Critical,
                    )
                } else {
                    width = size
                    height = size
                }
            }
            2 -> {
                val widthString = dimensions[0]
                val heightString = dimensions[1]
                width = widthString.toIntOrNull()?.takeIf { it >= 0 && it <= Field.MAX_WIDTH }
                if (width == null) {
                    reportPropertyDiagnostic(
                        "has invalid width: `${widthString}`. Expected: 0..${Field.MAX_WIDTH}.",
                        TextSpan(propertyValueToken.textSpan.start, widthString.length),
                        DiagnosticSeverity.Critical,
                    )
                }
                height = heightString.toIntOrNull()?.takeIf { it >= 0 && it <= Field.MAX_HEIGHT }
                if (height == null) {
                    reportPropertyDiagnostic(
                        "has invalid height: `${heightString}`. Expected: 0..${Field.MAX_HEIGHT}.",
                        TextSpan(propertyValueToken.textSpan.start + widthString.length + 1, heightString.length),
                        DiagnosticSeverity.Critical,
                    )
                }
            }
            else -> {
                width = null
                height = null
                reportPropertyDiagnostic(
                    "is defined in incorrect format: ${dimensions}. Expected: INT or INT:INT.",
                    propertyValueToken.textSpan,
                    DiagnosticSeverity.Critical,
                )
            }
        }
        return Pair(width, height)
    }

    private fun Property.convertAppInfo(): AppInfo? {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return null
        val propertyValueString = propertyValueToken.value

        // Handle escaping
        var colonIndex = -1
        do {
            colonIndex = propertyValueString.indexOf(':', colonIndex + 1)
            if (colonIndex == -1) break
            if (propertyValueString.elementAtOrNull(colonIndex - 1) != '\\') break
        } while (true)

        val name: String
        val version: String?
        if (colonIndex != -1) {
            name = propertyValueString.substring(0, colonIndex) .convertSimpleText()
            version = propertyValueString.substring(colonIndex + 1).convertSimpleText()
        } else {
            name = propertyValueString
            version = null
        }

        return AppInfo(name, version)
    }

    private fun Property.convertSimpleText(): String? {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return null
        return propertyValueToken.value.convertSimpleText()
    }

    private fun Property.convertText(): String? {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return null
        return propertyValueToken.value.convertText()
    }

    private fun Property.convertDouble(): Double? {
        val propertyValueToken = extractSinglePropertyValueToken() ?: return null
        return propertyValueToken.value.toDoubleOrNull()
    }

    private fun Property.extractSinglePropertyValueToken(): PropertyValueToken? {
        val firstPropertyValue = value.firstOrNull()
        if (firstPropertyValue == null) {
            reportPropertyDiagnostic("is unspecified.", textSpan, DiagnosticSeverity.Error)
            return null
        } else if (value.size > 1) {
            reportPropertyDiagnostic("has multiple values. The first will be used.", textSpan, DiagnosticSeverity.Warning)
        }

        return firstPropertyValue.propertyValueToken
    }

    private fun Property.reportPropertyDiagnostic(message: String, textSpan: TextSpan, severity: DiagnosticSeverity) {
        val messageWithPropertyInfo = generateMessageWithPropertyInfo(identifier.value, message)
        val lineColumn = textSpan.start.getLineColumn(lineOffsets)
        diagnosticReporter(SgfDiagnostic(messageWithPropertyInfo, lineColumn, severity))
    }

    private fun reportDiagnostic(message: String, textSpan: TextSpan, severity: DiagnosticSeverity) {
        diagnosticReporter(SgfDiagnostic(message, textSpan.start.getLineColumn(lineOffsets), severity))
    }

    private fun generateMessageWithPropertyInfo(identifier: String, message: String): String {
        val propertyId = identifier
        val propertyNameInfix = propertyNames[propertyId]?.let { " ($it)" } ?: ""
        return "Property ${propertyId}${propertyNameInfix} $message"
    }
}

data class SgfDiagnostic(
    val message: String,
    val lineColumn: LineColumn,
    val severity: DiagnosticSeverity,
) {
    override fun toString(): String {
        return "$severity at $lineColumn: $message"
    }
}

enum class DiagnosticSeverity {
    Warning,
    Error,
    Critical,
}
