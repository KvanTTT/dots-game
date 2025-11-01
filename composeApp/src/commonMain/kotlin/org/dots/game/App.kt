@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package org.dots.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
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
import org.dots.game.localization.LocalLocalizationManager
import org.dots.game.localization.LocalStrings
import org.dots.game.localization.LocalizationManager
import org.dots.game.views.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import dotsgame.composeapp.generated.resources.Res
import dotsgame.composeapp.generated.resources.ic_grounding
import dotsgame.composeapp.generated.resources.ic_resign

@Composable
@Preview
fun App(currentGameSettings: CurrentGameSettings = loadCurrentGameSettings(), onGamesChange: (games: Games?) -> Unit = { }) {
    val localizationManager = remember {
        appSettings?.let { LocalizationManager(it) } ?: LocalizationManager(
            object : com.russhwolf.settings.Settings {
                private val map = mutableMapOf<String, Any>()
                override val keys: Set<String> get() = map.keys
                override val size: Int get() = map.size
                override fun clear() = map.clear()
                override fun remove(key: String) { map.remove(key) }
                override fun hasKey(key: String) = map.containsKey(key)
                override fun putInt(key: String, value: Int) { map[key] = value }
                override fun getInt(key: String, defaultValue: Int) = map[key] as? Int ?: defaultValue
                override fun getIntOrNull(key: String) = map[key] as? Int
                override fun putLong(key: String, value: Long) { map[key] = value }
                override fun getLong(key: String, defaultValue: Long) = map[key] as? Long ?: defaultValue
                override fun getLongOrNull(key: String) = map[key] as? Long
                override fun putString(key: String, value: String) { map[key] = value }
                override fun getString(key: String, defaultValue: String) = map[key] as? String ?: defaultValue
                override fun getStringOrNull(key: String) = map[key] as? String
                override fun putFloat(key: String, value: Float) { map[key] = value }
                override fun getFloat(key: String, defaultValue: Float) = map[key] as? Float ?: defaultValue
                override fun getFloatOrNull(key: String) = map[key] as? Float
                override fun putDouble(key: String, value: Double) { map[key] = value }
                override fun getDouble(key: String, defaultValue: Double) = map[key] as? Double ?: defaultValue
                override fun getDoubleOrNull(key: String) = map[key] as? Double
                override fun putBoolean(key: String, value: Boolean) { map[key] = value }
                override fun getBoolean(key: String, defaultValue: Boolean) = map[key] as? Boolean ?: defaultValue
                override fun getBooleanOrNull(key: String) = map[key] as? Boolean
            }
        )
    }

    CompositionLocalProvider(LocalLocalizationManager provides localizationManager) {
        MaterialTheme {
            val strings = LocalStrings
            var uiSettings by remember { mutableStateOf(loadUiSettings()) }
            var newGameDialogRules by remember { mutableStateOf(loadRules()) }
            var openGameSettings by remember { mutableStateOf(loadOpenGameSettings()) }

            val coroutineScope = rememberCoroutineScope()

        var startOrReset by remember { mutableStateOf(true) }
        var games by remember { mutableStateOf(Games.fromField(Field.create(newGameDialogRules))) }
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
            startOrReset = true
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
                reset(newGame = true)
            }
        }

        if (startOrReset) {
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

            startOrReset = false
        }

        if (openGameDialog) {
            OpenDialog(
                newGameDialogRules,
                openGameSettings,
                onDismiss = {
                    openGameDialog = false
                    focusRequester.requestFocus()
                },
                onConfirmation = { newGames, newOpenGameSettings, path, content ->
                    openGameDialog = false
                    openGameSettings = newOpenGameSettings
                    saveOpenGameSettings(openGameSettings)
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

       /* TODO: implement saving if needed in the loop
       LaunchedEffect(Unit) {
            while (true) {
                saveGamesIfNeeded(games)
                delay(3000)
            }
        }*/

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
            Column(Modifier.padding(5.dp).width(maxFieldSize.width), horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    FieldView(currentGameTreeNode, moveMode, getField(), uiSettings) { position, player ->
                        getGameTree().addChild(MoveInfo(position.toXY(getField().realWidth), player))
                        updateFieldAndGameTree()
                    }
                }
                Row(Modifier.padding(bottom = 10.dp)) {
                    val player1Name = currentGame.player1Name ?: Player.First.toString()
                    val player2Name = currentGame.player2Name ?: Player.Second.toString()

                    Text("$player1Name   ", color = uiSettings.playerFirstColor)
                    Text(player1Score.toString(), color = uiSettings.playerFirstColor, fontWeight = FontWeight.Bold)

                    Text(" : ")

                    Text(player2Score.toString(), color = uiSettings.playerSecondColor, fontWeight = FontWeight.Bold)
                    Text("   $player2Name", color = uiSettings.playerSecondColor)

                    if (uiSettings.developerMode) {
                        val winnerColor: Color = if (player2Score > 0.0f) {
                            uiSettings.playerSecondColor
                        } else if (player2Score < 0.0f) {
                            uiSettings.playerFirstColor
                        } else {
                            Color.Black
                        }
                        Text("  ($player2Score)", color = winnerColor)
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
                val playerColorIconModifier = Modifier.size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape)
                val selectedModeButtonColor = Color.Magenta

                Row(rowModifier) {
                    Button(onClick = { showNewGameDialog = true }, controlButtonModifier) {
                        Text(strings.new)
                    }
                    Button(onClick = { reset(newGame = false) }, controlButtonModifier) {
                        Text(strings.reset)
                    }
                    Button(onClick = { openGameDialog = true }, controlButtonModifier) {
                        Text(strings.load)
                    }
                    Button(onClick = { showSaveGameDialog = true }, controlButtonModifier) {
                        Text(strings.save)
                    }
                    Button(onClick = { showUiSettingsForm = true }, controlButtonModifier) {
                        Text(strings.settings)
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
                                // Check for game over just in case
                                if (getField().isGameOver()) return@Button

                                getGameTree().addChild(
                                    MoveInfo.createFinishingMove(
                                        moveMode.getMovePlayer() ?: getField().getCurrentPlayer(),
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
                            enabled = !getField().isGameOver()
                        ) {
                            Icon(
                                painter = painterResource(if (isGrounding) Res.drawable.ic_grounding else Res.drawable.ic_resign),
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
                            }, controlButtonModifier) {
                                Text(if (next) ">>" else "<<")
                            }
                        }
                        SwitchGame(next = false)
                        SwitchGame(next = true)
                    }
                }

                GameTreeView(currentGameTreeNode, currentGame.gameTree, gameTreeViewData, uiSettings, focusRequester, onChangeGameTree = {
                    updateFieldAndGameTree()
                }) {
                    updateCurrentNode()
                }
            }
        }
        }
    }
}
