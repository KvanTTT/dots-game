package org.dots.game.sgf

import org.dots.game.core.Field
import org.dots.game.core.Game
import org.dots.game.core.GameInfo
import org.dots.game.core.GameResult
import org.dots.game.core.GameTree
import org.dots.game.core.MoveInfo
import org.dots.game.core.MoveResult
import org.dots.game.core.Player
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
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_MARKER
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_MOVE_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TIME_LEFT_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_MARKER
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_MOVE_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_TIME_LEFT_KEY
import org.dots.game.sgf.SgfMetaInfo.RESIGN_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.RESULT_KEY
import org.dots.game.sgf.SgfMetaInfo.ROUND_KEY
import org.dots.game.sgf.SgfMetaInfo.SIZE_KEY
import org.dots.game.sgf.SgfMetaInfo.SOURCE_KEY
import org.dots.game.sgf.SgfMetaInfo.TIME_KEY
import org.dots.game.sgf.SgfMetaInfo.TIME_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.UNKNOWN_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.propertyInfoToKey
import org.dots.game.sgf.SgfMetaInfo.propertyInfos
import kotlin.math.abs

class SgfConverter private constructor(val sgf: SgfRoot, val warnOnMultipleGames: Boolean = false, val diagnosticReporter: (SgfDiagnostic) -> Unit) {
    companion object {
        private const val LOWER_CHAR_OFFSET = 'a' - Field.OFFSET
        private const val UPPER_CHAR_OFFSET = 'A' - ('z' - 'a' + 1) - Field.OFFSET
        private const val CAPTURING_POSITIONS_INDEX = 3
        private const val GAME_RESULT_DESCRIPTION_SUFFIX = "is Number, $RESIGN_WIN_GAME_RESULT (Resign), $TIME_WIN_GAME_RESULT (Time) or $UNKNOWN_WIN_GAME_RESULT (Unknown)"

        /**
         * Returns `null` if a critical error occurs:
         *   * Unsupported file format (FF)
         *   * Unsupported mode (not Kropki)
         *   * Incorrect or unspecified size
         */
        fun convert(sgf: SgfRoot, warnOnMultipleGames: Boolean = false, diagnosticReporter: (SgfDiagnostic) -> Unit): List<Game> {
            return SgfConverter(sgf, warnOnMultipleGames, diagnosticReporter).convert()
        }
    }

    private fun TextSpan.getText() = sgf.text.substring(start, end)

    private fun convert(): List<Game> {
        if (sgf.gameTree.isEmpty()) {
            reportDiagnostic("Empty game trees.", sgf.textSpan, SgfDiagnosticSeverity.Warning)
        }

        return buildList {
            for ((index, sgfGameTree) in sgf.gameTree.withIndex()) {
                convertGameTree(sgfGameTree, mainBranch = true, game = null, rootConverterProperties = null)?.let { add(it) }

                if (warnOnMultipleGames && index > 0) {
                    reportDiagnostic("Only single game is supported. Other games will be ignored.", sgfGameTree.textSpan, SgfDiagnosticSeverity.Warning)
                }
            }
        }
    }

    private fun convertGameTree(
        sgfGameTree: SgfGameTree,
        mainBranch: Boolean,
        game: Game?,
        rootConverterProperties: Map<String, SgfProperty<*>>?
    ): Game? {
        val root = mainBranch && game == null
        if (root && sgfGameTree.nodes.isEmpty()) {
            reportDiagnostic("Root node with game info is missing.", TextSpan(sgfGameTree.lParen.textSpan.end, 0), SgfDiagnosticSeverity.Error)
        }

        var initializedGame: Game? = game
        var initializedRootConverterProperties: Map<String, SgfProperty<*>>? = rootConverterProperties
        var movesCount = 0

        for ((index, sgfNode) in sgfGameTree.nodes.withIndex()) {
            val isRootScope = root && index == 0
            val (convertedProperties, hasCriticalError) = convertProperties(sgfNode, isRootScope = isRootScope)
            if (isRootScope) {
                require(initializedGame == null)
                require(initializedRootConverterProperties == null)
                initializedGame = convertGameInfo(sgfNode, convertedProperties, hasCriticalError)
                initializedRootConverterProperties = convertedProperties
            }
            if (initializedGame?.gameTree != null) {
                movesCount += convertMovesInfo(initializedGame.gameTree, convertedProperties)
            }
        }

        if (initializedGame != null) {
            if (mainBranch && sgfGameTree.childrenGameTrees.isEmpty()) {
                initializedRootConverterProperties.validateResultPropertyMatchesFieldResult(initializedGame)
            }

            for ((index, childGameTree) in sgfGameTree.childrenGameTrees.withIndex()) {
                convertGameTree(
                    childGameTree,
                    mainBranch = index == 0,
                    initializedGame,
                    initializedRootConverterProperties
                )
            }

            initializedGame.gameTree.back(movesCount)
        }

        return initializedGame
    }

