package org.dots.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dots.game.core.*
import org.dots.game.views.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.dots.game.dump.DumpParameters

@Composable
@Preview
fun App(currentGameSettings: CurrentGameSettings = loadClassSettings(CurrentGameSettings.Default), onGamesChange: (games: Games?) -> Unit = { }) {
    MaterialTheme {
        var uiSettings by remember { mutableStateOf(loadClassSettings(UiSettings.Standard)) }
        var strings by remember { mutableStateOf(uiSettings.language.getStrings()) }
        var newGameDialogRules by remember { mutableStateOf(loadClassSettings(Rules.Standard)) }
        var openGameSettings by remember { mutableStateOf(loadClassSettings(OpenGameSettings.Default)) }
        var kataGoDotsSettings by remember { mutableStateOf(loadClassSettings(KataGoDotsSettings.Default)) }
        val coroutineScope = rememberCoroutineScope()

        var start by remember { mutableStateOf(true) }
        var reset by remember { mutableStateOf(true) }
        var games by remember { mutableStateOf(Games.fromField(Field.create(newGameDialogRules))) }
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

        fun switchGame(gameNumber: Int) {
            currentGameSettings.currentGameNumber = gameNumber
            currentGame = games.elementAtOrNull(gameNumber) ?: games[0]

            if (currentGame.initialization && currentGameSettings.currentNodeNumber == -1) {
                if (openGameSettings.rewindToEnd) {
                    currentGame.gameTree.rewindToEnd()
                }
            } else {
                currentGame.gameTree.switchToDepthFirstIndex(currentGameSettings.currentNodeNumber)
            }
            currentGame.initialization = false
            currentGame.gameTree.memoizePaths = true

            updateFieldAndGameTree()
        }

        fun reset(newGame: Boolean) {
            if (newGame)
                currentGameSettings.path = null
            currentGameSettings.content = null
            currentGameSettings.currentGameNumber = 0
            currentGameSettings.currentNodeNumber = -1
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
            val contentOrPath = currentGameSettings.content ?: currentGameSettings.path

            if (contentOrPath == null) {
                games = Games.fromField(Field.create(newGameDialogRules))
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
                        switchGame(currentGameSettings.currentGameNumber)
                    }
                }
            }

            if (start) {
                println("Detected platform: $platform")

                coroutineScope.launch {
                    println("Build Info: " + getBuildInfo())

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
                    currentGameSettings.path = path
                    currentGameSettings.content = content
                    currentGameSettings.currentGameNumber = 0
                    currentGameSettings.currentNodeNumber = -1
                    games = newGames
                    onGamesChange(games)
                    switchGame(currentGameSettings.currentGameNumber)
                }
            )
        }

        if (showSaveGameDialog) {
            SaveDialog(
                games,
                getField(),
                currentGameSettings.path,
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
                        currentGameSettings.path = newPath
                        saveClassSettings(currentGameSettings, games)
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
            KataGoDotsSettingsForm(kataGoDotsSettings, onSettingsChange = {
                showKataGoDotsSettingsForm = false
                focusRequester.requestFocus()
                kataGoDotsSettings = it.settings
                kataGoDotsEngine = it
                saveClassSettings(it.settings)
            }, onDismiss = {
                showKataGoDotsSettingsForm = false
                focusRequester.requestFocus()
            })
        }

        fun makeAIMove() {
            kataGoDotsEngine?.let {
                coroutineScope.launch {
                    val gameTree = getGameTree()
                    engineIsCalculating = true
                    gameTree.disabled = true
                    val moveInfo = it.generateMove(getField(), getField().getCurrentPlayer())
                    engineIsCalculating = false
                    gameTree.disabled = false
                    if (moveInfo != null) {
                        getGameTree().addChild(moveInfo)
                        updateFieldAndGameTree()
                    }
                    focusRequester.requestFocus()
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
                    FieldView(currentGameTreeNode, moveMode, getField(), uiSettings) { position, player ->
                        getGameTree().addChild(MoveInfo(position.toXY(getField().realWidth), player))
                        updateFieldAndGameTree()

                        if (automove) {
                            makeAIMove()
                        }
                    }
                }
                Row(Modifier.padding(bottom = 10.dp)) {
                    val player1Name = currentGame.player1Name ?: Player.First.toString()
                    val player2Name = currentGame.player2Name ?: Player.Second.toString()

                    Text("$player1Name   ", color = uiSettings.playerFirstColor)
                    Text(player1Score.toNeatNumber().toString(), color = uiSettings.playerFirstColor, fontWeight = FontWeight.Bold)

                    Text(" : ")

                    Text(
                        player2Score.toNeatNumber().toString(),
                        color = uiSettings.playerSecondColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("   $player2Name", color = uiSettings.playerSecondColor)

                    if (uiSettings.developerMode) {
                        val diff = player2Score - player1Score
                        val winnerColor: Color = when {
                            diff.isAlmostEqual(0.0) -> Color.Black
                            diff > 0.0 -> uiSettings.playerSecondColor
                            else -> uiSettings.playerFirstColor
                        }
                        Text("  ($diff)", color = winnerColor)
                    }
                }
                Row {
                    val gameNumberText = if (games.size > 1)
                        "${strings.game}: ${games.indexOf(currentGame)}; "
                    else
                        ""
                    Text(gameNumberText + "${strings.move}: $moveNumber")
                }
            }
            Column(Modifier.padding(start = 5.dp)) {
                val controlButtonModifier = Modifier.padding(end = 5.dp)
                val rowModifier = Modifier.padding(bottom = 5.dp)

                GameControlButtons(
                    strings = strings,
                    modifier = rowModifier,
                    buttonModifier = controlButtonModifier,
                    onNewGame = { showNewGameDialog = true },
                    onReset = { reset(newGame = false) },
                    onLoad = { openGameDialog = true },
                    onSave = { showSaveGameDialog = true },
                    onSettings = { showUiSettingsForm = true },
                    onAiSettings = { showKataGoDotsSettingsForm = true },
                    showAiSettings = KataGoDotsEngine.IS_SUPPORTED
                )

                MoveControlButtons(
                    moveMode = moveMode,
                    uiSettings = uiSettings,
                    strings = strings,
                    isGameOver = getField().isGameOver(),
                    engineIsCalculating = engineIsCalculating,
                    modifier = rowModifier,
                    buttonModifier = controlButtonModifier,
                    onMoveModeChange = {
                        moveMode = it
                        focusRequester.requestFocus()
                    },
                    onEndMove = { reason ->
                        getGameTree().addChild(
                            MoveInfo.createFinishingMove(
                                moveMode.getMovePlayer(getField()),
                                reason
                            )
                        )
                        updateFieldAndGameTree()
                        focusRequester.requestFocus()
                    }
                )

                GameNavigationButtons(
                    gamesCount = games.size,
                    strings = strings,
                    modifier = rowModifier,
                    buttonModifier = controlButtonModifier,
                    engineIsCalculating = engineIsCalculating,
                    onSwitchGame = { next ->
                        var currentGameIndex = games.indexOf(currentGame)
                        currentGameIndex = (currentGameIndex + if (next) 1 else games.size - 1) % games.size
                        currentGameSettings.currentNodeNumber = -1
                        switchGame(currentGameIndex)
                    }
                )

                AiControlButtons(
                    isEngineAvailable = kataGoDotsEngine != null,
                    isEngineCalculating = engineIsCalculating,
                    isGameOver = getField().isGameOver(),
                    isRulesSupported = doesKataSupportRules(getField().rules),
                    autoMove = automove,
                    strings = strings,
                    modifier = rowModifier,
                    buttonModifier = controlButtonModifier,
                    onMakeAiMove = { makeAIMove() },
                    onAutoMoveChange = { value ->
                        automove = value
                        kataGoDotsSettings = kataGoDotsSettings.copy(autoMove = automove)
                        saveClassSettings(kataGoDotsSettings)
                    }
                )

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
            }
        }
    }
}
