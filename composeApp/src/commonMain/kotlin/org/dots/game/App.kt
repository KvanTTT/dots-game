package org.dots.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dots.game.core.*
import org.dots.game.views.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_ai_move
import dotsgame.composeapp.generated.resources.ic_ai_settings
import dotsgame.composeapp.generated.resources.ic_ground
import dotsgame.composeapp.generated.resources.ic_load_game
import dotsgame.composeapp.generated.resources.ic_new_game
import dotsgame.composeapp.generated.resources.ic_reset
import dotsgame.composeapp.generated.resources.ic_resign
import dotsgame.composeapp.generated.resources.ic_save_game
import dotsgame.composeapp.generated.resources.ic_settings
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
                val playerColorIconModifier =
                    Modifier.size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape)
                val selectedModeButtonColor = Color.Magenta

                Row(rowModifier) {
                    IconButton(strings.new, Res.drawable.ic_new_game, controlButtonModifier) {
                        showNewGameDialog = true
                    }
                    IconButton(strings.reset, Res.drawable.ic_reset, controlButtonModifier) {
                        reset(newGame = false)
                    }
                    IconButton(strings.load, Res.drawable.ic_load_game, controlButtonModifier) {
                        openGameDialog = true
                    }
                    IconButton(strings.save, Res.drawable.ic_save_game, controlButtonModifier) {
                        showSaveGameDialog = true
                    }
                    IconButton(strings.settings, Res.drawable.ic_settings, controlButtonModifier) {
                        showUiSettingsForm = true
                    }

                    if (KataGoDotsEngine.IS_SUPPORTED) {
                        IconButton(strings.aiSettings, Res.drawable.ic_ai_settings, controlButtonModifier) {
                            showKataGoDotsSettingsForm = true
                        }
                    }
                }

                Row(rowModifier) {
                    Button(
                        onClick = {
                            moveMode = MoveMode.Next
                            focusRequester.requestFocus()
                        },
                        controlButtonModifier,
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
                    Button(
                        onClick = {
                            moveMode = MoveMode.First
                            focusRequester.requestFocus()
                        },
                        controlButtonModifier,
                        colors = if (moveMode == MoveMode.First) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                    ) {
                        Box(
                            modifier = playerColorIconModifier.background(uiSettings.playerFirstColor)
                        )
                    }
                    Button(
                        onClick = {
                            moveMode = MoveMode.Second
                            focusRequester.requestFocus()
                        },
                        controlButtonModifier,
                        colors = if (moveMode == MoveMode.Second) ButtonDefaults.buttonColors(
                            selectedModeButtonColor
                        ) else ButtonDefaults.buttonColors(),
                    ) {
                        Box(
                            modifier = playerColorIconModifier.background(uiSettings.playerSecondColor)
                        )
                    }

                    @Composable
                    fun EndMoveButton(isGrounding: Boolean) {
                        Button(
                            onClick = {
                                // Check for game over just in case
                                if (getField().isGameOver()) return@Button

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
                            },
                            controlButtonModifier,
                            enabled = !getField().isGameOver() && !engineIsCalculating
                        ) {
                            Icon(
                                painter = painterResource(if (isGrounding) Res.drawable.ic_ground else Res.drawable.ic_resign),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    EndMoveButton(isGrounding = true)
                    EndMoveButton(isGrounding = false)

                    if (games.size > 1) {
                        @Composable
                        fun SwitchGame(next: Boolean) {
                            Button(onClick = {
                                var currentGameIndex = games.indexOf(currentGame)
                                currentGameIndex = (currentGameIndex + if (next) 1 else games.size - 1) % games.size
                                currentGameSettings.currentNodeNumber = -1
                                switchGame(currentGameIndex)
                            }, controlButtonModifier, enabled = !engineIsCalculating) {
                                Text(if (next) ">>" else "<<")
                            }
                        }
                        SwitchGame(next = false)
                        SwitchGame(next = true)
                    }
                }

                kataGoDotsEngine?.let {
                    Row(rowModifier) {
                        Button(
                            onClick = { makeAIMove() },
                            controlButtonModifier,
                            enabled = !getField().isGameOver() && !engineIsCalculating && doesKataSupportRules(getField().rules)
                        ) {
                            if (engineIsCalculating) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp) // "Thinking..."
                                )
                            } else {
                                Icon(
                                    painterResource(Res.drawable.ic_ai_move),
                                    contentDescription = "AI Move",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text("Auto", Modifier.align(Alignment.CenterVertically))
                        Checkbox(automove, onCheckedChange = { value ->
                            automove = value
                            kataGoDotsSettings = kataGoDotsSettings.copy(autoMove = automove)
                            saveClassSettings(kataGoDotsSettings)
                        })
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
            }
        }
    }
}
