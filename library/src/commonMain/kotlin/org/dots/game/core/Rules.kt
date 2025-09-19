package org.dots.game.core

import org.dots.game.core.InitPosType.Cross
import org.dots.game.core.InitPosType.Empty
import org.dots.game.core.InitPosType.Single
import org.dots.game.core.InitPosType.DoubleCross
import org.dots.game.core.InitPosType.QuadrupleCross
import org.dots.game.core.InitPosType.Custom
import org.dots.game.core.Rules.Companion.DYNAMIC_RANDOM_SEED
import org.dots.game.core.Rules.Companion.NO_RANDOM_SEED
import kotlin.math.round
import kotlin.random.Random

class Rules(
    val width: Int = 39,
    val height: Int = 32,
    val captureByBorder: Boolean = false,
    val baseMode: BaseMode = BaseMode.AtLeastOneOpponentDot,
    val suicideAllowed: Boolean = true,
    val randomSeed: Int = NO_RANDOM_SEED,
    val initialMoves: List<MoveInfo> = Cross.generateDefaultInitPos(width, height, randomSeed)!!,
    val komi: Double = 0.0,
) {
    companion object {
        val Standard = Rules()
        const val NO_RANDOM_SEED = -1
        const val DYNAMIC_RANDOM_SEED = 0
    }

    /**
     * The recognizer doesn't consider crosses orientation and positions.
     */
    val initPosType: InitPosType by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val recognizedMoves = mutableListOf<MoveInfo>()

        fun verifyRandomSeed(expectedInitPosType: InitPosType): InitPosType {
            val nonRandomInitPosMoves =
                expectedInitPosType.generateDefaultInitPos(width, height, NO_RANDOM_SEED)!!
                    .sortedBy { it.positionXY!!.position }
            recognizedMoves.sortBy { it.positionXY!!.position }

            require(recognizedMoves.size == nonRandomInitPosMoves.size)
            var random = false
            for (index in 0..<recognizedMoves.size) {
                val recognizedMove = recognizedMoves[index]
                val nonRandomInitPosMove = nonRandomInitPosMoves[index]
                if (recognizedMove.positionXY != nonRandomInitPosMove.positionXY || recognizedMove.player != nonRandomInitPosMove.player) {
                    random = true
                    break
                }
            }

            if (random) {
                require(randomSeed >= 0)
            }
            // If the recognized moves sequence is not random, it's not possible to detect whether it's generated or not
            // because randomizer can generate ordinary poses in rare cases

            return expectedInitPosType
        }

        when (initialMoves.size) {
            0 -> Empty
            1 -> {
                recognizedMoves.add(initialMoves.single())
                verifyRandomSeed(Single)
            }
            else -> {
                var maxX = 0
                var maxY = 0
                for (initialMove in initialMoves) {
                    initialMove.positionXY.let {
                        val (x, y) = it ?: break
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                    }
                }

                val movesArray: Array<Array<Player?>> = Array(maxX) { Array(maxY) { null } }

                for (initialMove in initialMoves) {
                    val (positionXY, player, _) = initialMove
                    val (x, y) = positionXY ?: break
                    movesArray[x - 1][y - 1] = player
                }

                val ignoredPositions = hashSetOf<PositionXY>()
                for (y in 0 until maxY) {
                    for (x in 0 until maxX) {
                        val x1 = x + 1
                        val y1= y + 1
                        if (ignoredPositions.contains(PositionXY(x1, y1))) continue

                        val crossFirstPlayer = movesArray.recognizeCross(x, y) ?: continue

                        PositionXY(x1, y1).let {
                            recognizedMoves.add(MoveInfo(it, crossFirstPlayer))
                            ignoredPositions.add(it)
                        }

                        PositionXY(x1 + 1, y1).let {
                            recognizedMoves.add(MoveInfo(it, crossFirstPlayer.opposite()))
                            ignoredPositions.add(it)
                        }

                        PositionXY(x1 + 1, y1 + 1).let {
                            recognizedMoves.add(MoveInfo(it, crossFirstPlayer))
                            ignoredPositions.add(it)
                        }

                        PositionXY(x1, y1 + 1).let {
                            recognizedMoves.add(MoveInfo(it, crossFirstPlayer.opposite()))
                            ignoredPositions.add(it)
                        }
                    }
                }

                when (recognizedMoves.size) {
                    4 -> verifyRandomSeed(Cross)
                    8 -> verifyRandomSeed(DoubleCross)
                    16 -> verifyRandomSeed(QuadrupleCross)
                    else -> Custom // Don't check randomSeed in case of custom position because of lack of info
                }
            }
        }
    }

    private fun Array<Array<Player?>>.recognizeCross(x: Int, y: Int): Player? {
        fun getPlayer(x: Int, y: Int): Player? {
            return elementAtOrNull(x)?.elementAtOrNull(y)
        }

        val firstPlayer = getPlayer(x, y) ?: return null
        val secondPlayer = getPlayer(x + 1, y) ?: return null
        if (getPlayer(x + 1, y + 1) != firstPlayer) return null
        if (getPlayer(x, y + 1) != secondPlayer) return null
        return firstPlayer
    }
}

