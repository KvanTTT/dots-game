package org.dots.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.dots.game.core.*
import org.dots.game.views.*
import org.jetbrains.compose.resources.painterResource
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_ai_move
import dotsgame.composeapp.generated.resources.ic_ai_settings
import dotsgame.composeapp.generated.resources.ic_candidate_moves
import dotsgame.composeapp.generated.resources.ic_ground
import dotsgame.composeapp.generated.resources.ic_load_game
import dotsgame.composeapp.generated.resources.ic_new_game
import dotsgame.composeapp.generated.resources.ic_next
import dotsgame.composeapp.generated.resources.ic_ownership
import dotsgame.composeapp.generated.resources.ic_previous
import dotsgame.composeapp.generated.resources.ic_reset
import dotsgame.composeapp.generated.resources.ic_resign
import dotsgame.composeapp.generated.resources.ic_save_as
import dotsgame.composeapp.generated.resources.ic_settings
import org.dots.game.dump.DumpParameters
import org.dots.game.sgf.SgfParsedNode

@Composable
@Preview
fun App(gameSettings: GameSettings = loadClassSettings(GameSettings.Default), onGamesChange: (games: Games?) -> Unit = { }) {
    MaterialTheme {
        var uiSettings by remember { mutableStateOf(loadClassSettings(UiSettings.Standard)) }
        var strings by remember { mutableStateOf(uiSettings.language.getStrings()) }
        var newGameDialogRules by remember { mutableStateOf(loadClassSettings(Rules.Standard)) }
        var openGameSettings by remember { mutableStateOf(loadClassSettings(OpenGameSettings.Default)) }
        var kataGoDotsSettings by remember { mutableStateOf(loadClassSettings(KataGoDotsSettings.Default)) }
        val coroutineScope = rememberCoroutineScope()

        var start by remember { mutableStateOf(true) }
        var reset by remember { mutableStateOf(true) }
        var games by remember { mutableStateOf(Games.fromRules(newGameDialogRules)) }
        var currentGame by remember { mutableStateOf(games.first()) }

        fun getField(): Field = currentGame.gameTree.field
        fun getGameTree(): GameTree = currentGame.gameTree

        var gameTreeViewData: GameTreeViewData by remember { mutableStateOf(GameTreeViewData(currentGame.gameTree)) }

        var currentGameTreeNode by remember { mutableStateOf<GameTreeNode?>(null) }
        var player1Score by remember { mutableStateOf(0.0) }
        var player2Score by remember { mutableStateOf(0.0) }
        var moveNumber by remember { mutableStateOf(0) }
        var showNewGameDialog by remember { mutableStateOf(false) }
        var openGameDialog by remember { mutableStateOf(false) }
        var dumpParameters by remember { mutableStateOf(loadClassSettings(DumpParameters.DEFAULT)) }
        var showSaveGameDialog by remember { mutableStateOf(false) }
        var showUiSettingsForm by remember { mutableStateOf(false) }
        var showKataGoDotsSettingsForm by remember { mutableStateOf(false) }
        var moveMode by remember { mutableStateOf(MoveMode.Next) }

        val focusRequester = remember { FocusRequester() }

        var kataGoDotsEngine by remember { mutableStateOf<KataGoDotsEngine?>(null) }
        var automove by remember { mutableStateOf(kataGoDotsSettings.autoMove) }
        var engineIsCalculating by remember { mutableStateOf(false) }
        var engineCommandsInProgress by remember { mutableStateOf(0) }
        var engineIsAnalyzing by remember { mutableStateOf(false) }
        var moveAnalysis by remember { mutableStateOf<MoveAnalysis?>(null) }

        // A single GTP stream is shared by all the engine commands, thus they must not interleave
        val engineMutex = remember { Mutex() }

        fun updateCurrentNode() {
            val field = getField()
            if (field.rules.komi < 0) {
                player1Score = field.player1Score - field.rules.komi
                player2Score = field.player2Score.toDouble()
            } else {
                player1Score = field.player1Score.toDouble()
                player2Score = field.player2Score + field.rules.komi
            }

            val currentNode = getGameTree().currentNode
            currentGameTreeNode = currentNode
            moveNumber = currentNode.number
        }

        fun updateFieldAndGameTree() {
            updateCurrentNode()

            gameTreeViewData = GameTreeViewData(getGameTree())
        }

        fun switchGame(gameNumber: Int?) {
            gameSettings.game = gameNumber
            currentGame = gameNumber?.let { games.elementAtOrNull(it) } ?: games[0]
            val node = gameSettings.node

            if (currentGame.initialization && node == null) {
                if (openGameSettings.rewindToEnd) {
                    currentGame.gameTree.rewindToEnd()
                }
            } else if (node != null) {
                currentGame.gameTree.trySwitchingByDepthFirstIndex(node)
            }
            currentGame.initialization = false
            currentGame.gameTree.memoizePaths = true

            updateFieldAndGameTree()
        }

        fun reset(newGame: Boolean) {
            if (newGame)
                gameSettings.path = null
            gameSettings.sgf = null
            gameSettings.game = null
            gameSettings.node = null
            reset = true
        }

        if (showNewGameDialog) {
            NewGameDialog(
                newGameDialogRules,
                uiSettings,
                onDismiss = {
                    showNewGameDialog = false
                    focusRequester.requestFocus()
                },
            ) {
                showNewGameDialog = false
                newGameDialogRules = it
                saveClassSettings(newGameDialogRules)
                reset(newGame = true)
            }
        }

        if (start || reset) {
            val contentOrPath = gameSettings.sgf ?: gameSettings.path

            if (contentOrPath == null) {
                games = Games.fromRules(newGameDialogRules)
                onGamesChange(games)
                switchGame(0)
            } else {
                coroutineScope.launch {
                    val loadResult =
                        GameLoader.openOrLoad(
                            contentOrPath,
                            rules = null,
                            addFinishingMove = openGameSettings.addFinishingMove
                        )
                    if (loadResult.games.isNotEmpty()) {
                        games = loadResult.games
                        onGamesChange(games)
                        switchGame(gameSettings.game)
                    }
                }
            }

            if (start) {
                println("Detected platform: $platform")

                coroutineScope.launch {
                    println("Build Info: ${BuildInfo.render()}")

                    if (KataGoDotsEngine.IS_SUPPORTED) {
                        kataGoDotsEngine = KataGoDotsEngine.initialize(kataGoDotsSettings) {
                            println(it)
                        }
                    }
                }
            }

            start = false
            reset = false
        }

        if (openGameDialog) {
            OpenDialog(
                newGameDialogRules,
                openGameSettings,
                uiSettings,
                onDismiss = {
                    openGameDialog = false
                    focusRequester.requestFocus()
                },
                onConfirmation = { newGames, newOpenGameSettings, path, content ->
                    openGameDialog = false
                    openGameSettings = newOpenGameSettings
                    saveClassSettings(openGameSettings)
                    gameSettings.path = path
                    gameSettings.sgf = content
                    gameSettings.game = null
                    gameSettings.node = null
                    games = newGames
                    onGamesChange(games)
                    switchGame(gameSettings.game)
                }
            )
        }

        if (showSaveGameDialog) {
            SaveDialog(
                getField(),
                gameSettings.update(games),
                dumpParameters,
                uiSettings,
                onDismiss = { newDumpParameters, newPath ->
                    showSaveGameDialog = false
                    focusRequester.requestFocus()
                    dumpParameters = newDumpParameters
                    saveClassSettings(newDumpParameters)
                    if (newPath != null) {
                        openGameSettings = openGameSettings.copy(pathOrContent = newPath)
                        saveClassSettings(openGameSettings)
                        gameSettings.path = newPath
                        saveClassSettings(gameSettings.update(games))
                    }
                })
        }

        if (showUiSettingsForm) {
            UiSettingsForm(uiSettings, onUiSettingsChange = {
                uiSettings = it
                strings = uiSettings.language.getStrings()
                saveClassSettings(it)
            }, onDismiss = {
                showUiSettingsForm = false
                focusRequester.requestFocus()
            })
        }

        if (showKataGoDotsSettingsForm) {
            KataGoDotsSettingsForm(kataGoDotsSettings, strings, onSettingsChange = {
                showKataGoDotsSettingsForm = false
                focusRequester.requestFocus()
                kataGoDotsSettings = it.settings
                kataGoDotsEngine = it
                saveClassSettings(it.settings)
            }) {
                showKataGoDotsSettingsForm = false
                focusRequester.requestFocus()
            }
        }

        /**
         * Every engine command evaluates the current position, so nothing may change it until the command is done:
         * neither a move on the field nor a navigation over the game tree.
         * The commands are counted, because an AI move and the analysis may be in progress at the same time.
         */
        suspend fun <T> withFrozenPosition(block: suspend () -> T): T {
            val gameTree = getGameTree()
            engineCommandsInProgress++
            gameTree.disabled = true
            try {
                return block()
            } finally {
                if (--engineCommandsInProgress == 0) {
                    gameTree.disabled = false
                }
            }
        }

        fun makeAIMove() {
            kataGoDotsEngine?.let {
                coroutineScope.launch {
                    engineIsCalculating = true
                    val moveInfo = withFrozenPosition {
                        engineMutex.withLock {
                            it.generateMove(getField(), moveMode.getMovePlayer(getField()))
                        }
                    }
                    engineIsCalculating = false
                    if (moveInfo != null) {
                        getGameTree().addChild(moveInfo)
                        updateFieldAndGameTree()
                    }
                    focusRequester.requestFocus()
                }
            }
        }

        if (uiSettings.analysisEnabled) {
            // Re-evaluate the position every time it changes (or the player to move does), the same way
            // an analysis mode of a Go client does.
            // The displayed options are no keys of the effect, because the analysis of a position has to stay
            // the same no matter which of them is switched on and in which order
            LaunchedEffect(kataGoDotsEngine, currentGame, currentGameTreeNode, moveMode) {
                moveAnalysis = null

                val engine = kataGoDotsEngine ?: return@LaunchedEffect
                val field = getField()
                if (field.isGameOver() || !doesKataSupportRules(field.rules)) return@LaunchedEffect

                engineIsAnalyzing = true
                try {
                    val analysis = withFrozenPosition {
                        engineMutex.withLock {
                            // An interrupted GTP exchange would leave the unread part of the response in the stream
                            // and corrupt every following command, so it's never cancelled in the middle
                            withContext(NonCancellable) {
                                // The ownership is always requested, otherwise the very same position would be
                                // evaluated differently depending on whether it's displayed
                                engine.analyze(field, moveMode.getMovePlayer(field), withOwnership = true)
                            }
                        }
                    }
                    // The position may have changed while the engine was busy; the relaunched effect refreshes it
                    if (isActive) {
                        moveAnalysis = analysis
                    }
                } finally {
                    engineIsAnalyzing = false
                }
            }
        }

        Row(Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type == PointerEventType.Press) {
                        if (event.buttons.isBackPressed) {
                            if (getGameTree().back()) {
                                updateCurrentNode()
                            }
                        } else if (event.buttons.isForwardPressed) {
                            if (getGameTree().next()) {
                                updateCurrentNode()
                            }
                        }
                    }
                }
            }
        }) {
            Column(
                Modifier.padding(5.dp).width(maxFieldSize.width),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    FieldView(currentGameTreeNode, moveMode, getField(), uiSettings, moveAnalysis) { position, player ->
                        getGameTree().addChild(MoveInfo(position.toXY(getField().realWidth), player))
                        updateFieldAndGameTree()

                        if (automove) {
                            makeAIMove()
                        }
                    }
                }
                Row(Modifier.padding(bottom = 10.dp)) {
                    val gameResult = getField().gameResult ?:
                        currentGameTreeNode?.takeIf { it.mainBranch && it.children.isEmpty() }?.let { currentGame.result }
                    GameInfo(currentGame, player1Score, player2Score, gameResult, strings, uiSettings)
                }
                Row {
                    Tooltip(gameSettings.path) {
                        val gameAndMoveInfo = buildString {
                            if (games.size > 1)
                                append("${strings.game}: ${games.indexOf(currentGame) + 1} (${games.size}); ")
                            append("${strings.move}: $moveNumber")
                        }
                        Text(gameAndMoveInfo)
                    }
                }
            }
            Column(Modifier.padding(start = 5.dp)) {
                val rowModifier = Modifier.padding(bottom = 5.dp)
                val playerColorIconModifier =
                    Modifier.size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape)

                Row(rowModifier) {
                    with (strings) {
                        IconButton(Res.drawable.ic_new_game) {
                            showNewGameDialog = true
                        }
                        IconButton(Res.drawable.ic_reset) {
                            reset(newGame = false)
                        }
                        IconButton(Res.drawable.ic_load_game) {
                            openGameDialog = true
                        }
                        IconButton(Res.drawable.ic_save_as) {
                            showSaveGameDialog = true
                        }
                        IconButton(Res.drawable.ic_settings) {
                            showUiSettingsForm = true
                        }

                        if (KataGoDotsEngine.IS_SUPPORTED) {
                            IconButton(Res.drawable.ic_ai_settings) {
                                showKataGoDotsSettingsForm = true
                            }
                        }
                    }
                }

                Row(rowModifier) {
                    Tooltip(strings.nextPlayer) {
                        Button(
                            onClick = {
                                moveMode = MoveMode.Next
                                focusRequester.requestFocus()
                            },
                            defaultButtonModifier,
                            colors = if (moveMode == MoveMode.Next) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                        ) {
                            Box {
                                Box(
                                    modifier = Modifier.offset((-5).dp).size(16.dp)
                                        .border(1.dp, Color.White, CircleShape).clip(CircleShape)
                                        .background(uiSettings.playerFirstColor)
                                )
                                Box(
                                    modifier = Modifier.offset(5.dp).size(16.dp).border(1.dp, Color.White, CircleShape)
                                        .clip(CircleShape).background(uiSettings.playerSecondColor)
                                )
                            }
                        }
                    }
                    Tooltip(strings.firstPlayerDefaultName) {
                        Button(
                            onClick = {
                                moveMode = MoveMode.First
                                focusRequester.requestFocus()
                            },
                            defaultButtonModifier,
                            colors = if (moveMode == MoveMode.First) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                        ) {
                            Box(
                                modifier = playerColorIconModifier.background(uiSettings.playerFirstColor)
                            )
                        }
                    }
                    Tooltip(strings.secondPlayerDefaultName) {
                        Button(
                            onClick = {
                                moveMode = MoveMode.Second
                                focusRequester.requestFocus()
                            },
                            defaultButtonModifier,
                            colors = if (moveMode == MoveMode.Second) ButtonDefaults.buttonColors(
                                selectedModeButtonColor
                            ) else ButtonDefaults.buttonColors(),
                        ) {
                            Box(
                                modifier = playerColorIconModifier.background(uiSettings.playerSecondColor)
                            )
                        }
                    }

                    @Composable
                    fun EndMoveButton(isGrounding: Boolean) {
                        with(strings) {
                            IconButton(
                                if (isGrounding) Res.drawable.ic_ground else Res.drawable.ic_resign,
                                enabled = !getField().isGameOver() && !engineIsCalculating && !engineIsAnalyzing,
                            ) {
                                // Check for game over just in case
                                if (getField().isGameOver()) return@IconButton

                                getGameTree().addChild(
                                    MoveInfo.createFinishingMove(
                                        moveMode.getMovePlayer(getField()),
                                        if (isGrounding)
                                            ExternalFinishReason.Grounding
                                        else
                                            ExternalFinishReason.Resign
                                    )
                                )
                                updateFieldAndGameTree()
                                focusRequester.requestFocus()
                            }
                        }
                    }

                    EndMoveButton(isGrounding = true)
                    EndMoveButton(isGrounding = false)

                    if (games.size > 1) {
                        @Composable
                        fun SwitchGame(next: Boolean) {
                            with (strings) {
                                IconButton(
                                    if (next) Res.drawable.ic_next else Res.drawable.ic_previous,
                                    enabled = !engineIsCalculating && !engineIsAnalyzing,
                                ) {
                                    var currentGameIndex = games.indexOf(currentGame)
                                    currentGameIndex = (currentGameIndex + if (next) 1 else games.size - 1) % games.size
                                    gameSettings.node = null
                                    switchGame(currentGameIndex)
                                }
                            }
                        }
                        SwitchGame(next = false)
                        SwitchGame(next = true)
                    }
                }

                kataGoDotsEngine?.let {
                    Row(rowModifier) {
                        val aiMoveTooltip = strings.aiMove + "\n" + when {
                            engineIsCalculating -> strings.aiThinking
                            automove -> strings.autoMoveDescription
                            else -> strings.aiMoveDescription
                        }
                        Tooltip(aiMoveTooltip) {
                            LongPressButton(
                                onClick = { makeAIMove() },
                                // The auto move mode is switched by a long press, because it's the very same
                                // action, just repeated after every move, and it needs no button of its own
                                onLongClick = {
                                    // Switching the mode off shouldn't make a move the user is turning off
                                    if (!automove) {
                                        makeAIMove()
                                    }
                                    automove = !automove
                                    kataGoDotsSettings = kataGoDotsSettings.copy(autoMove = automove)
                                    saveClassSettings(kataGoDotsSettings)
                                    focusRequester.requestFocus()
                                },
                                enabled = !getField().isGameOver() && !engineIsCalculating && !engineIsAnalyzing &&
                                        doesKataSupportRules(getField().rules),
                                colors = if (automove)
                                    ButtonDefaults.buttonColors(selectedModeButtonColor)
                                else
                                    ButtonDefaults.buttonColors(),
                            ) {
                                if (engineIsCalculating) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                } else {
                                    Icon(
                                        painterResource(Res.drawable.ic_ai_move),
                                        contentDescription = strings.aiMove,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Switching every analysis option off stops the analysis, so it has no button of its own
                        fun switchAnalysisOption(newUiSettings: UiSettings) {
                            uiSettings = newUiSettings
                            saveClassSettings(uiSettings)
                            if (!uiSettings.analysisEnabled) {
                                // Nothing displays the analysis anymore, and the stale one shouldn't come back
                                // along with the next switched on option. A running command is not interrupted,
                                // it reports itself as done, so that nothing modifies the position under it
                                moveAnalysis = null
                            }
                            focusRequester.requestFocus()
                        }

                        val analysisSupported = !getField().isGameOver() && doesKataSupportRules(getField().rules)
                        with (strings) {
                            ToggleIconButton(
                                Res.drawable.ic_candidate_moves,
                                checked = uiSettings.showCandidateMoves,
                                description = strings.candidateMovesDescription,
                                enabled = analysisSupported,
                            ) {
                                switchAnalysisOption(
                                    uiSettings.copy(showCandidateMoves = !uiSettings.showCandidateMoves)
                                )
                            }

                            ToggleIconButton(
                                Res.drawable.ic_ownership,
                                checked = uiSettings.showOwnership,
                                description = strings.ownershipDescription,
                                enabled = analysisSupported,
                            ) {
                                switchAnalysisOption(uiSettings.copy(showOwnership = !uiSettings.showOwnership))
                            }
                        }

                        if (engineIsAnalyzing) {
                            Box(Modifier.align(Alignment.CenterVertically).padding(start = 3.dp)) {
                                Tooltip(strings.analyzing) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    moveAnalysis?.let { analysis ->
                        MoveAnalysisView(analysis, getField(), uiSettings, strings)
                    }
                }

                GameTreeView(
                    currentGameTreeNode,
                    currentGame.gameTree,
                    gameTreeViewData,
                    uiSettings,
                    focusRequester,
                    onChangeGameTree = {
                        updateFieldAndGameTree()
                    }) {
                    updateCurrentNode()
                }

                if (gameTreeViewData.gameTree.game?.appInfo?.appType == AppType.Katago) {
                    GameTreeGraphsView(
                        currentGameTreeNode,
                        gameTreeViewData,
                        uiSettings,
                        onUiSettingsChange = {
                            uiSettings = it
                            saveClassSettings(uiSettings)
                            focusRequester.requestFocus()
                        },
                    ) {
                        updateCurrentNode()
                    }
                }

                currentGameTreeNode?.comment?.let { comment ->
                    if (comment.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 15.dp)) {
                            Text(
                                text = strings.sgfComment,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = comment,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                style = MaterialTheme.typography.body2
                            )
                        }
                    }
                }

                if (games.parsedNode is SgfParsedNode) {
                    SgfStatsView(games, strings)
                }
            }
        }
    }
}

