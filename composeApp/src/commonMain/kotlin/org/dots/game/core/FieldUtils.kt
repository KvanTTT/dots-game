package org.dots.game.core

import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

fun Field.getStrongConnectionLinePositions(position: Position): List<Position> {
    val state = position.getState()
    if (!state.checkPlaced() || state.checkTerritory()) return emptyList()

    val player = state.getPlacedPlayer()
    val playerPlaced = player.createPlacedState()
    val (x, y) = position

    return buildList {
        fun Position.addIfActive() {
            if (getState().checkActive(playerPlaced)) add(this)
        }

        Position(x, y - 1).addIfActive()
        Position(x + 1, y).addIfActive()
        Position(x, y + 1).addIfActive()
        Position(x - 1, y).addIfActive()
    }
}

/**
 * Used for graphical representation for last-placed dot
 */
fun Field.getPositionsOfConnection(position: Position, diagonalConnections: Boolean = false): List<Position> {
    val state = position.getState()
    if (!state.checkPlaced() || state.checkTerritory()) return emptyList()

    val player = state.getPlacedPlayer()
    val playerPlaced = player.createPlacedState()
    val (x, y) = position

    val activePositions = buildList {
        position.clockwiseBigJumpWalk(Position(x - 1, y - 1)) {
            if (it.getState().checkActive(playerPlaced)) {
                add(it)
            }
            true
        }
    }

    return buildList {
        fun addCurrentAndOriginalIfNeeded(currentPosition: Position) {
            if (isNotEmpty() && currentPosition.squareDistanceTo(last()) > 2) {
                add(position)
            }
            add(currentPosition)
        }

        for ((index, activePosition) in activePositions.withIndex()) {
            if (activePosition.squareDistanceTo(position) > 1) { // weak connection
                val prevActivePosition = activePositions[(index - 1 + activePositions.size) % activePositions.size]
                val nextActivePosition = activePositions[(index + 1) % activePositions.size]
                val distanceToPrev = activePosition.squareDistanceTo(prevActivePosition)
                val distanceToNext = activePosition.squareDistanceTo(nextActivePosition)
                if ((prevActivePosition == nextActivePosition && distanceToPrev == 1) ||
                    (distanceToPrev == 1 && distanceToNext > 1 || distanceToPrev > 1 && distanceToNext == 1) ||
                    diagonalConnections && (distanceToPrev > 1 || distanceToNext > 1)
                ) {
                    addCurrentAndOriginalIfNeeded(activePosition)
                }
            } else { // strong connection
                addCurrentAndOriginalIfNeeded(activePosition)
            }
        }

        if (isNotEmpty() && (last().squareDistanceTo(first()) > 2 || size <= 2)) {
            add(position)
        }
    }
}

/**
 * Returns outer/inner closures that have sorted positions (square distance between adjacent positions <= 2).
 * It's useful for surrounding drawing.
 */
fun Base.getSortedClosurePositions(field: Field, considerTerritoryPositions: Boolean = false): ExtendedClosureInfo {
    val baseMode = field.rules.baseMode
    if (baseMode != BaseMode.AllOpponentDots && !considerTerritoryPositions) {
        return ExtendedClosureInfo(closurePositions, emptyList())
    } else {
        val closureSet = (if (considerTerritoryPositions) previousStates.keys else closurePositions).toMutableSet()
        var outerClosure: List<Position> = emptyList()
        val innerClosures = mutableListOf<List<Position>>()

        var firstClosure = true
        while (closureSet.isNotEmpty()) {
            val positionClosestToHorizontalBorder = closureSet.minBy { it.y }
            // The outer closure should be minimal, the inner closure should be maximal
            val newClosure = closureSet.extractClosure(
                positionClosestToHorizontalBorder,
                // The next position should be inner for outer closure and outer for inner closure for AllOpponentDots
                // However, in case of territory positions there is only a single outer closure that should be outer-walked
                innerWalk = !considerTerritoryPositions && firstClosure,
            )
            if (firstClosure) {
                outerClosure = newClosure
                if (considerTerritoryPositions) {
                    break
                }
            } else {
                innerClosures.add(newClosure)
            }
            firstClosure = false
        }

        return ExtendedClosureInfo(outerClosure, innerClosures)
    }
}

