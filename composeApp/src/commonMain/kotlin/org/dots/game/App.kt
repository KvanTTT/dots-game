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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.dots.game.core.*
import org.dots.game.views.*
import org.jetbrains.compose.ui.tooling.preview.Preview

private val startRules = Rules(10, 10, captureEmptyBase = true, captureByBorder = true)
private val uiSettings = UiSettings.Standard

@Composable
@Preview
fun App() {
    MaterialTheme {
        var start by rememberSaveable { mutableStateOf(true) }
        var field: Field by rememberSaveable {  mutableStateOf(Field(startRules)) }
        var fieldViewData: FieldViewData by rememberSaveable { mutableStateOf<FieldViewData>(FieldViewData(field)) }
        var gameTree: GameTree by rememberSaveable { mutableStateOf(GameTree(field)) }
        var gameTreeViewData: GameTreeViewData by rememberSaveable { mutableStateOf(GameTreeViewData(gameTree)) }

        var lastMove: MoveResult? by remember { mutableStateOf<MoveResult?>(null) }
        var currentGameTreeNode by remember { mutableStateOf<GameTreeNode?>(null) }
        var player1Score by remember { mutableStateOf("0") }
        var player2Score by remember { mutableStateOf("0") }
        val showNewGameDialog = remember { mutableStateOf(false) }
        var moveMode by remember { mutableStateOf(MoveMode.Next) }

        fun updateCurrentNode() {
            player1Score = field.player1Score.toString()
            player2Score = field.player2Score.toString()
            lastMove = field.lastMove
            currentGameTreeNode = gameTree.currentNode
        }

        fun updateFieldAndGameTree() {
            updateCurrentNode()

            gameTreeViewData = GameTreeViewData(gameTree)
        }

        fun initializeFieldAndGameTree(rules: Rules) {
            field = Field(rules)
            fieldViewData = FieldViewData(field)
            gameTree = GameTree(field)

            updateFieldAndGameTree()
        }

        if (showNewGameDialog.value) {
            NewGameDialog(
                onDismiss = {
                    showNewGameDialog.value = false
                },
                onConfirmation = { rules ->
                    showNewGameDialog.value = false
                    initializeFieldAndGameTree(rules)
                }
            )
        } else if (start) {
            initializeFieldAndGameTree(startRules)
            start = false
        }

        Row {
            Column(Modifier.padding(5.dp)) {
                Row(Modifier.width(fieldViewData.fieldSize.width), horizontalArrangement = Arrangement.Center) {
                    Text(player1Score, color = uiSettings.playerFirstColor)
                    Text(" : ")
                    Text(player2Score, color = uiSettings.playerSecondColor)
                }
                Row {
                    FieldView(lastMove, moveMode, fieldViewData, uiSettings) {
                        gameTree.add(it)
                        updateFieldAndGameTree()
                    }
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
                    Button(onClick = { initializeFieldAndGameTree(field.rules) }, playerButtonModifier) {
                        Text("Reset")
                    }
                }

                Row(rowModifier) {
                    Button(
                        onClick = { moveMode = MoveMode.Next },
                        playerButtonModifier,
                        colors = if (moveMode == MoveMode.Next) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                    ) {
                        Box {
                            Box(
                                modifier = Modifier.offset(-5.dp).size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape).background(uiSettings.playerFirstColor)
                            )
                            Box(
                                modifier = Modifier.offset(5.dp).size(16.dp).border(1.dp, Color.White, CircleShape).clip(CircleShape).background(uiSettings.playerSecondColor)
                            )
                        }
                    }
                    Button(
                        onClick = { moveMode = MoveMode.First },
                        playerButtonModifier,
                        colors = if (moveMode == MoveMode.First) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                    ) {
                        Box(
                            modifier = playerColorIconModifier.background(uiSettings.playerFirstColor)
                        )
                    }
                    Button(
                        onClick = { moveMode = MoveMode.Second },
                        playerButtonModifier,
                        colors = if (moveMode == MoveMode.Second) ButtonDefaults.buttonColors(selectedModeButtonColor) else ButtonDefaults.buttonColors(),
                    ) {
                        Box(
                            modifier = playerColorIconModifier.background(uiSettings.playerSecondColor)
                        )
                    }
                }

                GameTreeView(currentGameTreeNode, gameTree, gameTreeViewData, uiSettings) {
                    updateCurrentNode()
                }
            }
        }
    }
}