enum class InitPosType {
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
 * The generator tries to obey notago and bbs implementations.
 * The [randomSeed] forces the cross to be random generated according to notago rules (only 4*4 cross)
 * If [randomSeed] < [DYNAMIC_RANDOM_SEED] ([NO_RANDOM_SEED]) then no randomization is used.
 * If [randomSeed] == [DYNAMIC_RANDOM_SEED] then randomization is used with system-generated seed (not reproducible behavior)
 * Otherwise, randomization relies on the provided positive seed.
 */
fun InitPosType.generateDefaultInitPos(width: Int, height: Int, randomSeed: Int = Rules.Standard.randomSeed): List<MoveInfo>? {
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
            return mutableListOf<MoveInfo>().apply { addCross((width + 1) / 2, height / 2, Player.First) }
        }
        DoubleCross -> {
            if (width < 4 || height < 2) return null

            val middleX = (width + 1) / 2 + 1
            val middleY = height / 2 // Obey notago implementation for odd height
            return mutableListOf<MoveInfo>().apply {
                addCross(middleX - 2, middleY, Player.First)
                addCross(middleX, middleY, Player.Second)
            }
        }
        QuadrupleCross -> {
            if (width < 4 || height < 4) return null

            val offsetX1: Int
            val offsetY1: Int
            val offsetX2: Int
            val offsetY2: Int
            val offsetX3: Int
            val offsetY3: Int
            val offsetX4: Int
            val offsetY4: Int

            if (randomSeed < DYNAMIC_RANDOM_SEED) {
                if (width == 39 && height == 32) {
                    // Obey notago and bbs implementation for the standard field
                    offsetX1 = 12
                    offsetY1 = 11
                } else {
                    offsetX1 = (width - 3) / 3 + 1
                    offsetY1 = (height - 3) / 3 + 1
                }
                offsetX2 = width - offsetX1
                offsetY2 = offsetY1
                offsetX3 = width - offsetX1
                offsetY3 = height - offsetY1
                offsetX4 = offsetX1
                offsetY4 = height - offsetY1
            } else {
                val middleX = width / 2
                val middleY = height / 2
                val random = if (randomSeed > DYNAMIC_RANDOM_SEED) Random(randomSeed) else Random.Default

                // Obey notago implementation but generalize it to arbitrary field size
                fun nextRandomOffset(): Int = random.nextInt(4, 7 + 1)
                fun nextRandomOffsetX(): Int = round ( nextRandomOffset() / 39.0 * width).toInt()
                fun nextRandomOffsetY(): Int = round (nextRandomOffset()  / 32.0 * height).toInt()

                offsetX1 = middleX - nextRandomOffsetX()
                offsetY1 = middleY - nextRandomOffsetY()
                offsetX2 = middleX + nextRandomOffsetX()
                offsetY2 = middleY - nextRandomOffsetY()
                offsetX3 = middleX + nextRandomOffsetX()
                offsetY3 = middleY + nextRandomOffsetY()
                offsetX4 = middleX - nextRandomOffsetX()
                offsetY4 = middleY + nextRandomOffsetY()
            }
            return mutableListOf<MoveInfo>().apply {
                addCross(offsetX1, offsetY1, Player.First)
                addCross(offsetX2, offsetY2, Player.First)
                addCross(offsetX3, offsetY3, Player.First)
                addCross(offsetX4, offsetY4, Player.First)
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