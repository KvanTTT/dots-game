package org.dots.game.core

import org.dots.game.core.InitPosType.Cross
import org.dots.game.core.InitPosType.Custom
import org.dots.game.core.InitPosType.DoubleCross
import org.dots.game.core.InitPosType.Empty
import org.dots.game.core.InitPosType.QuadrupleCross
import org.dots.game.core.InitPosType.Single
import kotlin.collections.elementAtOrNull
import kotlin.math.round
import kotlin.random.Random

class Rules private constructor(
    val width: Int,
    val height: Int,
    val captureByBorder: Boolean,
    val baseMode: BaseMode,
    val suicideAllowed: Boolean,
    val initialMoves: List<MoveInfo>,
    val initPosType: InitPosType,
    val remainingInitMoves: List<MoveInfo>,
    val initPosIsRandom: Boolean,
    val komi: Double,
) : ClassSettings<Rules>() {
    val random: Random?
        get() = Random.takeIf { initPosIsRandom }

    override val default: Rules
        get() = Standard

    companion object {
        val Standard = create(
            width = 39,
            height = 32,
            captureByBorder = false,
            baseMode = BaseMode.AtLeastOneOpponentDot,
            suicideAllowed = true,
            initPosType = Cross,
            random = null,
            komi = 0.0
        )

        fun create(
            width: Int,
            height: Int,
            captureByBorder: Boolean,
            baseMode: BaseMode,
            suicideAllowed: Boolean,
            initPosType: InitPosType,
            random: Random?,
            komi: Double,
        ): Rules {
            return Rules(
                width,
                height,
                captureByBorder,
                baseMode,
                suicideAllowed,
                initPosType.generateMoves(width, height, random)!!,
                initPosType = initPosType,
                remainingInitMoves = emptyList(),
                initPosIsRandom = random != null,
                komi = komi
            )
        }

        data class RulesExtra(val rules: Rules, val specifiedRandomizationContradictsRecognition: Boolean)

        fun createAndDetectInitPos(
            width: Int,
            height: Int,
            captureByBorder: Boolean,
            baseMode: BaseMode,
            suicideAllowed: Boolean,
            initialMoves: List<MoveInfo>,
            komi: Double,
            specifiedInitPosIsRandom: Boolean = false,
        ): RulesExtra {
            val (initPosType, refinedInitialMoves, isRandomized, remainingInitMoves) = recognizeInitPosType(initialMoves, width, height)
            return RulesExtra(
                Rules(
                    width,
                    height,
                    captureByBorder,
                    baseMode,
                    suicideAllowed,
                    refinedInitialMoves,
                    initPosType,
                    remainingInitMoves,
                    isRandomized || specifiedInitPosIsRandom, // In rare cases random position matches strict position
                    komi
                ),
                specifiedRandomizationContradictsRecognition = isRandomized && !specifiedInitPosIsRandom
            )
        }
    }
}

enum class InitPosType {
    Empty,
    Single,
    Cross,
    DoubleCross,
    QuadrupleCross,
    Custom;

