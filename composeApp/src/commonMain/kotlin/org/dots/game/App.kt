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
import org.dots.game.core.*
import org.dots.game.views.*
import org.jetbrains.compose.ui.tooling.preview.Preview

private val uiSettings = UiSettings.Standard

@Composable
@Preview
fun App() {
    MaterialTheme {
        var start by remember { mutableStateOf(true) }
        var newGameDialogRules by remember { mutableStateOf(loadRules()) }
        var field: Field by remember {  mutableStateOf(Field.create(newGameDialogRules)) }
        var fieldViewData: FieldViewData by remember { mutableStateOf<FieldViewData>(FieldViewData(field)) }
        var gameTree: GameTree by remember { mutableStateOf(GameTree(field)) }
        var gameTreeViewData: GameTreeViewData by remember { mutableStateOf(GameTreeViewData(gameTree)) }

        var lastMove: MoveResult? by remember { mutableStateOf(null) }
        var currentGameTreeNode by remember { mutableStateOf<GameTreeNode?>(null) }
        var player1Score by remember { mutableStateOf(0) }
        var player2Score by remember { mutableStateOf(0) }
        var moveNumber by remember { mutableStateOf(0) }
        val showNewGameDialog = remember { mutableStateOf(false) }
        val openGameDialog = remember { mutableStateOf(false) }
        var dumpParameters by remember { mutableStateOf(loadDumpParameters())}
        val saveGameDialog = remember { mutableStateOf(false) }
        var moveMode by remember { mutableStateOf(MoveMode.Next) }

        val focusRequester = remember { FocusRequester() }

        fun updateCurrentNode() {
            player1Score = field.player1Score
            player2Score = field.player2Score

            val currentNode = gameTree.currentNode
            currentGameTreeNode = currentNode
            lastMove = currentNode.moveResult
            moveNumber = currentNode.number
        }

        fun updateFieldAndGameTree() {
            updateCurrentNode()

            gameTreeViewData = GameTreeViewData(gameTree)
        }

        fun initializeFieldAndGameTree(newField: Field, newGameTree: GameTree, rewindForward: Boolean) {
            field = newField
            fieldViewData = FieldViewData(field)
            gameTree = newGameTree
            if (rewindForward) {
                gameTree.rewindForward()
            }
            gameTree.memoizePaths = true

            updateFieldAndGameTree()
        }

        fun resetFieldAndGameTree(rules: Rules) {
            val newField = Field.create(rules)
            initializeFieldAndGameTree(newField, GameTree(newField), rewindForward = false)
        }

        if (showNewGameDialog.value) {
            NewGameDialog(
                newGameDialogRules,
                onDismiss = {
                    showNewGameDialog.value = false
                    focusRequester.requestFocus()
                },
                onConfirmation = {
                    showNewGameDialog.value = false
                    newGameDialogRules = it
                    saveRules(newGameDialogRules)
                    resetFieldAndGameTree(newGameDialogRules)
                }
            )
        } else if (start) {
            resetFieldAndGameTree(newGameDialogRules)
            start = false
        }

        if (openGameDialog.value) {
            OpenDialog(
                newGameDialogRules,
                onDismiss = {
                    openGameDialog.value = false
                    focusRequester.requestFocus()
                },
                onConfirmation = { game ->
                    openGameDialog.value = false
                    initializeFieldAndGameTree(game.gameTree.field, game.gameTree, rewindForward = true)
                }
            )
        }

        if (saveGameDialog.value) {
            SaveDialog(
                field,
                dumpParameters,
                onDismiss = {
                    saveGameDialog.value = false
                    focusRequester.requestFocus()
                    dumpParameters = it
                    saveDumpParameters(it)
                })
        }

        Row(Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type == PointerEventType.Press) {
                        if (event.buttons.isBackPressed) {
                            if (gameTree.back()) {
                                updateCurrentNode()
                            }
                        } else if (event.buttons.isForwardPressed) {
                            if (gameTree.next()) {
                                updateCurrentNode()
                            }
                        }
                    }
                }
            }
        }) {
            Column(Modifier.padding(5.dp)) {
                Row(Modifier.width(fieldViewData.fieldSize.width), horizontalArrangement = Arrangement.Center) {
                    Text(player1Score.toString(), color = uiSettings.playerFirstColor)
                    Text(" : ")
                    Text(player2Score.toString(), color = uiSettings.playerSecondColor)
                    val player2Score = field.getScoreDiff(Player.Second)
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
                    FieldView(lastMove, moveMode, fieldViewData, uiSettings) {
                        gameTree.add(it)
                        updateFieldAndGameTree()
                    }
                }
                Row(Modifier.width(fieldViewData.fieldSize.width), horizontalArrangement = Arrangement.Center) {
                    Text(moveNumber.toString())
                }
            }
            Column(Modifier.padding(start = 5.dp)) {
                val playerButtonModifier = Modifier.padding(end = 5.dp)
                val rowModifier = Modifier.padding(bottom = 5.dp)
                val playerColorIconModifier = Modifier.size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape)
                val selectedModeButtonColor = Color.Magenta

                Row(rowModifier) {
                    Button(onClick = { showNewGameDialog.value = true }, playerButtonModifier) {
                        Text("New")
                    }
                    Button(onClick = { resetFieldAndGameTree(field.rules) }, playerButtonModifier) {
                        Text("Reset")
                    }
                    Button(onClick = { openGameDialog.value = true }, playerButtonModifier) {
                        Text("Load")
                    }
                    Button(onClick = { saveGameDialog.value = true }, playerButtonModifier) {
                        Text("Save")
                    }
                }

                Row(rowModifier) {
                    Button(
                        onClick = {
                            moveMode = MoveMode.Next
                            focusRequester.requestFocus()
                        },
                        playerButtonModifier,
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
                        playerButtonModifier,
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
                        playerButtonModifier,
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
                                val gameResult = field.finishGame(
                                    if (isGrounding) ExternalFinishReason.Grounding else ExternalFinishReason.Resign,
                                    moveMode.getMovePlayer()
                                )
                                if (gameResult != null) {
                                    gameTree.add(move = field.lastMove, gameResult = gameResult)
                                    updateFieldAndGameTree()
                                    focusRequester.requestFocus()
                                }
                            },
                            playerButtonModifier,
                        ) {
                            Text(if (isGrounding) "⏚" else "\uD83C\uDFF3\uFE0F") // Resign flag emoji in case of resigning
                        }
                    }

                    EndMoveButton(isGrounding = true)
                    EndMoveButton(isGrounding = false)
                }

                Row(rowModifier) {
                    @Composable
                    fun TransformButton(transformType: TransformType, text: String) {
                        Button(onClick = {
                            val newGameTree = gameTree.transform(transformType)
                            initializeFieldAndGameTree(newGameTree.field, newGameTree, rewindForward = false)
                        }, playerButtonModifier) {
                            Text(text)
                        }
                    }

                    TransformButton(TransformType.RotateCw90, "↻")
                    TransformButton(TransformType.RotateCw270, "↺")
                    TransformButton(TransformType.FlipHorizontal, "⇔")
                    TransformButton(TransformType.FlipVertical, "⇕")
                }

                GameTreeView(currentGameTreeNode, gameTree, gameTreeViewData, uiSettings, focusRequester) {
                    updateCurrentNode()
                }
            }
        }
    }
}
