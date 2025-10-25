package org.dots.game.sgf

import org.dots.game.Diagnostic
import org.dots.game.DiagnosticSeverity
import org.dots.game.ParsedNode
import org.dots.game.core.AppInfo
import org.dots.game.core.AppType
import org.dots.game.core.BaseMode
import org.dots.game.core.EndGameKind
import org.dots.game.core.Field
import org.dots.game.core.Game
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.GameIsAlreadyOverIllegalMove
import org.dots.game.core.GameProperty
import org.dots.game.core.GameResult
import org.dots.game.core.GameTree
import org.dots.game.core.GameTreeNode
import org.dots.game.core.Games
import org.dots.game.core.Label
import org.dots.game.core.LegalMove
import org.dots.game.core.MoveInfo
import org.dots.game.core.NoLegalMoves
import org.dots.game.core.Player
import org.dots.game.core.PosIsOccupiedIllegalMove
import org.dots.game.core.PosOutOfBoundsIllegalMove
import org.dots.game.core.PositionXY
import org.dots.game.core.PropertiesHolder
import org.dots.game.core.PropertiesMap
import org.dots.game.core.Rules
import org.dots.game.core.SuicidalIllegalMove
import org.dots.game.sgf.SgfGameMode.Companion.SUPPORTED_GAME_MODE_KEY
import org.dots.game.sgf.SgfGameMode.Companion.SUPPORTED_GAME_MODE_NAME
import org.dots.game.sgf.SgfMetaInfo.FILE_FORMAT_KEY
import org.dots.game.sgf.SgfMetaInfo.GAME_MODE_KEY
import org.dots.game.sgf.SgfMetaInfo.GROUNDING_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.HANDICAP_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_MARKER
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_MARKER
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.RESIGN_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.RESULT_KEY
import org.dots.game.sgf.SgfMetaInfo.SIZE_KEY
import org.dots.game.sgf.SgfMetaInfo.TIME_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.UNKNOWN_WIN_GAME_RESULT
import org.dots.game.sgf.SgfMetaInfo.sgfPropertyInfoToKey
import org.dots.game.sgf.SgfMetaInfo.propertyInfos
import org.dots.game.toNeatNumber
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.measureTime