    /**
     * The generator tries to obey notago and bbs implementations.
     * If [random] is specified, the generator randomized the init pos, but currently it works only for 4*4 cross
     */
    fun generateMoves(width: Int, height: Int, random: Random? = null): List<MoveInfo>? {
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

                if (random == null) {
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

                    // Obey notago implementation but generalize it to arbitrary field size
                    fun nextRandomOffset(): Double = random.nextDouble(4.0, 7.0)
                    fun nextRandomOffsetX(): Int = round(nextRandomOffset() / 39.0 * width).toInt()
                    fun nextRandomOffsetY(): Int = round(nextRandomOffset() / 32.0 * height).toInt()

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

    fun calculateAcceptableKomiRange(width: Int, height: Int, considerDraws: Boolean): DoubleRange {
        return RecognitionInfo(this, generateMoves(width, height)!!, isRandomized = false, remainingInitMoves = emptyList())
            .calculateAcceptableKomiRange(considerDraws)
    }
}

data class DoubleRange(override val start: Double, override val endInclusive: Double) : ClosedFloatingPointRange<Double> {
    override fun lessThanOrEquals(a: Double, b: Double): Boolean {
        return a <= b
    }
}

data class RecognitionInfo(val initPosType: InitPosType, val refinedInitMoves: List<MoveInfo>, val isRandomized: Boolean, val remainingInitMoves: List<MoveInfo>) {
    /**
     * A value within the acceptable range means that the game can't be instantly finished by the first or second player.
     * Other values make the game imbalanced because of grounding move.
     *
     * For instance, if [initPosType] is [InitPosType.Cross] (2 blue dots, 2 red dots) and komi is +3,
     * then the red player can instantly win the game by just grounding move: he loses 2 dots, but the komi compensate it
     * and the player has result score of +1.
     * The acceptable range of [InitPosType.Cross] is [-2.0, +2.0] if [considerDraws] is true and [-1.5, +1.5] if draw is prohibited.
     *
     * See tests for more examples.
     */
    fun calculateAcceptableKomiRange(considerDraws: Boolean): DoubleRange {
        val allDots = refinedInitMoves + remainingInitMoves
        val player1DotsCount: Int
        val player2DotsCount: Int
        if (allDots.isEmpty()) {
            // In empty init pos mode it's expected that at least one ungrounded dot from both players will be played (due to a central square restriction)
            player1DotsCount = 1
            player2DotsCount = 1
        } else {
            player1DotsCount = allDots.count { it.player == Player.First }
            player2DotsCount = allDots.count { it.player == Player.Second }
        }
        val lowerBound = -player1DotsCount + (if (considerDraws) 0.0 else 0.5)
        val upperBound = player2DotsCount + (if (considerDraws) 0.0 else -0.5)
        return DoubleRange(lowerBound, upperBound)
    }
}

fun recognizeInitPosType(initialMoves: List<MoveInfo>, width: Int, height: Int): RecognitionInfo {
    val recognizedCrossMoves = mutableListOf<MoveInfo>()

    fun detectRandomization(expectedInitPosType: InitPosType): RecognitionInfo {
        val nonRandomInitPosMoves =
            expectedInitPosType.generateMoves(width, height, random = null)!!.sortedBy { it.positionXY!!.position }
        recognizedCrossMoves.sortBy { it.positionXY!!.position }

        require(recognizedCrossMoves.size == nonRandomInitPosMoves.size)
        val remainingMoves = initialMoves.toMutableSet()

        var randomized = false
        for (index in 0..<recognizedCrossMoves.size) {
            val recognizedMove = recognizedCrossMoves[index]
            val nonRandomInitPosMove = nonRandomInitPosMoves[index]
            if (!randomized && (recognizedMove.positionXY != nonRandomInitPosMove.positionXY || recognizedMove.player != nonRandomInitPosMove.player)) {
                randomized = true
            }
            require(remainingMoves.remove(recognizedMove))
        }

        // If the recognized moves sequence is not random, it's not possible to detect whether it's generated or not
        // because randomizer can generate ordinary poses in rare cases

        return RecognitionInfo(expectedInitPosType, recognizedCrossMoves, randomized, remainingMoves.toList())
    }

    return when (initialMoves.size) {
        0 -> RecognitionInfo(Empty, emptyList(), false, emptyList())
        1 -> {
            recognizedCrossMoves.add(initialMoves.single())
            detectRandomization(Single)
        }
        else -> {
            val movesArray: Array<Array<MoveInfo?>> = Array(width) { Array(height) { null } }

            for (initialMove in initialMoves) {
                val (x, y) = initialMove.positionXY ?: break
                if (x >= width || y >= height) break
                movesArray[x - 1][y - 1] = initialMove
            }

            for (xyMoveInfo in initialMoves) {
                val (x, y) = xyMoveInfo.positionXY?.let { it.x - 1 to it.y - 1 } ?: break
                val firstPlayer = xyMoveInfo.player

                val x1yMoveInfo = movesArray.elementAtOrNull(x + 1)?.elementAtOrNull(y) ?: continue
                val secondPlayer = x1yMoveInfo.player

                if (firstPlayer == secondPlayer) continue

                val x1y1MoveInfo = movesArray[x + 1].elementAtOrNull(y + 1) ?: continue
                if (x1y1MoveInfo.player != firstPlayer) continue

                val xy1MoveInfo = movesArray[x][y + 1] ?: continue
                if (xy1MoveInfo.player != secondPlayer) continue

                recognizedCrossMoves.apply {
                    add(xyMoveInfo)
                    add(x1yMoveInfo)
                    add(x1y1MoveInfo)
                    add(xy1MoveInfo)
                }

                // Clean up the move array because the recognized cross is already stored
                movesArray[x][y] = null
                movesArray[x + 1][y] = null
                movesArray[x + 1][y + 1] = null
                movesArray[x][y + 1] = null
            }

            when (recognizedCrossMoves.size) {
                4 -> detectRandomization(Cross)
                8 -> detectRandomization(DoubleCross)
                16 -> detectRandomization(QuadrupleCross)
                else -> RecognitionInfo(Custom, initialMoves,  false, emptyList()) // Assume custom poses always are not random
            }
        }
    }
}

enum class BaseMode {
    /**
     * The surrounding becomes captured if at least it captures at least one enemy's dot.
     * It doesn't imply inner holes since they could be encountered only in a case
     * when an inner empty base is surrounded by an outer inner base.
     * The case is very rare and generally in this case there is no sense to place dot inside such territories.
     * That's why inner base holes are not implied, and surroundings are always created by a minimal territory
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
    OnlyOpponentDots;
}