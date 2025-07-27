package org.dots.game.core

import org.dots.game.core.InitialPositionType.Cross
import org.dots.game.core.InitialPositionType.Empty
import org.dots.game.core.InitialPositionType.Single
import org.dots.game.core.InitialPositionType.DoubleCross
import org.dots.game.core.InitialPositionType.QuadrupleCross
import org.dots.game.core.InitialPositionType.Custom

class Rules(
    val width: Int = 39,
    val height: Int = 32,
    val captureByBorder: Boolean = false,
    val baseMode: BaseMode = BaseMode.AtLeastOneOpponentDot,
    val suicideAllowed: Boolean = true,
    val initialMoves: List<MoveInfo> = Cross.generateDefaultInitialPositions(width, height) ?: emptyList(),
) {
    companion object {
        val Standard = Rules()
    }

    /**
     * The recognizer doesn't consider crosses orientation and positions.
     */
    val initialPositionType: InitialPositionType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val fieldStride = Field.getStride(width)
        when (initialMoves.size) {
            0 -> Empty
            1 -> Single
            else -> {
                var maxX = 0
                var maxY = 0
                for (initialMove in initialMoves) {
                    initialMove.positionXY.let {
                        val (x, y) = it
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }

                val movesArray: Array<Array<Player?>> = Array(maxX) { Array(maxY) { null } }

                for (initialMove in initialMoves) {
                    val (positionXY, player, _) = initialMove
                    val (x, y) = positionXY
                    movesArray[x - 1][y - 1] = player
                }

                var crossesCount = 0
                val ignoredPositions = hashSetOf<PositionXY>()
                for (y in 0 until maxY) {
                    for (x in 0 until maxX) {
                        if (!ignoredPositions.contains(PositionXY(x, y)) && movesArray.recognizeCross(x, y)) {
                            ignoredPositions.apply {
                                add(PositionXY(x, y))
                                add(PositionXY(x + 1, y))
                                add(PositionXY(x + 1, y + 1))
                                add(PositionXY(x, y + 1))
                            }
                            crossesCount++
                        }
                    }
                }

                when (crossesCount) {
                    1 -> Cross
                    2 -> DoubleCross
                    4 -> QuadrupleCross
                    else -> Custom
                }
            }
        }
    }

    private fun Array<Array<Player?>>.recognizeCross(x: Int, y: Int): Boolean {
        fun getPlayer(x: Int, y: Int): Player? {
            return elementAtOrNull(x)?.elementAtOrNull(y)
        }

        val firstPlayer = getPlayer(x, y) ?: return false
        val secondPlayer = getPlayer(x + 1, y) ?: return false
        if (getPlayer(x + 1, y + 1) != firstPlayer) return false
        if (getPlayer(x, y + 1) != secondPlayer) return false
        return true
    }
}

enum class InitialPositionType {
    Empty,
    Single,
    Cross,
    DoubleCross,
    QuadrupleCross,
    Custom;
}

enum class BaseMode {
    /**
     * The surrounding becomes captured if at least it captures at least one enemy's dot.
     * It doesn't imply inner holes since they could be encountered only in a case
     * when an inner empty base is surrounded by an outer inner base.
     * The case is very rare and generally in this case there is no sense to place dot inside such territories.
     * That's why inner base holes are not implied and surroundings are always created by a minimal territory
     * excluding inner holes.
     * Also, the dots game format is surrounding-based unlike go-format that is more filling-based
     * (it affects algorithms for searching surroundings).
     */
    AtLeastOneOpponentDot,

    /**
     * The surrounding becomes captured even without any dot inside.
     * Inner holes can't be created.
     */
    AnySurrounding,

    /**
     * Surrounding should not contain empty positions.
     * The mode makes the game more like Go.
     * Inner holes are possible because it removes game state contradictions and makes the game more attractive for
     * go players that used to play in this way.
     */
    AllOpponentDots;
}

/**
 * The generator tries to obey notago and bbs implementations
 */
fun InitialPositionType.generateDefaultInitialPositions(width: Int, height: Int): List<MoveInfo>? {
    val fieldStride = Field.getStride(width)
    when (this) {
        Empty -> {
            return emptyList()
        }
        Single -> {
            if (width < 1 || height < 1) return null

            return listOf(MoveInfo(PositionXY(width / 2 + 1, height / 2 + 1), Player.First))
        }
        Cross -> {
            if (width < 2 || height < 2) return null

            // Obey notago implementation for odd height
            return mutableListOf<MoveInfo>().apply { addCross((width + 1) / 2, height / 2, Player.Second) }
        }
        DoubleCross -> {
            if (width < 4 || height < 2) return null

            val middleX = (width + 1) / 2 + 1
            val middleY = height / 2 // Obey notago implementation for odd height
            return mutableListOf<MoveInfo>().apply {
                addCross(middleX - 2, middleY, Player.Second)
                addCross(middleX, middleY, Player.First)
            }
        }
        QuadrupleCross -> {
            if (width < 4 || height < 4) return null

            val offsetX: Int
            val offsetY: Int
            if (width == 39 && height == 32) {
                // Obey notago and bbs implementation for the standard field
                offsetX = 12
                offsetY = 11
            } else {
                offsetX = (width - 3) / 3 + 1
                offsetY = (height - 3) / 3 + 1
            }
            return mutableListOf<MoveInfo>().apply {
                addCross(offsetX, offsetY, Player.Second)
                addCross(width - offsetX, offsetY, Player.Second)
                addCross(width - offsetX, height - offsetY, Player.Second)
                addCross(offsetX, height - offsetY, Player.Second)
            }
        }
        else -> {
            return emptyList()
        }
    }
}

private fun MutableList<MoveInfo>.addCross(x: Int, y: Int, startPlayer: Player) {
    val oppPlayer = startPlayer.opposite()
    add(MoveInfo(PositionXY(x, y), startPlayer))
    add(MoveInfo(PositionXY(x + 1, y), oppPlayer))
    add(MoveInfo(PositionXY(x + 1, y + 1), startPlayer))
    add(MoveInfo(PositionXY(x, y + 1), oppPlayer))
}