class SgfConverter(
    val sgf: SgfRoot,
    val warnOnMultipleGames: Boolean = false,
    val useEndingMove: Boolean = true,
    val diagnosticReporter: (Diagnostic) -> Unit,
) {
    companion object {
        internal const val LOWER_CHAR_OFFSET = 'a' - Field.OFFSET
        internal const val UPPER_CHAR_OFFSET = 'A' - ('z' - 'a' + 1) - Field.OFFSET
        private const val EXTRA_MOVE_INFO_INDEX = 3
        private const val GAME_RESULT_DESCRIPTION_SUFFIX = "is Number, $RESIGN_WIN_GAME_RESULT (Resign), $TIME_WIN_GAME_RESULT (Time) or $UNKNOWN_WIN_GAME_RESULT (Unknown)"

        /**
         * Returns `null` if a critical error occurs:
         *   * Unsupported file format (FF)
         *   * Unsupported mode (not Kropki)
         *   * Incorrect or unspecified size
         */
        fun convert(sgf: SgfRoot, warnOnMultipleGames: Boolean = false, addFinishingMove: Boolean = true, diagnosticReporter: (Diagnostic) -> Unit): Games {
            return SgfConverter(sgf, warnOnMultipleGames, addFinishingMove, diagnosticReporter).convert()
        }
    }

    private fun TextSpan.getText() = sgf.text.substring(start, end)

    private var capturingIsCalculatedAutomaticallyIsReported: Boolean = false

    var fieldTime: Duration = Duration.ZERO
        private set

    fun convert(): Games {
        capturingIsCalculatedAutomaticallyIsReported = false

        if (sgf.gameTree.isEmpty()) {
            reportDiagnostic("Empty game trees.", sgf.textSpan, DiagnosticSeverity.Warning)
        }

        return Games(sgf).apply {
            for ((index, sgfGameTree) in sgf.gameTree.withIndex()) {
                convertGameTree(sgfGameTree, mainBranch = true, game = null, rootConverterProperties = null)?.let { add(it) }

                if (warnOnMultipleGames && index > 0) {
                    reportDiagnostic("Only single game is supported. Other games will be ignored.", sgfGameTree.textSpan, DiagnosticSeverity.Warning)
                }
            }
        }
    }

    private fun convertGameTree(
        sgfGameTree: SgfGameTree,
        mainBranch: Boolean,
        game: Game?,
        rootConverterProperties: Map<String, SgfProperty<*>>?,
    ): Game? {
        val root = mainBranch && game == null
        if (root && sgfGameTree.nodes.isEmpty()) {
            reportDiagnostic("Root node with game info is missing.", TextSpan(sgfGameTree.lParen.textSpan.end, 0), DiagnosticSeverity.Error)
        }

        var initializedGame: Game? = game
        var initializedRootConverterProperties: Map<String, SgfProperty<*>>? = rootConverterProperties
        var rollbackNode: GameTreeNode? = initializedGame?.gameTree?.currentNode

        for ((index, sgfNode) in sgfGameTree.nodes.withIndex()) {
            val isRootScope = root && index == 0
            val (convertedProperties, hasCriticalError) = convertProperties(sgfNode, isRootScope = isRootScope)
            if (isRootScope) {
                require(initializedGame == null)
                require(initializedRootConverterProperties == null)
                initializedGame = convertGameInfo(sgfGameTree, sgfNode, convertedProperties, hasCriticalError)
                initializedRootConverterProperties = convertedProperties
                rollbackNode = initializedGame?.gameTree?.rootNode
            } else if (initializedGame?.gameTree != null) {
                convertMovesInfo(initializedGame.gameTree, convertedProperties, sgfNode)
            }
        }

        if (initializedGame != null) {
            if (mainBranch && sgfGameTree.childrenGameTrees.isEmpty() && initializedRootConverterProperties != null) {
                initializedRootConverterProperties.finishAndValidateGameResult(initializedGame, sgfGameTree)
            }

            for ((index, childGameTree) in sgfGameTree.childrenGameTrees.withIndex()) {
                convertGameTree(
                    childGameTree,
                    mainBranch = mainBranch && index == 0,
                    initializedGame,
                    initializedRootConverterProperties
                )
            }

            fieldTime += measureTime {
                initializedGame.gameTree.switch(rollbackNode)
            }
        }

        return initializedGame
    }

    private fun Map<String, SgfProperty<*>>.finishAndValidateGameResult(game: Game, currentGameTree: SgfGameTree) {
        val definedGameResult = game.result
        val gameTree = game.gameTree
        val field = gameTree.field

        val gameResultProperty by lazy(LazyThreadSafetyMode.NONE) { getValue(RESULT_KEY) }

        val handicapProperty = get(HANDICAP_KEY)
        if (handicapProperty != null) {
            val handicapValueFromGameInfo = handicapProperty.value as? Int
            if (handicapValueFromGameInfo != null) {
                val extraBlackDotsCount = field.moveSequence.drop(field.initialMovesCount).takeWhile { it.player == Player.First }.count()
                val remainingFirstPlayerInitDotsCount = game.remainingInitMoves.count { it.player == Player.First }
                val remainingSecondPlayerInitDotsCount = game.remainingInitMoves.count { it.player == Player.Second }
                val handicapFromField =
                    remainingFirstPlayerInitDotsCount - remainingSecondPlayerInitDotsCount + extraBlackDotsCount
                if ((handicapValueFromGameInfo > 1 || handicapFromField > 1) && handicapValueFromGameInfo != handicapFromField) {
                    handicapProperty.info.reportPropertyDiagnostic(
                        "has `$handicapValueFromGameInfo` value but expected value from field is `$handicapFromField`",
                        handicapProperty.node.value.first().textSpan,
                        DiagnosticSeverity.Warning
                    )
                }
            }
        }

        if (definedGameResult is GameResult.Draw) {
            // Check if the game is not automatically over because of no legal moves or a grounding move
            if (useEndingMove && field.gameResult == null) {
                fieldTime += measureTime {
                    gameTree.addChild(
                        MoveInfo.createFinishingMove(definedGameResult.player, ExternalFinishReason.Draw)
                    )
                }
            }

            val actualWinner = (field.gameResult as? GameResult.WinGameResult)?.winner
            if (actualWinner != null) {
                gameResultProperty.info.reportPropertyDiagnostic(
                    "has Draw value but the result of the game from field: $actualWinner wins.",
                    TextSpan(currentGameTree.textSpan.end, 0),
                    DiagnosticSeverity.Warning,
                )
            }
        } else if (definedGameResult is GameResult.WinGameResult) {
            val definedFinishReason = definedGameResult.toExternalFinishReason()
            val definedWinner = definedGameResult.winner

            // TODO: report warning when `externalFinishReason` is null (no legal moves),
            //  but actual field result is also null (legal moves still exist)
            // Check if the game is not automatically over because of no legal moves of a grounding move
            if (useEndingMove && field.gameResult == null && definedFinishReason != null) {
                // In case of grounding, rely on the last player's move; otherwise it's a move of looser
                val activePlayer = definedGameResult.player.takeIf { it != Player.None }
                    ?: if (definedFinishReason == ExternalFinishReason.Grounding)
                        field.getCurrentPlayer()
                    else
                        definedWinner.opposite()
                fieldTime += measureTime {
                    gameTree.addChild(
                        MoveInfo.createFinishingMove(activePlayer, definedFinishReason)
                    )
                }
            }

            val actualWinner = if (field.gameResult != null) {
                (field.gameResult as? GameResult.WinGameResult)?.winner
            } else {
                definedWinner
            }

            if (definedWinner != actualWinner) {
                gameResultProperty.info.reportPropertyDiagnostic(
                    "has `${definedWinner}` player as winner but the result of the game from field: ${actualWinner?.let { "$it wins" } ?: "Draw/Unknown"}.",
                    TextSpan(currentGameTree.textSpan.end, 0),
                    DiagnosticSeverity.Warning,
                )
            } else {
                // Don't check for GROUND because typical SGF (notago) doesn't contain info about score in case of grounding
                if (definedGameResult is GameResult.ScoreWin && definedFinishReason != ExternalFinishReason.Grounding) {
                    val definedGameScore = definedGameResult.score
                    val scoreFromField = game.gameTree.field.getScoreDiff(definedWinner)
                    if (definedGameScore != scoreFromField) {
                        gameResultProperty.info.reportPropertyDiagnostic(
                            "has value `${definedGameScore.toNeatNumber()}` that doesn't match score from game field `${scoreFromField.toNeatNumber()}`.",
                            TextSpan(currentGameTree.textSpan.end, 0),
                            DiagnosticSeverity.Warning
                        )
                    }
                }
            }
        }
    }

    private fun convertGameInfo(
        sgfGameTree: SgfGameTree,
        sgfNode: SgfNode,
        gameInfoProperties: Map<String, SgfProperty<*>>,
        hasCriticalError: Boolean
    ): Game? {
        var hasCriticalError = hasCriticalError

        // Report only properties that should be specified
        gameInfoProperties.reportPropertyIfNotSpecified(GAME_MODE_KEY, sgfNode, DiagnosticSeverity.Error)
        gameInfoProperties.reportPropertyIfNotSpecified(FILE_FORMAT_KEY, sgfNode, DiagnosticSeverity.Error)

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
            gameInfoProperties.reportPropertyIfNotSpecified(SIZE_KEY, sgfNode, DiagnosticSeverity.Critical)
            hasCriticalError = true
        }

        if (hasCriticalError || width == null || height == null) return null

        val initialMoves = buildList {
            addAll(gameInfoProperties.getPropertyValue<List<MoveInfo>>(PLAYER1_ADD_DOTS_KEY) ?: emptyList())
            for (player2InitialMoveInfo in (gameInfoProperties.getPropertyValue<List<MoveInfo>>(PLAYER2_ADD_DOTS_KEY)
                ?: emptyList())) {
                if (removeAll { it.positionXY == player2InitialMoveInfo.positionXY }) {
                    val (propertyInfo, textSpan) = player2InitialMoveInfo.parsedNode as PropertyInfoAndTextSpan
                    propertyInfo.reportPropertyDiagnostic(
                        "value `${textSpan.getText()}` overwrites one the previous position of first player ${
                            propertyInfos.getValue(PLAYER1_ADD_DOTS_KEY).getFullName()
                        }.",
                        textSpan,
                        DiagnosticSeverity.Warning,
                    )
                }
                add(player2InitialMoveInfo)
            }
        }

        val gameProperties = transformSgfToGameProperties(gameInfoProperties)

        var baseMode = Rules.Standard.baseMode
        var suicideAllowed = Rules.Standard.suicideAllowed
        var initPosIsRandom: Boolean? = null

        val rulesProperty = gameInfoProperties[SgfMetaInfo.RULES_KEY]
        if ((gameInfoProperties[SgfMetaInfo.APP_INFO_KEY]?.value as? AppInfo)?.appType == AppType.Katago) {
            rulesProperty?.let {
                val kataGoExtraRules = tryParseKataGoExtraRules(it)
                if (kataGoExtraRules != null) {
                    baseMode = if (kataGoExtraRules.dotsCaptureEmptyBase) BaseMode.AnySurrounding else Rules.Standard.baseMode
                    suicideAllowed = kataGoExtraRules.sui
                    initPosIsRandom = kataGoExtraRules.startPosIsRandom
                }
            }
        }

        val (rules, remainingInitMoves) = Rules.createAndDetectInitPos(
            width,
            height,
            captureByBorder = false,
            baseMode = baseMode,
            suicideAllowed = suicideAllowed,
            initialMoves = initialMoves,
            komi = gameProperties[Game::komi]?.value as? Double ?: 0.0
        )
        if (rules.initPosIsRandom && initPosIsRandom == false) {
            rulesProperty?.info?.reportPropertyDiagnostic(
                "Random `${rules.initPosType}` is detected but strict is expected according to extra rules.",
                rulesProperty.node.textSpan,
                DiagnosticSeverity.Warning,
            )
        }
        val field = Field.create(rules) { moveInfo, withinBounds, currentMoveNumber ->
            moveInfo.reportPositionThatViolatesRules(withinBounds, width, height, currentMoveNumber)
        }

        val gameTree = GameTree(field, sgfNode).also { it.memoizePaths = false }

        return Game(gameTree, gameProperties, sgfGameTree, remainingInitMoves)
    }

    private fun convertMovesInfo(
        gameTree: GameTree,
        convertedProperties: Map<String, SgfProperty<*>>,
        sgfNode: SgfNode,
    ) {
        val field = gameTree.field

        val gameProperties = transformSgfToGameProperties(convertedProperties)

        fieldTime += measureTime {
            gameTree.addChild(gameProperties, sgfNode) { moveInfo, moveResult ->
                when (moveResult) {
                    is GameIsAlreadyOverIllegalMove -> {
                        val (propertyInfo, textSpan) = moveInfo.parsedNode as PropertyInfoAndTextSpan
                        propertyInfo.reportPropertyDiagnostic(
                            "is defined (`${textSpan.getText()}`), however the game is already over with the result: ${field.gameResult}",
                            textSpan,
                            DiagnosticSeverity.Error
                        )
                    }

                    is PosOutOfBoundsIllegalMove -> {
                        moveInfo.reportPositionThatViolatesRules(
                            withinBounds = false,
                            field.width,
                            field.height,
                            field.currentMoveNumber
                        )
                    }

                    is PosIsOccupiedIllegalMove,
                    is SuicidalIllegalMove,
                    NoLegalMoves -> {
                        moveInfo.reportPositionThatViolatesRules(
                            withinBounds = true,
                            field.width,
                            field.height,
                            field.currentMoveNumber
                        )
                    }

                    else -> {
                        require(moveResult is LegalMove)
                    }
                }
            }
        }
    }

    private fun transformSgfToGameProperties(gameInfoProperties: Map<String, SgfProperty<*>>): PropertiesMap {
        return mutableMapOf<KProperty<*>, GameProperty<*>>().apply {
            for (sgfProperty in gameInfoProperties.values) {
                val gameProperty = this[sgfProperty.info.gameInfoProperty]
                this[sgfProperty.info.gameInfoProperty] = if (gameProperty == null) {
                    GameProperty(sgfProperty.value, listOf(sgfProperty.node))
                } else {
                    GameProperty(gameProperty.value, gameProperty.parsedNodes + sgfProperty.node)
                }
            }
        }
    }

    private fun MoveInfo.reportPositionThatViolatesRules(withinBounds: Boolean, width: Int, height: Int, currentMoveNumber: Int) {
        val (propertyInfo, textSpan) = parsedNode as PropertyInfoAndTextSpan
        val errorMessageSuffix = if (!withinBounds) {
            "The position $positionXY is out of bounds $width:$height"
        } else {
            "The dot at position $positionXY is already placed or captured"
        }
        propertyInfo.reportPropertyDiagnostic(
            "has incorrect value `${textSpan.getText()}`. $errorMessageSuffix (move number: ${currentMoveNumber + 1}).",
            textSpan,
            DiagnosticSeverity.Error
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
                    DiagnosticSeverity.Warning
                )
            }

            if (isRootScope && sgfPropertyInfo.scope == SgfPropertyScope.Move ||
                !isRootScope && sgfPropertyInfo.scope == SgfPropertyScope.Root
            ) {
                val currentScope: SgfPropertyScope
                val severity: DiagnosticSeverity
                val messageSuffix: String
                if (isRootScope) {
                    currentScope = SgfPropertyScope.Root
                    severity = DiagnosticSeverity.Warning
                    messageSuffix = ""
                } else {
                    currentScope = SgfPropertyScope.Move
                    severity = DiagnosticSeverity.Error
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
        severity: DiagnosticSeverity
    ) {
        if (this[propertyKey] == null) {
            propertyInfos.getValue(propertyKey).reportPropertyDiagnostic(
                "should be specified.",
                TextSpan(node.semicolon.textSpan.end, 0),
                severity
            )
        }
    }

    private fun SgfPropertyNode.convert(): Pair<SgfProperty<*>, Boolean> {
        var reportedCriticalError = false

        val propertyIdentifier = identifier.value
        val propertyInfo = propertyInfos[propertyIdentifier] ?: SgfPropertyInfo(
            propertyIdentifier,
            PropertiesHolder::unknownProperties,
            SgfPropertyType.Text,
            multipleValues = true,
            scope = SgfPropertyScope.Both,
            isKnown = false,
        )

        if (propertyInfo.isKnown) {
            if (value.isEmpty()) {
                propertyInfo.reportPropertyDiagnostic(
                    "is unspecified.",
                    TextSpan(textSpan.end, 0),
                    if (propertyIdentifier == SIZE_KEY) DiagnosticSeverity.Critical else DiagnosticSeverity.Error
                )
                reportedCriticalError = propertyIdentifier == SIZE_KEY
            }
        } else {
            propertyInfo.reportPropertyDiagnostic(
                "is unknown.",
                identifier.textSpan,
                DiagnosticSeverity.Warning,
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
                                    DiagnosticSeverity.Warning,
                                )
                            }
                            false
                        }
                    }

                    intValue
                }

                SgfPropertyType.Double -> {
                    val normalizedValue = if (sgfPropertyInfoToKey.getValue(propertyInfo).let { it  == PLAYER1_RATING_KEY || it == PLAYER2_RATING_KEY }) {
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
                                DiagnosticSeverity.Warning,
                            )
                        }
                    }
                }
                SgfPropertyType.SimpleText -> propertyValue.convertSimpleText()
                SgfPropertyType.Text -> propertyValue.convertText()
                SgfPropertyType.Size -> propertyValueToken.convertSize(propertyInfo)
                SgfPropertyType.AppInfo -> propertyValue.convertAppInfo()
                SgfPropertyType.MovePosition -> {
                    propertyValueToken.convertMoveInfo(propertyInfo)?.also { moveInfo ->
                        if (convertedValues.removeAll { (it as MoveInfo).positionXY == moveInfo.positionXY }) {
                            propertyInfo.reportPropertyDiagnostic(
                                "value `${propertyValue}` overwrites one the previous position.",
                                propertyValueToken.textSpan,
                                DiagnosticSeverity.Warning,
                            )
                        }
                    }
                }
                SgfPropertyType.Position -> propertyValueToken.convertPositionXY(propertyInfo)
                SgfPropertyType.Label -> propertyValueToken.convertLabel(propertyInfo)
                SgfPropertyType.GameResult -> propertyValueToken.convertGameResult(propertyInfo)
            }

            convertedValue?.let { convertedValues.add(it) }

            if (index > 0 && !propertyInfo.multipleValues) {
                propertyInfo.reportPropertyDiagnostic(
                    "has duplicated value `$propertyValue` that's ignored.",
                    propertyValueToken.textSpan,
                    DiagnosticSeverity.Warning
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
                if (intValue != null) DiagnosticSeverity.Critical else DiagnosticSeverity.Error,
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
                if (intValue != null) DiagnosticSeverity.Critical else DiagnosticSeverity.Error,
            )
            return intValue != null
        }
        return false
    }

    private fun PropertyValueToken.convertSize(propertyInfo: SgfPropertyInfo): Pair<Int?, Int?> {
        val dimensions = value.split(':')
        val width: Int?
        val height: Int?

        fun reportZeroSize(sizeSuffix: String, valueStart: Int, valueLength: Int) {
            propertyInfo.reportPropertyDiagnostic(
                "has zero $sizeSuffix.",
                TextSpan(valueStart, valueLength),
                DiagnosticSeverity.Warning,
            )
        }

        when (dimensions.size) {
            1 -> {
                val size = dimensions[0].toIntOrNull()?.takeIf { it in 0..Field.MAX_SIZE }
                if (size == null) {
                    width = null
                    height = null
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid value `${dimensions[0]}`. Expected: 0..${Field.MAX_SIZE}.",
                        textSpan,
                        DiagnosticSeverity.Critical,
                    )
                } else {
                    if (size == 0) {
                        reportZeroSize("value", textSpan.start, dimensions[0].length)
                    }
                    width = size
                    height = size
                }
            }
            2 -> {
                val widthString = dimensions[0]
                val heightString = dimensions[1]

                width = widthString.toIntOrNull()?.takeIf { Field.checkSize(it) }
                if (width == null) {
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid width: `${widthString}`. Expected: 0..${Field.MAX_SIZE}.",
                        TextSpan(textSpan.start, widthString.length),
                        DiagnosticSeverity.Critical,
                    )
                } else if (width == 0) {
                    reportZeroSize("width", textSpan.start, widthString.length)
                }
                height = heightString.toIntOrNull()?.takeIf { Field.checkSize(it) }
                if (height == null) {
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid height: `${heightString}`. Expected: 0..${Field.MAX_SIZE}.",
                        TextSpan(textSpan.start + widthString.length + 1, heightString.length),
                        DiagnosticSeverity.Critical,
                    )
                } else if (height == 0) {
                    reportZeroSize("height", textSpan.start + widthString.length + 1, heightString.length)
                }
            }
            else -> {
                width = null
                height = null
                propertyInfo.reportPropertyDiagnostic(
                    "is defined in incorrect format: `${value}`. Expected: INT or INT:INT.",
                    textSpan,
                    DiagnosticSeverity.Critical,
                )
            }
        }

        return width to height
    }

    private fun PropertyValueToken.convertMoveInfo(propertyInfo: SgfPropertyInfo): MoveInfo? {
        val propertyInfoAndTextSpan = PropertyInfoAndTextSpan(propertyInfo, TextSpan(textSpan.start, value.length))

        val externalFinishReason = if (value.isEmpty()) {
            ExternalFinishReason.Grounding
        } else {
            ExternalFinishReason.textToValue[value]
        }

        return if (externalFinishReason != null) {
            MoveInfo.createFinishingMove(propertyInfo.getPlayer(), externalFinishReason, propertyInfoAndTextSpan)
        } else {
            val positionXY = convertCoordinates(propertyInfo, 0).toPositionXY()

            if (value.elementAtOrNull(EXTRA_MOVE_INFO_INDEX - 1) == '.') {
                if (!capturingIsCalculatedAutomaticallyIsReported) {
                    val capturingMoveInfos = buildList {
                        for (internalIndex in EXTRA_MOVE_INFO_INDEX until value.length step 2) {
                            convertCoordinates(propertyInfo, internalIndex).let { coordinates ->
                                coordinates.toPositionXY()?.let { add(it) }
                            }
                        }
                    }
                    val textSpan =
                        TextSpan(textSpan.start + EXTRA_MOVE_INFO_INDEX, value.length - EXTRA_MOVE_INFO_INDEX)
                    propertyInfo.reportPropertyDiagnostic(
                        "has capturing positions that are not yet supported: ${capturingMoveInfos.joinToString()} (`${textSpan.getText()}`). " +
                                "The capturing is calculated automatically according game rules for this and next cases.",
                        textSpan,
                        DiagnosticSeverity.Warning,
                    )
                    capturingIsCalculatedAutomaticallyIsReported = true
                }
            } else if (value.length > 2) {
                reportCoordinatesExtraCharsIfNeeded(propertyInfo)
            }

            positionXY?.let {
                MoveInfo(it, propertyInfo.getPlayer(), propertyInfoAndTextSpan)
            }
        }
    }

    private fun PropertyValueToken.convertPositionXY(propertyInfo: SgfPropertyInfo): PositionXY? {
        reportCoordinatesExtraCharsIfNeeded(propertyInfo)
        return convertCoordinates(propertyInfo, 0).toPositionXY()
    }

    private fun PropertyValueToken.reportCoordinatesExtraCharsIfNeeded(propertyInfo: SgfPropertyInfo) {
        if (value.length > 2) {
            val textSpan = TextSpan(textSpan.start + 2, value.length - 2)
            propertyInfo.reportPropertyDiagnostic(
                "has incorrect extra chars: `${value.substring(2)}`",
                textSpan,
                DiagnosticSeverity.Error,
            )
        }
    }

    private fun PropertyValueToken.convertLabel(propertyInfo: SgfPropertyInfo): Label? {
        val coordinates = convertCoordinates(propertyInfo, 0)

        val labelText: String =
            if (value.elementAtOrNull(EXTRA_MOVE_INFO_INDEX - 1) == ':') {
                value.subSequence(EXTRA_MOVE_INFO_INDEX, value.length).toString()
            } else {
                propertyInfo.reportPropertyDiagnostic("has unexpected separator `${value.elementAtOrNull(EXTRA_MOVE_INFO_INDEX - 1) ?: ""}`",
                    TextSpan(textSpan.start + EXTRA_MOVE_INFO_INDEX - 1, 1), DiagnosticSeverity.Error)
                ""
            }

        return coordinates.toPositionXY()?.let { Label(it, labelText) }
    }

    private fun PropertyValueToken.convertCoordinates(propertyInfo: SgfPropertyInfo, internalIndex: Int): Coordinates {
        val x = value[internalIndex].convertToCoordinateOrNull() ?: run {
            propertyInfo.reportPropertyDiagnostic(
                "has incorrect x coordinate `${value[0]}`.",
                TextSpan(textSpan.start + internalIndex, 1),
                DiagnosticSeverity.Error,
            )
            null
        }
        val yChar = value.elementAtOrNull(internalIndex + 1)
        val y = yChar?.convertToCoordinateOrNull() ?: run {
            propertyInfo.reportPropertyDiagnostic(
                "has incorrect y coordinate `${yChar ?: ""}`.",
                TextSpan(textSpan.start + internalIndex + 1, yChar?.let { 1 } ?: 0),
                DiagnosticSeverity.Error,
            )
            null
        }
        return Coordinates(x, y)
    }

    private class Coordinates(val x: Int?, val y: Int?) {
        fun toPositionXY(): PositionXY? = if (x != null && y != null) PositionXY(x, y) else null
    }

    private fun PropertyValueToken.convertGameResult(propertyInfo: SgfPropertyInfo): GameResult? {
        // TODO: Calculate score and player based on field (currently the last move in inaccessible here)?

        if (value == "0" || value == "Draw") {
            return GameResult.Draw(endGameKind = null, player = null)
        }

        val player = value.elementAtOrNull(0).let {
            when (it) {
                PLAYER1_MARKER -> Player.First
                PLAYER2_MARKER -> Player.Second
                else -> {
                    propertyInfo.reportPropertyDiagnostic(
                        "has invalid player `${it ?: ""}`. Allowed values: $PLAYER1_MARKER or $PLAYER2_MARKER",
                        TextSpan(textSpan.start, if (it == null) 0 else 1),
                        DiagnosticSeverity.Error,
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
                    DiagnosticSeverity.Error,
                )
                return GameResult.UnknownWin(player)
            }
        }

        fun reportUnknownSuffixIfNeeded() {
            if (value.length > 3) {
                propertyInfo.reportPropertyDiagnostic(
                    "has unexpected suffix `${value.substring(3)}`.",
                    TextSpan(textSpan.start + 3, value.length - 3),
                    DiagnosticSeverity.Error,
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
                    if (resultString.singleOrNull() == GROUNDING_WIN_GAME_RESULT) {
                        GameResult.ScoreWin(0.0, EndGameKind.Grounding, player, player = null)
                    } else {
                        val number = resultString.toDoubleOrNull()
                        if (number != null) {
                            GameResult.ScoreWin(number, endGameKind = null, player, player = null)
                        } else {
                            propertyInfo.reportPropertyDiagnostic(
                                "has invalid result value `$resultString`. Correct value $GAME_RESULT_DESCRIPTION_SUFFIX",
                                TextSpan(textSpan.start + 2, resultString.length),
                                DiagnosticSeverity.Error,
                            )
                            GameResult.UnknownWin(player)
                        }
                    }
                }
            }
        }
    }

    private data class KataGoExtraRules(val dotsCaptureEmptyBase: Boolean, val sui: Boolean, val startPosIsRandom: Boolean)

    private fun tryParseKataGoExtraRules(sgfRulesProperty: SgfProperty<*>): KataGoExtraRules? {
        val propertyInfo = sgfRulesProperty.info
        val sgfPropertyNode = sgfRulesProperty.node

        val propertyValueNode = sgfPropertyNode.value.firstOrNull() ?: return null
        val propertyValueToken = propertyValueNode.propertyValueToken
        val propertyValue = propertyValueNode.propertyValueToken.value

        var currentIndex = 0

        fun tryParseKey(property: KProperty<*>): Boolean {
            val name = property.name
            for (i in 0 until name.length) {
                if (propertyValue.elementAtOrNull(currentIndex + i) != name[i]) {
                    return false
                }
            }
            currentIndex += name.length
            return true
        }

        fun tryParseBooleanValue(): Boolean? {
            when (val element = propertyValue.elementAtOrNull(currentIndex)) {
                '0', '1' -> {
                    currentIndex++
                    return element == '1'
                }
                else -> {
                    val textSpan = TextSpan(propertyValueToken.textSpan.start + currentIndex, if (element == null) 0 else 1)
                    propertyInfo.reportPropertyDiagnostic(
                        "Invalid value `$element`. Expected: `0` or `1`.",
                        textSpan,
                        DiagnosticSeverity.Error
                    )
                    return null
                }
            }
        }

        var dotsCaptureEmptyBase = Rules.Standard.baseMode == BaseMode.AnySurrounding
        var suicideAllowed = Rules.Standard.suicideAllowed
        var startPosIsRandom = Rules.Standard.initPosIsRandom

        while (currentIndex < propertyValue.length) {
            when {
                tryParseKey(KataGoExtraRules::dotsCaptureEmptyBase) -> {
                    val value = tryParseBooleanValue()
                    if (value != null) {
                        dotsCaptureEmptyBase = value
                    } else {
                        break
                    }
                }
                tryParseKey(KataGoExtraRules::sui) -> {
                    val value = tryParseBooleanValue()
                    if (value != null) {
                        suicideAllowed = value
                    } else {
                        break
                    }
                }
                tryParseKey(KataGoExtraRules::startPosIsRandom) -> {
                    val value = tryParseBooleanValue()
                    if (value != null) {
                        startPosIsRandom = value
                    } else {
                        break
                    }
                }
                else -> {
                    val errorTextSpan = TextSpan(propertyValueToken.textSpan.start + currentIndex, propertyValue.length - currentIndex)
                    propertyInfo.reportPropertyDiagnostic(
                        "Unrecognized KataGo key `${errorTextSpan.getText()}`.",
                        errorTextSpan,
                        DiagnosticSeverity.Error
                    )
                    break
                }
            }
        }

        return KataGoExtraRules(dotsCaptureEmptyBase, suicideAllowed, startPosIsRandom)
    }

    private class PropertyInfoAndTextSpan(val propertyInfo: SgfPropertyInfo, textSpan: TextSpan) : ParsedNode(textSpan) {
        operator fun component1(): SgfPropertyInfo = propertyInfo
        operator fun component2(): TextSpan = textSpan
    }

    private fun Char.convertToCoordinateOrNull(): Int? {
        return when (this) {
            in 'a'..'z' -> this - LOWER_CHAR_OFFSET
            in 'A'..'Z' -> this - UPPER_CHAR_OFFSET
            else -> null
        }
    }

    private fun SgfPropertyInfo.reportPropertyDiagnostic(message: String, textSpan: TextSpan, severity: DiagnosticSeverity) {
        val messageWithPropertyInfo = "Property ${getFullName()} $message"
        diagnosticReporter(Diagnostic(messageWithPropertyInfo, textSpan, severity))
    }

    private fun SgfPropertyInfo.getFullName(): String {
        val propertyKey: String
        val propertyNameInfix: String
        if (isKnown) {
            propertyKey = sgfPropertyInfoToKey.getValue(this)
            propertyNameInfix = " ($name)"
        } else {
            propertyKey = name
            propertyNameInfix = ""
        }
        return "$propertyKey$propertyNameInfix"
    }

    private fun reportDiagnostic(message: String, textSpan: TextSpan, severity: DiagnosticSeverity) {
        diagnosticReporter(Diagnostic(message, textSpan, severity))
    }
}
