package org.dots.game.sgf

import org.dots.game.core.Field
import org.dots.game.core.Game
import org.dots.game.core.GameInfo
import org.dots.game.core.GameTree
import org.dots.game.core.MoveInfo
import org.dots.game.core.Position
import org.dots.game.core.Rules
import org.dots.game.sgf.SgfGameMode.Companion.SUPPORTED_GAME_MODE_KEY
import org.dots.game.sgf.SgfGameMode.Companion.SUPPORTED_GAME_MODE_NAME
import org.dots.game.sgf.SgfMetaInfo.ANNOTATOR_KEY
import org.dots.game.sgf.SgfMetaInfo.APP_INFO_KEY
import org.dots.game.sgf.SgfMetaInfo.COMMENT_KEY
import org.dots.game.sgf.SgfMetaInfo.COPYRIGHT_KEY
import org.dots.game.sgf.SgfMetaInfo.DATE_KEY
import org.dots.game.sgf.SgfMetaInfo.EVENT_KEY
import org.dots.game.sgf.SgfMetaInfo.FILE_FORMAT_KEY
import org.dots.game.sgf.SgfMetaInfo.GAME_COMMENT_KEY
import org.dots.game.sgf.SgfMetaInfo.GAME_MODE_KEY
import org.dots.game.sgf.SgfMetaInfo.GAME_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.KOMI_KEY
import org.dots.game.sgf.SgfMetaInfo.OPENING_KEY
import org.dots.game.sgf.SgfMetaInfo.OVERTIME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLACE_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.SIZE_KEY
import org.dots.game.sgf.SgfMetaInfo.SOURCE_KEY
import org.dots.game.sgf.SgfMetaInfo.TIME_KEY
import org.dots.game.sgf.SgfMetaInfo.propertyInfoToKey
import org.dots.game.sgf.SgfMetaInfo.propertyInfos

class SgfConverter private constructor(val sgf: SgfRoot, val diagnosticReporter: (SgfDiagnostic) -> Unit) {
    companion object {
        /**
         * Returns `null` if a critical error occurs:
         *   * Unsupported file format (FF)
         *   * Unsupported mode (not Kropki)
         *   * Incorrect or unspecified size
         */
        fun convert(sgf: SgfRoot, diagnosticReporter: (SgfDiagnostic) -> Unit): List<Game> {
            return SgfConverter(sgf, diagnosticReporter).convert()
        }
    }

    private val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { sgf.text.buildLineOffsets() }

    private fun TextSpan.getText() = sgf.text.substring(start, end)

