package org.dots.game

import org.dots.game.core.BaseMode
import org.dots.game.core.ExternalFinishReason
import org.dots.game.core.Field
import org.dots.game.core.Game
import org.dots.game.core.GameTree
import org.dots.game.core.Games
import org.dots.game.core.InitPosType
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.dots.game.sgf.SgfWriter
import kotlin.random.Random

object ExampleTestData {
    const val EXAMPLE_PATH: String = "&example=.sgf"

    val exampleSgf: String = run {
        val firstGameTree = GameTree(
            Field.create(
                Rules.create(
                    10, 10, captureByBorder = true, BaseMode.AnySurrounding, suicideAllowed = false,
                    InitPosType.QuadrupleCross, Random.Default, komi = 0.5
                )
            )
        ).apply {
            addChild(MoveInfo(PositionXY(8, 1), Player.First))
            addChild(MoveInfo(PositionXY(9, 2), Player.Second))
        }
        val secondGameTree = GameTree(Field.create(Rules.Standard)).apply {
            addChild(MoveInfo(PositionXY(3, 3), Player.First))
            addChild(MoveInfo(PositionXY(4, 4), Player.Second))
            addChild(MoveInfo(PositionXY(5, 5), Player.First))
            addChild(MoveInfo.createFinishingMove(Player.Second, ExternalFinishReason.Grounding))
        }

        val games = Games(listOf(Game(firstGameTree), Game(secondGameTree)))
        SgfWriter.write(games)
    }

    val exampleSgfParams: String = run {
        val gameSettings = GameSettings(EXAMPLE_PATH, exampleSgf, 1, 2)
        gameSettings.toUrlParams()
    }
}