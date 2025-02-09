package org.dots.game.sgf

import org.dots.game.core.Field
import org.dots.game.core.Game
import org.dots.game.core.GameInfo
import org.dots.game.core.GameTree
import org.dots.game.core.MoveInfo
import org.dots.game.core.MoveResult
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
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_MOVE
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_MOVE
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

    private fun convert(): List<Game> {
        if (sgf.gameTree.isEmpty()) {
            reportDiagnostic("Empty game trees.", sgf.textSpan, SgfDiagnosticSeverity.Warning)
        }

        return buildList {
            for (sgfGameTree in sgf.gameTree) {
                convertGameTree(sgfGameTree, gameTree = null, topLevel = true)?.let { add(it) }
            }
        }
    }

    private fun convertGameTree(sgfGameTree: SgfGameTree, gameTree: GameTree?, topLevel: Boolean): Game? {
        if (topLevel && sgfGameTree.nodes.isEmpty()) {
            reportDiagnostic("Root node with game info is missing.", TextSpan(sgfGameTree.lParen.textSpan.end, 0), SgfDiagnosticSeverity.Error)
        }

        var game: Game? = null
        var initializedGameTree: GameTree? = gameTree
        val movesInfoToResult = mutableListOf<Pair<MoveInfo, MoveResult?>>()

        for ((index, sgfNode) in sgfGameTree.nodes.withIndex()) {
            val isRootScope = topLevel && index == 0
            val (convertedProperties, hasCriticalError) = convertProperties(sgfNode, isRootScope = isRootScope)
            if (isRootScope) {
                require(gameTree == null)
                game = convertGameInfo(sgfNode, convertedProperties, hasCriticalError)
                initializedGameTree = game?.gameTree
            }
            if (initializedGameTree != null) {
                movesInfoToResult.convertMovesInfo(sgfNode, initializedGameTree, convertedProperties)
            }
        }

        if (initializedGameTree != null) {
            movesInfoToResult.rollbackMoves(initializedGameTree)

            for (childGameTree in sgfGameTree.childrenGameTrees) {
                require(convertGameTree(childGameTree, initializedGameTree, topLevel = false) == null)
            }
        }

        return game
    }

    private fun convertGameInfo(node: SgfNode, gameInfoProperties: MutableMap<String, SgfProperty<*>>, hasCriticalError: Boolean): Game? {
        var hasCriticalError = hasCriticalError

        // Report only properties that should be specified
        gameInfoProperties.reportPropertyIfNotSpecified(GAME_MODE_KEY, node, SgfDiagnosticSeverity.Error)
        gameInfoProperties.reportPropertyIfNotSpecified(FILE_FORMAT_KEY, node, SgfDiagnosticSeverity.Error)

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
            gameInfoProperties.reportPropertyIfNotSpecified(SIZE_KEY, node, SgfDiagnosticSeverity.Critical)
            hasCriticalError = true
        }

        if (hasCriticalError || width == null || height == null) return null

        val initialMoves = buildList {
            addAll(gameInfoProperties.getPropertyValue<List<MoveInfo>>(PLAYER1_ADD_DOTS_KEY) ?: emptyList())
            for (player2InitialMoveInfo in (gameInfoProperties.getPropertyValue<List<MoveInfo>>(PLAYER2_ADD_DOTS_KEY) ?: emptyList())) {
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
            gameName = gameInfoProperties.getPropertyValue(GAME_NAME_KEY),
            player1Name = gameInfoProperties.getPropertyValue(PLAYER1_NAME_KEY),
            player1Rating = gameInfoProperties.getPropertyValue(PLAYER1_RATING_KEY),
            player1Team = gameInfoProperties.getPropertyValue(PLAYER1_TEAM_KEY),
            player2Name = gameInfoProperties.getPropertyValue(PLAYER2_NAME_KEY),
            player2Rating = gameInfoProperties.getPropertyValue(PLAYER2_RATING_KEY),
            player2Team = gameInfoProperties.getPropertyValue(PLAYER2_TEAM_KEY),
            komi = gameInfoProperties.getPropertyValue(KOMI_KEY),
            date = gameInfoProperties.getPropertyValue(DATE_KEY),
            description = gameInfoProperties.getPropertyValue(GAME_COMMENT_KEY),
            comment = gameInfoProperties.getPropertyValue(COMMENT_KEY),
            place = gameInfoProperties.getPropertyValue(PLACE_KEY),
            event = gameInfoProperties.getPropertyValue(EVENT_KEY),
            opening = gameInfoProperties.getPropertyValue(OPENING_KEY),
            annotator = gameInfoProperties.getPropertyValue(ANNOTATOR_KEY),
            copyright = gameInfoProperties.getPropertyValue(COPYRIGHT_KEY),
            source = gameInfoProperties.getPropertyValue(SOURCE_KEY),
            time = gameInfoProperties.getPropertyValue(TIME_KEY),
            overtime = gameInfoProperties.getPropertyValue(OVERTIME_KEY),
            appInfo = gameInfoProperties.getPropertyValue(APP_INFO_KEY),
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

    private fun MutableList<Pair<MoveInfo, MoveResult?>>.convertMovesInfo(
        node: SgfNode,
        gameTree: GameTree,
        convertedProperties: MutableMap<String, SgfProperty<*>>,
    ) {
        val player1Moves = convertedProperties.getPropertyValue<List<MoveInfo>>(PLAYER1_MOVE)
        val player2Moves = convertedProperties.getPropertyValue<List<MoveInfo>>(PLAYER2_MOVE)

        fun processMoves(moveInfos: List<MoveInfo>?) {
            if (moveInfos == null) return

            for (moveInfo in moveInfos) {
                val moveResult = gameTree.field.makeMove(moveInfo.position, moveInfo.player)
                if (moveResult != null) {
                    gameTree.add(moveResult)
                } else {
                    // TODO: report a error
                }
                add(moveInfo to moveResult)
            }
        }

        processMoves(player1Moves)
        processMoves(player2Moves)
    }

    private fun MutableList<Pair<MoveInfo, MoveResult?>>.rollbackMoves(initializedGameTree: GameTree) {
        for ((_, moveResult) in this) {
            if (moveResult != null) {
                require(initializedGameTree.back())
            }
        }
    }

    private fun convertProperties(node: SgfNode, isRootScope: Boolean): Pair<MutableMap<String, SgfProperty<*>>, Boolean> {
        val properties = mutableMapOf<String, SgfProperty<*>>()
        var hasCriticalError = false

        for (property in node.properties) {
            val propertyIdentifier = property.identifier.value

            val existingProperty = properties[propertyIdentifier]
            val (sgfProperty, reportedCriticalError) = property.convert()
            val sgfPropertyInfo = sgfProperty.info
            if (existingProperty == null) {
                hasCriticalError = hasCriticalError or reportedCriticalError
                properties[propertyIdentifier] = sgfProperty
            } else if (sgfPropertyInfo.isKnown) {
                sgfPropertyInfo.reportPropertyDiagnostic(
                    "is duplicated and ignored.",
                    property.textSpan,
                    SgfDiagnosticSeverity.Warning
                )
            }

            if (isRootScope && sgfPropertyInfo.scope == SgfPropertyScope.Move ||
                    !isRootScope && sgfPropertyInfo.scope == SgfPropertyScope.Root
            ) {
                val currentScope: SgfPropertyScope
                val severity: SgfDiagnosticSeverity
                val messageSuffix: String
                if (isRootScope) {
                    currentScope = SgfPropertyScope.Root
                    severity = SgfDiagnosticSeverity.Warning
                    messageSuffix = ""
                } else {
                    currentScope = SgfPropertyScope.Move
                    severity = SgfDiagnosticSeverity.Error
                    messageSuffix = " The value is ignored."
                }
                sgfPropertyInfo.reportPropertyDiagnostic(
                    "declared in $currentScope scope, but should be declared in ${sgfPropertyInfo.scope} scope.$messageSuffix",
                    property.textSpan,
                    severity,
                )
            }
        }

        return properties to hasCriticalError
    }

    private fun Map<String, SgfProperty<*>>.reportPropertyIfNotSpecified(
        propertyKey: String,
        node: SgfNode,
        severity: SgfDiagnosticSeverity
    ) {
        if (this[propertyKey] == null) {
            propertyInfos.getValue(propertyKey).reportPropertyDiagnostic(
                "should be specified.",
                TextSpan(node.semicolon.textSpan.end, 0),
                severity
            )
        }
    }

    private fun <T> Map<String, SgfProperty<*>>.getPropertyValue(propertyKey: String): T? {
        @Suppress("UNCHECKED_CAST")
        return this[propertyKey]?.value as? T
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

    private fun reportDiagnostic(message: String, textSpan: TextSpan, severity: SgfDiagnosticSeverity) {
        diagnosticReporter(SgfDiagnostic(message, textSpan.start.getLineColumn(lineOffsets), severity))
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