    private fun Map<String, SgfProperty<*>>?.validateResultPropertyMatchesFieldResult(game: Game) {
        val gameResult = game.gameInfo.result
        if (gameResult is GameResult.ScoreWin) {
            val scoreFromGameResult = gameResult.score.toInt()
            val scoreFromField = abs(game.gameTree.field.getScoreDiff())
            if (scoreFromGameResult != scoreFromField) {
                val gameResultProperty = this!!.getValue(RESULT_KEY)
                gameResultProperty.info.reportPropertyDiagnostic(
                    "has value `${scoreFromGameResult}` that doesn't match score from field `${scoreFromField}`.",
                    gameResultProperty.node.textSpan,
                    SgfDiagnosticSeverity.Warning
                )
            }
        }
    }

    private fun convertGameInfo(node: SgfNode, gameInfoProperties: Map<String, SgfProperty<*>>, hasCriticalError: Boolean): Game? {
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
            for (player2InitialMoveInfo in (gameInfoProperties.getPropertyValue<List<MoveInfo>>(PLAYER2_ADD_DOTS_KEY)
                ?: emptyList())) {
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

        val player1TimeLeft = gameInfoProperties.getPropertyValue<Double>(PLAYER1_TIME_LEFT_KEY)
        val player2TimeLeft = gameInfoProperties.getPropertyValue<Double>(PLAYER2_TIME_LEFT_KEY)

        val gameInfo = GameInfo(
            appInfo = gameInfoProperties.getPropertyValue(APP_INFO_KEY),
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
            result = gameInfoProperties.getPropertyValue(RESULT_KEY),
            round = gameInfoProperties.getPropertyValue(ROUND_KEY),
        )

        val rules = Rules(width, height, initialMoves = initialMoves)
        val field = Field(rules) { moveInfo, withinBounds, currentMoveNumber ->
            moveInfo.reportPositionThatViolatesRules(withinBounds, width, height, currentMoveNumber)
        }

        val gameTree = GameTree(field, player1TimeLeft, player2TimeLeft).also { it.memoizePaths = false }

        return Game(gameInfo, gameTree)
    }

    private fun convertMovesInfo(
        gameTree: GameTree,
        convertedProperties: Map<String, SgfProperty<*>>,
    ): Int {
        val field = gameTree.field
        val player1Moves = convertedProperties.getPropertyValue<List<MoveInfo>>(PLAYER1_MOVE_KEY)
        val player2Moves = convertedProperties.getPropertyValue<List<MoveInfo>>(PLAYER2_MOVE_KEY)
        val player1TimeLeft = convertedProperties.getPropertyValue<Double>(PLAYER1_TIME_LEFT_KEY)
        val player2TimeLeft = convertedProperties.getPropertyValue<Double>(PLAYER2_TIME_LEFT_KEY)
        val comment = convertedProperties.getPropertyValue<String>(COMMENT_KEY)
        var movesCount = 0

        fun processMoves(moveInfos: List<MoveInfo>?) {
            if (moveInfos == null) return

            for (moveInfo in moveInfos) {
                var moveResult: MoveResult?
                val withinBounds: Boolean
                if (field.checkPositionWithinBounds(moveInfo.position)) {
                    moveResult = field.makeMove(moveInfo.position, moveInfo.player)
                    withinBounds = true
                } else {
                    moveResult = null
                    withinBounds = false
                }
                val timeLeft = if (moveInfo.player == Player.First) player1TimeLeft else player2TimeLeft
                gameTree.add(moveResult, timeLeft, comment)
                movesCount++

                if (moveResult == null) {
                    moveInfo.reportPositionThatViolatesRules(
                        withinBounds = withinBounds,
                        field.width,
                        field.height,
                        field.currentMoveNumber
                    )
                }
            }
        }

        processMoves(player1Moves)
        processMoves(player2Moves)

        return movesCount
    }

    private fun MoveInfo.reportPositionThatViolatesRules(withinBounds: Boolean, width: Int, height: Int, currentMoveNumber: Int) {
        val (propertyInfo, textSpan) = extraInfo as PropertyInfoAndTextSpan
        val errorMessageSuffix = if (!withinBounds) {
            "The position $position is out of bounds $width:$height"
        } else {
            "The dot at position $position is already placed or captured"
        }
        propertyInfo.reportPropertyDiagnostic(
            "has incorrect value `${textSpan.getText()}`. $errorMessageSuffix (move number: ${currentMoveNumber + 1}).",
            textSpan,
            SgfDiagnosticSeverity.Error
        )
    }

    private fun convertProperties(node: SgfNode, isRootScope: Boolean): Pair<Map<String, SgfProperty<*>>, Boolean> {
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
            scope = SgfPropertyScope.Both,
            isKnown = false,
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

                SgfPropertyType.Double -> {
                    val normalizedValue = if (propertyInfoToKey.getValue(propertyInfo).let { it  == PLAYER1_RATING_KEY || it == PLAYER2_RATING_KEY }) {
                        // TODO: consider `AppType` (normalization is only actual for Playdots)
                        propertyValue.split(',').last()
                    } else {
                        propertyValue
                    }
                    normalizedValue.toDoubleOrNull().also {
                        if (it == null) {
                            propertyInfo.reportPropertyDiagnostic(
                                "has incorrect format: `${propertyValue}`. Expected: Real Number.",
                                propertyValueToken.textSpan,
                                SgfDiagnosticSeverity.Warning,
                            )
                        }
                    }
                }
                SgfPropertyType.SimpleText -> propertyValue.convertSimpleText()
                SgfPropertyType.Text -> propertyValue.convertText()
                SgfPropertyType.Size -> propertyValueToken.convertSize(propertyInfo)
                SgfPropertyType.AppInfo -> propertyValue.convertAppInfo()
                SgfPropertyType.Position -> {
                    propertyValueToken.convertMoveInfo(propertyInfo)?.also { moveInfo ->
                        if (convertedValues.removeAll { (it as MoveInfo).position == moveInfo.position }) {
                            propertyInfo.reportPropertyDiagnostic(
                                "value `${propertyValue}` overwrites one the previous position.",
                                propertyValueToken.textSpan,
                                SgfDiagnosticSeverity.Warning,
                            )
                        }
                    }
                }
                SgfPropertyType.GameResult -> propertyValueToken.convertGameResult(propertyInfo)
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

        return SgfProperty(propertyInfo, this, resultValue) to reportedCriticalError
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

    private fun PropertyValueToken.convertMoveInfo(propertyInfo: SgfPropertyInfo): MoveInfo? {
        val resultMoveInfo = convertPosition(propertyInfo, 0)

        if (value.elementAtOrNull(CAPTURING_POSITIONS_INDEX - 1) == '.') {
            val capturingMoveInfos = buildList {
                for (internalIndex in CAPTURING_POSITIONS_INDEX until value.length step 2) {
                    convertPosition(propertyInfo, internalIndex)?.let { add(it) }
                }
            }
            val textSpan = TextSpan(textSpan.start + CAPTURING_POSITIONS_INDEX, value.length - CAPTURING_POSITIONS_INDEX)
            propertyInfo.reportPropertyDiagnostic(
                "has capturing positions that are not yet supported: ${capturingMoveInfos.map { it.position }.joinToString()} (`${textSpan.getText()}`). " +
                        "The capturing is calculated automatically according game rules.",
                textSpan,
                SgfDiagnosticSeverity.Warning,
            )
        } else if (value.length > 2) {
            val textSpan = TextSpan(textSpan.start + 2, value.length - 2)
            propertyInfo.reportPropertyDiagnostic(
                "has incorrect extra chars: `${value.substring(2)}`",
                textSpan,
                SgfDiagnosticSeverity.Error,
            )
        }

        return resultMoveInfo
    }

    private fun PropertyValueToken.convertPosition(propertyInfo: SgfPropertyInfo, internalIndex: Int): MoveInfo? {
        if (internalIndex == value.length) {
            return null // Empty value is treated as pass
        }

        val x = value[internalIndex].convertToCoordinateOrNull() ?: run {
            propertyInfo.reportPropertyDiagnostic(
                "has incorrect x coordinate `${value[0]}`.",
                TextSpan(textSpan.start + internalIndex, 1),
                SgfDiagnosticSeverity.Error,
            )
            null
        }
        val yChar = value.elementAtOrNull(internalIndex + 1)
        val y = yChar?.convertToCoordinateOrNull() ?: run {
            propertyInfo.reportPropertyDiagnostic(
                "has incorrect y coordinate `${yChar ?: ""}`.",
                TextSpan(textSpan.start + internalIndex + 1, yChar?.let { 1 } ?: 0),
                SgfDiagnosticSeverity.Error,
            )
            null
        }

        return if (x != null && y != null) {
            val textSpan = TextSpan(textSpan.start + internalIndex, 2)
            MoveInfo(Position(x, y), propertyInfo.getPlayer(), PropertyInfoAndTextSpan(propertyInfo, textSpan))
        }
        else {
            null
        }
    }

    private fun PropertyValueToken.convertGameResult(propertyInfo: SgfPropertyInfo): GameResult? {
        if (value == "0") {
            return GameResult.Draw
        }

        val player = value.elementAtOrNull(0).let {
            when (it) {
                PLAYER1_MARKER -> Player.First
                PLAYER2_MARKER -> Player.Second
                else -> {
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid player `${it ?: ""}`. Allowed values: $PLAYER1_MARKER or $PLAYER2_MARKER",
                        TextSpan(textSpan.start, if (it == null) 0 else 1),
                        SgfDiagnosticSeverity.Error,
                    )
                    return null
                }
            }
        }

        value.elementAtOrNull(1).let {
            if (it != '+') {
                propertyInfo.reportPropertyDiagnostic(
                    "value `$value` is written in invalid format. Correct format is 0 (Draw) or X+Y where X is $PLAYER1_MARKER or $PLAYER2_MARKER, Y $GAME_RESULT_DESCRIPTION_SUFFIX",
                    TextSpan(textSpan.start + 1, if (it == null) 0 else 1),
                    SgfDiagnosticSeverity.Error,
                )
                return GameResult.UnknownWin(player)
            }
        }

        fun reportUnknownSuffixIfNeeded() {
            if (value.length > 3) {
                propertyInfo.reportPropertyDiagnostic(
                    "has unexpected suffix `${value.substring(3)}`.",
                    TextSpan(textSpan.start + 3, value.length - 3),
                    SgfDiagnosticSeverity.Error,
                )
            }
        }

        return value.elementAtOrNull(2).let {
            when (it) {
                RESIGN_WIN_GAME_RESULT -> GameResult.ResignWin(player).also { reportUnknownSuffixIfNeeded() }
                TIME_WIN_GAME_RESULT -> GameResult.TimeWin(player).also { reportUnknownSuffixIfNeeded() }
                UNKNOWN_WIN_GAME_RESULT -> GameResult.UnknownWin(player).also { reportUnknownSuffixIfNeeded() }
                else -> {
                    val resultString = value.substring(2)
                    val number = resultString.toDoubleOrNull()
                    if (number != null) {
                        GameResult.ScoreWin(number, player)
                    } else {
                        propertyInfo.reportPropertyDiagnostic(
                            "has invalid result value `$resultString`. Correct value $GAME_RESULT_DESCRIPTION_SUFFIX",
                            TextSpan(textSpan.start + 2, resultString.length),
                            SgfDiagnosticSeverity.Error,
                        )
                        GameResult.UnknownWin(player)
                    }
                }
            }
        }
    }

    private data class PropertyInfoAndTextSpan(val propertyInfo: SgfPropertyInfo, val textSpan: TextSpan)

    private fun Char.convertToCoordinateOrNull(): Int? {
        return when {
            this >= 'a' && this <= 'z' -> this - LOWER_CHAR_OFFSET
            this >= 'A' && this <= 'Z' -> this - UPPER_CHAR_OFFSET
            else -> null
        }
    }

    private fun SgfPropertyInfo.reportPropertyDiagnostic(message: String, textSpan: TextSpan, severity: SgfDiagnosticSeverity) {
        val messageWithPropertyInfo = "Property ${getFullName()} $message"
        diagnosticReporter(SgfDiagnostic(messageWithPropertyInfo, textSpan, severity))
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
        diagnosticReporter(SgfDiagnostic(message, textSpan, severity))
    }
}
