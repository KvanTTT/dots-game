package org.dots.game.core

import org.dots.game.ParsedNode
import kotlin.reflect.KProperty

class Games(games: List<Game>, val parsedNode: ParsedNode? = null) : MutableList<Game> by mutableListOf() {
    init {
        addAll(games)
    }

    constructor(game: Game, parsedNode: ParsedNode? = null) : this(listOf(game), parsedNode)

    constructor(parsedNode: ParsedNode? = null) : this(emptyList(), parsedNode)
}

class Game(
    val gameTree: GameTree,
    val gameProperties: MutableMap<KProperty<*>, GameProperty<*>> = mutableMapOf(),
    val parsedNode: ParsedNode? = null,
    val remainingInitMoves: List<MoveInfo> = emptyList(),
) {
    init {
        val sizeProperty = gameProperties[Game::size]
        val rules = gameTree.field.rules
        @Suppress("UNCHECKED_CAST")
        if (sizeProperty != null) {
            val (width, height) = sizeProperty.value as Pair<Int, Int>
            require(width == rules.width)
            require(height == rules.height)
        } else {
            gameProperties[Game::size] = GameProperty(rules.width to rules.height, true)
        }
    }

    val rules = gameTree.field.rules

    val sgfGameMode: Int? by PropertyDelegate()
    val sgfFileFormat: Int? by PropertyDelegate()
    val charset: String? by PropertyDelegate()
    val size: Pair<Int, Int> by PropertyDelegate()
    val extraRules: String? by PropertyDelegate()
    val player1AddDots: List<MoveInfo> by PropertyDelegate()
    val player2AddDots: List<MoveInfo> by PropertyDelegate()

    var player1TimeLeft: Double? by PropertyDelegate()
    var player2TimeLeft: Double? by PropertyDelegate()
    var appInfo: AppInfo? by PropertyDelegate()
    var gameName: String? by PropertyDelegate()
    var player1Name: String? by PropertyDelegate()
    var player1Rating: Double? by PropertyDelegate()
    var player1Team: String? by PropertyDelegate()
    var player2Name: String? by PropertyDelegate()
    var player2Rating: Double? by PropertyDelegate()
    var player2Team: String? by PropertyDelegate()
    var komi: Double? by PropertyDelegate()
    var date: String? by PropertyDelegate()
    var description: String? by PropertyDelegate()
    var comment: String? by PropertyDelegate()
    var place: String? by PropertyDelegate()
    var event: String? by PropertyDelegate()
    var opening: String? by PropertyDelegate()
    var annotator: String? by PropertyDelegate()
    var copyright: String? by PropertyDelegate()
    var source: String? by PropertyDelegate()
    var time: Double? by PropertyDelegate()
    var overtime: String? by PropertyDelegate()
    var result: GameResult? by PropertyDelegate()
    var round: String? by PropertyDelegate()
    var handicap: Int? by PropertyDelegate()

    var unknownProperties: List<String> by PropertyDelegate()

    var initialization: Boolean = true
}

data class GameProperty<T>(val value: T?, val changed: Boolean = false, val parsedNodes: List<ParsedNode> = emptyList())

class PropertyDelegate {
    operator fun <T> getValue(thisRef: Game, property: KProperty<*>): T {
        @Suppress("UNCHECKED_CAST")
        return thisRef.gameProperties[property]?.value as T
    }

    operator fun <T> setValue(thisRef: Game, property: KProperty<*>, value: T) {
        thisRef.gameProperties[property] =
            GameProperty(value, changed = true, thisRef.gameProperties[property]?.parsedNodes ?: emptyList())
    }
}

data class AppInfo(val name: String, val version: String?) {
    val appType = AppType.entries.find { it.value == name } ?: AppType.Unknown

    override fun toString(): String {
        return name + (if (version == null) "" else ":$version")
    }
}

enum class EndGameKind {
    Grounding,
    NoLegalMoves,
}

sealed class GameResult(val player: Player?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other != null && this::class == other::class && player == (other as GameResult).player
    }

    override fun hashCode(): Int {
        return 31 * this::class.hashCode() + player.hashCode()
    }

    class Draw(val endGameKind: EndGameKind?, player: Player?) : GameResult(player) {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            return endGameKind == (other as Draw).endGameKind
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + endGameKind.hashCode()
        }

        override fun toString(): String {
            return this::class.simpleName + endGameKind?.let { " ($it)" }
        }
    }

    sealed class WinGameResult(val winner: Player, player: Player?) : GameResult(player) {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            return winner == (other as WinGameResult).winner
        }

        override fun hashCode(): Int {
            return 31 * super.hashCode() + winner.hashCode()
        }

        override fun toString(): String {
            return this::class.simpleName + "(${::winner.name} : $winner" +
                    player?.let { ", ${::player.name}: $player" } +
                    ")"
        }
    }

    class ScoreWin(val score: Double, val endGameKind: EndGameKind?, winner: Player, player: Player?) : WinGameResult(winner, player) {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as ScoreWin
            return score == other.score && endGameKind == other.endGameKind
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + score.hashCode()
            result = 31 * result + endGameKind.hashCode()
            return result
        }

        override fun toString(): String {
            return this::class.simpleName + "(${::winner.name}: $winner, $score" +
                    endGameKind?.let { ", $endGameKind" } + ", ${::player.name}: $player" + ")"
        }
    }

    class ResignWin(winner: Player) : WinGameResult(winner, winner.opposite())

    class TimeWin(winner: Player) : WinGameResult(winner, winner.opposite())

    class InterruptWin(winner: Player) : WinGameResult(winner, winner.opposite())

    class UnknownWin(winner: Player) : WinGameResult(winner, winner.opposite())

    fun toExternalFinishReason(): ExternalFinishReason? {
        return when (this) {
            is Draw -> {
                when (endGameKind) {
                    EndGameKind.Grounding -> ExternalFinishReason.Grounding
                    EndGameKind.NoLegalMoves -> null
                    null -> ExternalFinishReason.Unknown
                }
            }
            is ResignWin -> ExternalFinishReason.Resign
            is TimeWin -> ExternalFinishReason.Time
            is InterruptWin -> ExternalFinishReason.Interrupt
            is UnknownWin -> ExternalFinishReason.Unknown
            is ScoreWin -> {
                when (endGameKind) {
                    EndGameKind.Grounding -> ExternalFinishReason.Grounding
                    EndGameKind.NoLegalMoves -> null
                    null -> ExternalFinishReason.Unknown
                }
            }
        }
    }
}

data class MoveInfo(val positionXY: PositionXY?, val player: Player, val extraInfo: ParsedNode? = null)

data class Label(val positionXY: PositionXY, val text: String)

enum class AppType(val value: String) {
    Zagram("zagram.org"),
    Notago("NOTAGO"),
    Playdots("Спортивные Точки (playdots.ru)"),
    Katago("katago"),
    Unknown(""),
}