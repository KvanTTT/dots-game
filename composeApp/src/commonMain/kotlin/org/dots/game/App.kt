package org.dots.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dots.game.core.*
import org.dots.game.views.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var uiSettings by remember { mutableStateOf(loadUiSettings()) }
        var openGameSettings by remember { mutableStateOf(loadOpenGameSettings()) }
        var newGameDialogRules by remember { mutableStateOf(loadRules()) }

        val coroutineScope = rememberCoroutineScope()
        var pathOrContent = appSettings?.getStringOrNull(PATH_OR_CONTENT_KEY)

        var start by remember { mutableStateOf(true) }
        var games by remember { mutableStateOf(Games(Game(GameTree(Field.create(newGameDialogRules))))) }
        var currentGame by remember { mutableStateOf(games.first()) }

        fun getField(): Field = currentGame.gameTree.field
        fun getGameTree(): GameTree = currentGame.gameTree

        var gameTreeViewData: GameTreeViewData by remember { mutableStateOf(GameTreeViewData(currentGame.gameTree)) }

        var currentGameTreeNode by remember { mutableStateOf<GameTreeNode?>(null) }
        var player1Score by remember { mutableStateOf(0) }
        var player2Score by remember { mutableStateOf(0) }
        var moveNumber by remember { mutableStateOf(0) }
        var showNewGameDialog by remember { mutableStateOf(false) }
        var openGameDialog by remember { mutableStateOf(false) }
        var dumpParameters by remember { mutableStateOf(loadDumpParameters())}
        var showSaveGameDialog by remember { mutableStateOf(false) }
        var showUiSettingsForm by remember { mutableStateOf(false) }
        var moveMode by remember { mutableStateOf(MoveMode.Next) }

        val focusRequester = remember { FocusRequester() }

        fun updateCurrentNode() {
            player1Score = getField().player1Score
            player2Score = getField().player2Score

            val currentNode = getGameTree().currentNode
            currentGameTreeNode = currentNode
            moveNumber = currentNode.number
        }

        fun updateFieldAndGameTree() {
            updateCurrentNode()

            gameTreeViewData = GameTreeViewData(getGameTree())
        }

        fun switchGame(gameNumber: Int) {
            currentGame = games[gameNumber]
            if (openGameSettings.rewindToEnd && currentGame.initialization) {
                currentGame.gameTree.rewindToEnd()
            }
            currentGame.initialization = false
            currentGame.gameTree.memoizePaths = true

            updateFieldAndGameTree()
        }

        fun resetGame(newGame: Game) {
            games[games.indexOf(currentGame)] = newGame
            currentGame = newGame
            currentGame.gameTree.memoizePaths = true

            updateFieldAndGameTree()
        }

        fun resetFieldAndGameTree(rules: Rules) {
            resetGame(Game(GameTree(Field.create(rules))))
            pathOrContent = null
            appSettings?.putString(PATH_OR_CONTENT_KEY, "")
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
                saveRules(newGameDialogRules)
                resetFieldAndGameTree(newGameDialogRules)
            }
        } else if (start) {
            if (pathOrContent != null) {
                coroutineScope.launch {
                    val loadResult =
                        GameLoader.openOrLoad(pathOrContent!!, rules = null, addFinishingMove = openGameSettings.addFinishingMove)
                    if (loadResult.games.isNotEmpty()) {
                        games = loadResult.games
                        switchGame(0)
                    }
                }
            }
            start = false
        }

        if (openGameDialog) {
            OpenDialog(
                newGameDialogRules,
                pathOrContent,
                openGameSettings,
                onDismiss = {
                    openGameDialog = false
                    focusRequester.requestFocus()
                },
                onConfirmation = { newGames, newPathOrContent, newOpenGameSettings ->
                    openGameDialog = false
                    openGameSettings = newOpenGameSettings
                    pathOrContent = newPathOrContent
                    appSettings?.putString(PATH_OR_CONTENT_KEY, newPathOrContent)
                    saveOpenGameSettings(openGameSettings)
                    if (games.isNotEmpty()) {
                        games = newGames
                        switchGame(0)
                    }
                }
            )
        }

        if (showSaveGameDialog) {
            SaveDialog(
                getField(),
                dumpParameters,
                onDismiss = {
                    showSaveGameDialog = false
                    focusRequester.requestFocus()
                    dumpParameters = it
                    saveDumpParameters(it)
                })
        }

        if (showUiSettingsForm) {
            UiSettingsForm(uiSettings, onUiSettingsChange = {
                uiSettings = it
                saveUiSettings(it)
            }, onDismiss = {
                showUiSettingsForm = false
                focusRequester.requestFocus()
            })
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
            val fieldDpSize = getField().getDpSize()
            Column(Modifier.padding(5.dp)) {
                Row(Modifier.width(fieldDpSize.width), horizontalArrangement = Arrangement.Center) {
                    Text(player1Score.toString(), color = uiSettings.playerFirstColor)
                    Text(" : ")
                    Text(player2Score.toString(), color = uiSettings.playerSecondColor)
                    val player2Score = getField().getScoreDiff(Player.Second)
                    val winnerColor: Color = if (player2Score > 0.0f) {
                        uiSettings.playerSecondColor
                    } else if (player2Score < 0.0f) {
                        uiSettings.playerFirstColor
                    } else {
                        Color.Black
                    }
                    Text("  ($player2Score)", color = winnerColor)
                }
                Row {
                    FieldView(currentGameTreeNode, moveMode, getField(), uiSettings) {
                        getGameTree().add(it)
                        updateFieldAndGameTree()
                    }
                }
                Row(Modifier.width(fieldDpSize.width), horizontalArrangement = Arrangement.Center) {
                    val gameNumberText = if (games.size > 1)
                        "Game: ${games.indexOf(currentGame)}; "
                    else
                        ""
                    Text( gameNumberText + "Move: " + moveNumber.toString())
                }
            }
            Column(Modifier.padding(start = 5.dp)) {
                val controlButtonModifier = Modifier.padding(end = 5.dp)
                val rowModifier = Modifier.padding(bottom = 5.dp)
                val playerColorIconModifier = Modifier.size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape)
                val selectedModeButtonColor = Color.Magenta

                Row(rowModifier) {
                    Button(onClick = { showNewGameDialog = true }, controlButtonModifier) {
                        Text("New")
                    }
                    Button(onClick = { resetFieldAndGameTree(getField().rules) }, controlButtonModifier) {
                        Text("Reset")
                    }
                    Button(onClick = { openGameDialog = true }, controlButtonModifier) {
                        Text("Load")
                    }
                    Button(onClick = { showSaveGameDialog = true }, controlButtonModifier) {
                        Text("Save")
                    }
                    Button(onClick = { showUiSettingsForm = true }, controlButtonModifier) {
                        Text("Settings")
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
                                modifier = Modifier.offset((-5).dp).size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape).background(uiSettings.playerFirstColor)
                            )
                            Box(
                                modifier = Modifier.offset(5.dp).size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape).background(uiSettings.playerSecondColor)
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
                        colors = if (moveMode == MoveMode.Second) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                    ) {
                        Box(
                            modifier = playerColorIconModifier.background(uiSettings.playerSecondColor)
                        )
                    }

                    @Composable
                    fun EndMoveButton(isGrounding: Boolean) {
                        Button(
                            onClick = {
                                val gameResult = getField().finishGame(
                                    if (isGrounding) ExternalFinishReason.Grounding else ExternalFinishReason.Resign,
                                    moveMode.getMovePlayer()
                                )
                                if (gameResult != null) {
                                    getGameTree().add(move = getField().lastMove, gameResult = gameResult)
                                    updateFieldAndGameTree()
                                    focusRequester.requestFocus()
                                }
                            },
                            controlButtonModifier,
                        ) {
                            Text(if (isGrounding) "⏚" else "\uD83C\uDFF3\uFE0F") // Resign flag emoji in case of resigning
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
                                switchGame(currentGameIndex)
                            }, controlButtonModifier) {
                                Text(if (next) ">>" else "<<")
                            }
                        }
                        SwitchGame(next = false)
                        SwitchGame(next = true)
                    }
                }

                Row(rowModifier) {
                    @Composable
                    fun TransformButton(transformType: TransformType, text: String) {
                        Button(onClick = {
                            resetGame(Game(currentGame.gameTree.transform(transformType)))
                        }, controlButtonModifier) {
                            Text(text)
                        }
                    }

                    TransformButton(TransformType.RotateCw90, "↻")
                    TransformButton(TransformType.RotateCw270, "↺")
                    TransformButton(TransformType.FlipHorizontal, "⇔")
                    TransformButton(TransformType.FlipVertical, "⇕")
                }

                GameTreeView(currentGameTreeNode, currentGame.gameTree, gameTreeViewData, uiSettings, focusRequester) {
                    updateCurrentNode()
                }
            }
        }
    }
}
