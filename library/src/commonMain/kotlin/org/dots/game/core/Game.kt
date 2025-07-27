package org.dots.game.core

class Game(val gameInfo: GameInfo, val gameTree: GameTree) {
    val rules = gameTree.field.rules

    operator fun component1(): GameInfo = gameInfo
    operator fun component2(): Rules = rules
    operator fun component3(): GameTree = gameTree
}

class GameInfo(
    val appInfo: AppInfo?,
    val gameName: String?,
    val player1Name: String?,
    val player1Rating: Double?,
    val player1Team: String?,
    val player2Name: String?,
    val player2Rating: Double?,
    val player2Team: String?,
    val komi: Double?,
    val date: String?,
    val description: String?,
    val comment: String?,
    val place: String?,
    val event: String?,
    val opening: String?,
    val annotator: String?,
    val copyright: String?,
    val source: String?,
    val time: Double?,
    val overtime: String?,
    val result: GameResult?,
    val round: String?,
) {
    companion object {
        val Empty = GameInfo(
            appInfo = null,
            gameName = null,
            player1Name = null,
            player1Rating = null,
            player1Team = null,
            player2Name = null,
            player2Rating = null,
            player2Team = null,
            komi = null,
            date = null,
            description = null,
            comment = null,
            place = null,
            event = null,
            opening = null,
            annotator = null,
            copyright = null,
            source = null,
            time = null,
            overtime = null,
            result = null,
            round = null
        )
    }
}

data class AppInfo(val name: String, val version: String?) {
    val appType = AppType.entries.find { it.value == name } ?: AppType.Unknown
}

enum class EndGameKind {
    Grounding,
    NoLegalMoves,
}

sealed class GameResult {
    class Draw(val endGameKind: EndGameKind?) : GameResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return endGameKind == (other as Draw).endGameKind
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + endGameKind.hashCode()
            return result
        }
    }

    sealed class WinGameResult(val winner: Player) : GameResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return winner == (other as WinGameResult).winner
        }

        override fun hashCode(): Int {
            return winner.hashCode()
        }
    }

    class ScoreWin(val score: Double, val endGameKind: EndGameKind?, winner: Player) : WinGameResult(winner) {
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
    }

    class ResignWin(winner: Player) : WinGameResult(winner)

    class TimeWin(winner: Player) : WinGameResult(winner)

    class InterruptWin(winner: Player) : WinGameResult(winner)

    class UnknownWin(winner: Player) : WinGameResult(winner)

    fun toExternalFinishReason(): Pair<ExternalFinishReason?, Player?> {
        return when (this) {
            is Draw -> ExternalFinishReason.Draw to null
            is ResignWin -> ExternalFinishReason.Resign to winner.opposite()
            is TimeWin -> ExternalFinishReason.Time to winner.opposite()
            is InterruptWin -> ExternalFinishReason.Interrupt to winner.opposite()
            is UnknownWin -> ExternalFinishReason.Unknown to winner.opposite()
            is ScoreWin -> {
                when (endGameKind) {
                    EndGameKind.Grounding -> ExternalFinishReason.Grounding to winner.opposite()
                    EndGameKind.NoLegalMoves -> null to winner.opposite()
                    null -> ExternalFinishReason.Unknown to winner.opposite()
                }
            }
        }
    }
}

data class MoveInfo(val positionXY: PositionXY, val player: Player, val extraInfo: Any? = null)

data class Label(val positionXY: PositionXY, val text: String)

enum class AppType(val value: String) {
    Zagram("zagram.org"),
    Notago("NOTAGO"),
    Playdots("Спортивные Точки (playdots.ru)"),
    Unknown(""),
}