    fun convert(): List<Game> {
        if (sgf.gameTree.isEmpty()) {
            reportDiagnostic("At least one game tree should be specified.", sgf.textSpan)
        }

        return buildList {
            for (gameTree in sgf.gameTree) {
                if (gameTree.nodes.isEmpty()) {
                    reportDiagnostic("At least one node should be specified.", TextSpan(gameTree.lParen.textSpan.end, 0))
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

    private fun convertGameInfo(node: SgfNode): Game? {
        val gameInfoProperties = mutableMapOf<String, SgfProperty<*>>()
        var hasCriticalError = false

        for (property in node.properties) {
            val propertyIdentifier = property.identifier.value

            val existingProperty = gameInfoProperties[propertyIdentifier]
            val (sgfProperty, reportedCriticalError) = property.convert()
            if (existingProperty == null) {
                hasCriticalError = hasCriticalError or reportedCriticalError
                gameInfoProperties[propertyIdentifier] = sgfProperty
            } else if (sgfProperty.info.isKnown) {
                sgfProperty.info.reportPropertyDiagnostic(
                    "is duplicated and ignored.",
                    property.textSpan,
                    SgfDiagnosticSeverity.Warning
                )
            }
        }

        fun reportErrorIfNotSpecified(propertyKey: String, severity: SgfDiagnosticSeverity) {
            if (gameInfoProperties[propertyKey] == null) {
                propertyInfos.getValue(propertyKey).reportPropertyDiagnostic(
                    "should be specified.",
                    TextSpan(node.semicolon.textSpan.end, 0),
                    severity
                )
            }
        }

        // Report only properties that should be specified
        reportErrorIfNotSpecified(GAME_MODE_KEY, SgfDiagnosticSeverity.Error)
        reportErrorIfNotSpecified(FILE_FORMAT_KEY, SgfDiagnosticSeverity.Error)

        val sizeProperty = gameInfoProperties[SIZE_KEY]
        val width: Int?
        val height: Int?
        if (sizeProperty != null) {
            @Suppress("UNCHECKED_CAST")
            val sizeValue = sizeProperty.value as? Pair<Int?, Int?>
            if (sizeValue == null) {
                width = null
                height = null
            } else {
                width = sizeValue.first
                height = sizeValue.second
            }
        } else {
            width = null
            height = null
            reportErrorIfNotSpecified(SIZE_KEY, SgfDiagnosticSeverity.Critical)
            hasCriticalError = true
        }

        if (hasCriticalError || width == null || height == null) return null

        fun <T> String.getPropertyValue(): T? {
            @Suppress("UNCHECKED_CAST")
            return gameInfoProperties[this]?.value as? T
        }

        val initialMoves = buildList {
            addAll(PLAYER1_ADD_DOTS_KEY.getPropertyValue<List<MoveInfo>>() ?: emptyList())
            for (player2InitialMoveInfo in (PLAYER2_ADD_DOTS_KEY.getPropertyValue<List<MoveInfo>>() ?: emptyList())) {
                if (removeAll { it.position == player2InitialMoveInfo.position }) {
                    val (propertyInfo, textSpan) = player2InitialMoveInfo.extraInfo as PropertyInfoAndTextSpan
                    propertyInfo.reportPropertyDiagnostic(
                        "value `${textSpan.getText()}` overwrites one the previous position of first player ${
                            propertyInfos.getValue(
                                PLAYER1_ADD_DOTS_KEY
                            ).getFullName()
                        }.",
                        textSpan,
                        SgfDiagnosticSeverity.Warning,
                    )
                }
                add(player2InitialMoveInfo)
            }
        }

        val gameInfo = GameInfo(
            gameName = GAME_NAME_KEY.getPropertyValue(),
            player1Name = PLAYER1_NAME_KEY.getPropertyValue(),
            player1Rating = PLAYER1_RATING_KEY.getPropertyValue(),
            player1Team = PLAYER1_TEAM_KEY.getPropertyValue(),
            player2Name = PLAYER2_NAME_KEY.getPropertyValue(),
            player2Rating = PLAYER2_RATING_KEY.getPropertyValue(),
            player2Team = PLAYER2_TEAM_KEY.getPropertyValue(),
            komi = KOMI_KEY.getPropertyValue(),
            date = DATE_KEY.getPropertyValue(),
            description = GAME_COMMENT_KEY.getPropertyValue(),
            comment = COMMENT_KEY.getPropertyValue(),
            place = PLACE_KEY.getPropertyValue(),
            event = EVENT_KEY.getPropertyValue(),
            opening = OPENING_KEY.getPropertyValue(),
            annotator = ANNOTATOR_KEY.getPropertyValue(),
            copyright = COPYRIGHT_KEY.getPropertyValue(),
            source = SOURCE_KEY.getPropertyValue(),
            time = TIME_KEY.getPropertyValue(),
            overtime = OVERTIME_KEY.getPropertyValue(),
            appInfo = APP_INFO_KEY.getPropertyValue(),
        )

        val rules = Rules(width, height, initialMoves = initialMoves)
        val field = Field(rules) { errorMoveInfo ->
            val (propertyInfo, textSpan) = errorMoveInfo.extraInfo as PropertyInfoAndTextSpan
            propertyInfo.reportPropertyDiagnostic(
                "value `${textSpan.getText()}` is incorrect. The dot at position `${errorMoveInfo.position}` is already placed or captured.",
                textSpan,
                SgfDiagnosticSeverity.Error,
            )
        }

        val gameTree = GameTree(field)

        return Game(gameInfo, gameTree)
    }

    private fun SgfPropertyNode.convert(): Pair<SgfProperty<*>, Boolean> {
        var reportedCriticalError = false

        val propertyIdentifier = identifier.value
        val propertyInfo = propertyInfos[propertyIdentifier] ?: SgfPropertyInfo(
            propertyIdentifier,
            SgfPropertyType.Text,
            isKnown = false
        )

        if (propertyInfo.isKnown) {
            if (value.isEmpty()) {
                propertyInfo.reportPropertyDiagnostic(
                    "is unspecified.",
                    TextSpan(textSpan.end, 0),
                    if (propertyIdentifier == SIZE_KEY) SgfDiagnosticSeverity.Critical else SgfDiagnosticSeverity.Error
                )
                reportedCriticalError = propertyIdentifier == SIZE_KEY
            }
        } else {
            propertyInfo.reportPropertyDiagnostic(
                "is unknown.",
                identifier.textSpan,
                SgfDiagnosticSeverity.Warning,
            )
        }

        val convertedValues = mutableListOf<Any?>()
        for ((index, propertyValue) in value.withIndex()) {
            val propertyValueToken = propertyValue.propertyValueToken
            val propertyValue = propertyValueToken.value

            val convertedValue = when (propertyInfo.type) {
                SgfPropertyType.Number -> {
                    val intValue = propertyValue.toIntOrNull()

                    reportedCriticalError = reportedCriticalError or when (propertyIdentifier) {
                        GAME_MODE_KEY -> validateGameMode(intValue, propertyInfo, propertyValue, propertyValueToken)
                        FILE_FORMAT_KEY -> validateFileFormat(
                            intValue,
                            propertyInfo,
                            propertyValue,
                            propertyValueToken
                        )
                        else -> {
                            if (intValue == null) {
                                propertyInfo.reportPropertyDiagnostic(
                                    "has incorrect format: `${propertyValue}`. Expected: Number.",
                                    propertyValueToken.textSpan,
                                    SgfDiagnosticSeverity.Warning,
                                )
                            }
                            false
                        }
                    }

                    intValue
                }

                SgfPropertyType.Double -> propertyValue.toDoubleOrNull().also {
                    if (it == null) {
                        propertyInfo.reportPropertyDiagnostic(
                            "has incorrect format: `${propertyValue}`. Expected: Real Number.",
                            propertyValueToken.textSpan,
                            SgfDiagnosticSeverity.Warning,
                        )
                    }
                }
                SgfPropertyType.SimpleText -> propertyValue.convertSimpleText()
                SgfPropertyType.Text -> propertyValue.convertText()
                SgfPropertyType.Size -> propertyValueToken.convertSize(propertyInfo)
                SgfPropertyType.AppInfo -> propertyValue.convertAppInfo()
                SgfPropertyType.Position -> {
                    propertyValueToken.convertPosition(propertyInfo)?.also { moveInfo ->
                        if (convertedValues.removeAll { (it as MoveInfo).position == moveInfo.position }) {
                            propertyInfo.reportPropertyDiagnostic(
                                "value `${propertyValue}` overwrites one the previous position.",
                                propertyValueToken.textSpan,
                                SgfDiagnosticSeverity.Warning,
                            )
                        }
                    }
                }
            }

            convertedValue?.let { convertedValues.add(it) }

            if (index > 0 && !propertyInfo.multipleValues) {
                propertyInfo.reportPropertyDiagnostic(
                    "has duplicated value `$propertyValue` that's ignored.",
                    propertyValueToken.textSpan,
                    SgfDiagnosticSeverity.Warning
                )
            }
        }

        val resultValue = if (!propertyInfo.multipleValues) {
            convertedValues.firstOrNull()
        } else {
            convertedValues
        }

        return SgfProperty(propertyInfo, resultValue) to reportedCriticalError
    }

    private fun validateGameMode(
        intValue: Int?,
        propertyInfo: SgfPropertyInfo,
        propertyValue: String,
        propertyValueToken: PropertyValueToken,
    ): Boolean {
        if (intValue == null || intValue != SUPPORTED_GAME_MODE_KEY) {
            val parsedGameMode = SgfGameMode.gameModes[intValue]?.let { " (${it})" } ?: ""
            // If the value is specified and incorrect, report critical,
            // because it doesn't make sense to continue converting
            propertyInfo.reportPropertyDiagnostic(
                "has unsupported value `${propertyValue}`$parsedGameMode. The only `${SUPPORTED_GAME_MODE_KEY}` (${SUPPORTED_GAME_MODE_NAME}) is supported.",
                propertyValueToken.textSpan,
                if (intValue != null) SgfDiagnosticSeverity.Critical else SgfDiagnosticSeverity.Error,
            )
            return intValue != null
        }
        return false
    }

    private fun validateFileFormat(
        intValue: Int?,
        propertyInfo: SgfPropertyInfo,
        propertyValue: String,
        propertyValueToken: PropertyValueToken,
    ): Boolean {
        if (intValue == null || intValue != SUPPORTED_FILE_FORMAT) {
            // If the value is specified and incorrect, report critical,
            // because it doesn't make sense to continue converting
            propertyInfo.reportPropertyDiagnostic(
                "has unsupported value `${propertyValue}`. The only `$SUPPORTED_FILE_FORMAT` is supported.",
                propertyValueToken.textSpan,
                if (intValue != null) SgfDiagnosticSeverity.Critical else SgfDiagnosticSeverity.Error,
            )
            return intValue != null
        }
        return false
    }

    private fun PropertyValueToken.convertSize(propertyInfo: SgfPropertyInfo): Pair<Int?, Int?> {
        val dimensions = value.split(':')
        val width: Int?
        val height: Int?
        when (dimensions.size) {
            1 -> {
                val maxDimension = minOf(Field.MAX_WIDTH, Field.MAX_HEIGHT)
                val size = dimensions[0].toIntOrNull()?.takeIf { it >= 0 && it <= maxDimension }
                if (size == null) {
                    width = null
                    height = null
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid value `${dimensions[0]}`. Expected: 0..${maxDimension}.",
                        textSpan,
                        SgfDiagnosticSeverity.Critical,
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
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid width: `${widthString}`. Expected: 0..${Field.MAX_WIDTH}.",
                        TextSpan(textSpan.start, widthString.length),
                        SgfDiagnosticSeverity.Critical,
                    )
                }
                height = heightString.toIntOrNull()?.takeIf { it >= 0 && it <= Field.MAX_HEIGHT }
                if (height == null) {
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid height: `${heightString}`. Expected: 0..${Field.MAX_HEIGHT}.",
                        TextSpan(textSpan.start + widthString.length + 1, heightString.length),
                        SgfDiagnosticSeverity.Critical,
                    )
                }
            }
            else -> {
                width = null
                height = null
                propertyInfo.reportPropertyDiagnostic(
                    "is defined in incorrect format: `${value}`. Expected: INT or INT:INT.",
                    textSpan,
                    SgfDiagnosticSeverity.Critical,
                )
            }
        }
        return Pair(width, height)
    }

    private fun PropertyValueToken.convertPosition(propertyInfo: SgfPropertyInfo): MoveInfo? {
        return when (value.length) {
            0 -> null // Empty value is treated as pass
            2 -> {
                val x = value[0].convertToCoordinateOrNull()
                val y = value[1].convertToCoordinateOrNull()
                if (x != null && y != null) {
                    MoveInfo(
                        Position(x, y),
                        propertyInfo.getPlayer(),
                        PropertyInfoAndTextSpan(propertyInfo, textSpan),
                    )
                } else {
                    if (x == null) {
                        propertyInfo.reportPropertyDiagnostic(
                            "has incorrect x coordinate `${value[0]}`.",
                            TextSpan(textSpan.start, 1),
                            SgfDiagnosticSeverity.Error,
                        )
                    }
                    if (y == null) {
                        propertyInfo.reportPropertyDiagnostic(
                            "has incorrect y coordinate `${value[1]}`.",
                            TextSpan(textSpan.start + 1, 1),
                            SgfDiagnosticSeverity.Error,
                        )
                    }
                    null
                }
            }
            else -> {
                propertyInfo.reportPropertyDiagnostic(
                    "has incorrect format: `${value}`. Expected: `xy`, where coordinate in [a..zA..Z].",
                    textSpan,
                    SgfDiagnosticSeverity.Error,
                )
                null
            }
        }
    }

    private data class PropertyInfoAndTextSpan(val propertyInfo: SgfPropertyInfo, val textSpan: TextSpan)

    private fun Char.convertToCoordinateOrNull(): Int? {
        return when {
            this >= 'a' && this <= 'z' -> this - 'a' + Field.OFFSET
            this >= 'A' && this <= 'Z' -> this - 'A' + ('z' - 'a' + 1) + Field.OFFSET
            else -> null
        }
    }

    private fun SgfPropertyInfo.reportPropertyDiagnostic(message: String, textSpan: TextSpan, severity: SgfDiagnosticSeverity) {
        val messageWithPropertyInfo = "Property ${getFullName()} $message"
        val lineColumn = textSpan.start.getLineColumn(lineOffsets)
        diagnosticReporter(SgfDiagnostic(messageWithPropertyInfo, lineColumn, severity))
    }

    private fun SgfPropertyInfo.getFullName(): String {
        val propertyKey: String
        val propertyNameInfix: String
        if (isKnown) {
            propertyKey = propertyInfoToKey.getValue(this)
            propertyNameInfix = " ($name)"
        } else {
            propertyKey = name
            propertyNameInfix = ""
        }
        return "$propertyKey$propertyNameInfix"
    }

    private fun reportDiagnostic(message: String, textSpan: TextSpan) {
        diagnosticReporter(SgfDiagnostic(message, textSpan.start.getLineColumn(lineOffsets), SgfDiagnosticSeverity.Error))
    }
}

data class SgfDiagnostic(
    val message: String,
    val lineColumn: LineColumn,
    val severity: SgfDiagnosticSeverity,
) {
    override fun toString(): String {
        return "$severity at $lineColumn: $message"
    }
}

enum class SgfDiagnosticSeverity {
    Warning,
    Error,
    Critical,
}