data class ExtendedClosureInfo(val outerClosure: List<Position>, val innerClosures: List<List<Position>>)

private fun MutableSet<Position>.extractClosure(initialPosition: Position, innerWalk: Boolean): List<Position> {
    val closurePositions = mutableListOf(initialPosition)
    var square = 0
    var currentPosition: Position = initialPosition
    // The next position should always be inner for outer closure and outer for inner closure
    var nextPosition = Position(currentPosition.x + (if (innerWalk) +1 else -1), currentPosition.y + (if (innerWalk) +1 else -1))

    loop@ do {
        val walkCompleted = currentPosition.clockwiseBigJumpWalk(nextPosition) {
            return@clockwiseBigJumpWalk if (contains(it)) {
                square += currentPosition.getSquare(it)

                if (it == initialPosition) {
                    break@loop
                }

                closurePositions.add(it)
                nextPosition = currentPosition
                currentPosition = it

                false
            } else {
                true
            }
        }
        if (walkCompleted) {
            // Exit if `initialPosition` is single
            break
        }
    } while (true)

    require(if (innerWalk) square >= 0 else square <= 0)

    removeAll(closurePositions)

    return closurePositions
}

fun Field.getOneMoveCapturingAndBasePositions(): OneMoveCapturingAndBasePositions {
    val oneMoveCapturingPositions = mapOf<Player, MutableSet<Position>>(
        Player.First to mutableSetOf(),
        Player.Second to mutableSetOf(),
    )

    val oneMoveBasePositions = mapOf<Player, MutableSet<Position>>(
        Player.First to mutableSetOf(),
        Player.Second to mutableSetOf(),
    )

    for (x in 1..width) {
        for (y in 1..height) {
            val position = Position(x, y)

            fun collectCapturingAndPotentiallyBasePositions(player: Player) {
                with (this) {
                    val state = position.getState()
                    if (state.checkWithinEmptyTerritory()) {
                        val emptyTerritoryPlayer = state.getEmptyTerritoryPlayer()
                        oneMoveBasePositions.getValue(emptyTerritoryPlayer).add(position)
                        // Optimization: the dot placed into own empty territory never captures anything
                        if (emptyTerritoryPlayer == player) return
                    }

                    val moveResult = makeMove(position, player)
                    if (moveResult != null) {
                        unmakeMove()

                        if (moveResult.bases.any { it.isReal && it.player == player }) {
                            oneMoveCapturingPositions.getValue(player).add(position)
                        }

                        for (base in moveResult.bases) {
                            for ((position, oldState) in base.previousStates) {
                                if (!oldState.checkPlacedOrTerritory()) {
                                    oneMoveBasePositions.getValue(base.player).add(position)
                                }
                            }
                        }
                    }
                }
            }

            collectCapturingAndPotentiallyBasePositions(Player.First)
            collectCapturingAndPotentiallyBasePositions(Player.Second)
        }
    }

    fun refineOneMoveDeadPositions(player: Player) {
        oneMoveBasePositions.getValue(player).removeAll {
            oneMoveCapturingPositions.getValue(Player.First).contains(it) ||
                    oneMoveCapturingPositions.getValue(Player.Second).contains(it)
        }
    }

    refineOneMoveDeadPositions(Player.First)
    refineOneMoveDeadPositions(Player.Second)

    return OneMoveCapturingAndBasePositions(oneMoveCapturingPositions, oneMoveBasePositions)
}

data class OneMoveCapturingAndBasePositions(val capturingPositions: Map<Player, Set<Position>>, val basePositions: Map<Player, Set<Position>>)