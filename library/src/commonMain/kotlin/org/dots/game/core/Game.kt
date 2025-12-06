package org.dots.game.core

import org.dots.game.ParsedNode
import org.dots.game.isAlmostEqual
import org.dots.game.sgf.ruleExtraPropertyToName
import kotlin.Comparator
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

typealias PropertiesMap = MutableMap<KProperty<*>, GameProperty<*>>

sealed class PropertiesHolder(val properties: PropertiesMap, val parsedNode: ParsedNode?) {
    var player1TimeLeft: Double? by PropertyDelegate()
    var player2TimeLeft: Double? by PropertyDelegate()
    var comment: String? by PropertyDelegate()
    var unknownProperties: List<String> by PropertyDelegate()
}

data class GameProperty<T>(val value: T?, val changed: Boolean = false, val parsedNodes: List<ParsedNode> = emptyList()) {
    constructor(value: T?) : this(value, changed = true, parsedNodes = emptyList())
    constructor(value: T?, parsedNodes: List<ParsedNode>) : this(value, changed = false, parsedNodes = parsedNodes)
}

class PropertyDelegate {
    operator fun <T> getValue(thisRef: PropertiesHolder, property: KProperty<*>): T {
        @Suppress("UNCHECKED_CAST")
        return thisRef.properties[property]?.value as T
    }

    operator fun <T> setValue(thisRef: PropertiesHolder, property: KProperty<*>, value: T) {
        val previousProperty = thisRef.properties[property]
        thisRef.properties[property] =
            GameProperty(value, changed = previousProperty?.value != value, previousProperty?.parsedNodes ?: emptyList())
    }
}

class Games(games: List<Game>, val parsedNode: ParsedNode? = null) : MutableList<Game> by mutableListOf() {
    companion object {
        fun fromField(field: Field): Games {
            return Games(Game(GameTree(field).apply {
                for ((index, move = value) in field.moveSequence.withIndex()) {
                    if (index >= field.initialMovesCount) {
                        addChild(MoveInfo.fromLegalMove(move, field))
                    }
                }
            }))
        }
    }

    init {
        addAll(games)
    }

    constructor(game: Game, parsedNode: ParsedNode? = null) : this(listOf(game), parsedNode)

    constructor(parsedNode: ParsedNode? = null) : this(emptyList(), parsedNode)
}

class Game(
    val gameTree: GameTree,
    properties: PropertiesMap = mutableMapOf(),
    parsedNode: ParsedNode? = null,
) : PropertiesHolder(properties, parsedNode) {
    init {
        gameTree.game = this

        val appInfo = properties[Game::appInfo]
        if (appInfo == null && parsedNode == null) {
            // Set up this app property if the game doesn't have a binding to a parsedNode
            properties[Game::appInfo] = GameProperty(AppInfo(AppType.DotsGame.value, version = null))
        }

        val sizeProperty = properties[Game::size]
        val rules = gameTree.field.rules
        @Suppress("UNCHECKED_CAST")
        if (sizeProperty != null) {
            val (width = first, height = second) = sizeProperty.value as Pair<Int, Int>
            require(width == rules.width)
            require(height == rules.height)
        } else {
            properties[Game::size] = GameProperty(rules.width to rules.height)
        }

        val komiProperty = properties[Game::komi]
        if (komiProperty == null && !rules.komi.isAlmostEqual(0.0)) {
            properties[Game::komi] = GameProperty(rules.komi)
        }

        val ruleProperty = properties[Game::rules]
        if (ruleProperty == null && (properties[Game::appInfo]?.value as? AppInfo)?.appType == AppType.DotsGame) {
            val extraRules = buildString {
                fun appendIfNonStandard(kProperty: KProperty1<Rules, *>) {
                    val value = kProperty.get(rules)!!
                    if (value != kProperty.get(Rules.Standard)) {
                        if (isNotEmpty())
                            append(',')
                        append(ruleExtraPropertyToName.getValue(kProperty))
                        append('=')
                        val intValue = when (value) {
                            is Boolean -> if (value) 1 else 0
                            is BaseMode -> value.ordinal
                            else -> error("Not implemented type ${value::class.simpleName}")
                        }
                        append(intValue)
                    }
                }

                appendIfNonStandard(Rules::captureByBorder)
                appendIfNonStandard(Rules::baseMode)
                appendIfNonStandard(Rules::suicideAllowed)
                appendIfNonStandard(Rules::initPosIsRandom)
            }
            if (extraRules.isNotEmpty()) {
                properties[Game::rules] = GameProperty(extraRules)
            }
        }

        fun initAddDots(first: Boolean) {
            val propertyKey = if (first) Game::player1AddDots else Game::player2AddDots
            val addDots = properties[propertyKey]
            if (addDots == null) {
                val initialMoves = gameTree.field.rules.initialMoves.filter {
                    it.player == if (first) Player.First else Player.Second
                }
                if (initialMoves.isNotEmpty()) {
                    properties[propertyKey] = GameProperty(initialMoves)
                }
            }
        }

        initAddDots(first = true)
        initAddDots(first = false)
    }

    val rules: Rules = gameTree.field.rules

    val sgfGameMode: Int? by PropertyDelegate()
    val sgfFileFormat: Int? by PropertyDelegate()
    val charset: String? by PropertyDelegate()
    val size: Pair<Int, Int> by PropertyDelegate()
    val player1AddDots: List<MoveInfo> by PropertyDelegate()
    val player2AddDots: List<MoveInfo> by PropertyDelegate()
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

    var initialization: Boolean = true
}

data class AppInfo(val name: String, val version: String?) {
    val appType: AppType = AppType.entries.find { it.value == name } ?: AppType.Unknown

    override fun toString(): String {
        return name + (if (version == null) "" else ":$version")
    }
}

@ConsistentCopyVisibility
data class MoveInfo internal constructor(
    val positionXY: PositionXY?,
    val player: Player,
    val externalFinishReason: ExternalFinishReason? = null,
    val parsedNode: ParsedNode? = null,
) {
    companion object {
        fun createFinishingMove(player: Player, externalFinishReason: ExternalFinishReason, parsedNode: ParsedNode? = null): MoveInfo {
            return MoveInfo(positionXY = null, player, externalFinishReason, parsedNode)
        }

        fun fromLegalMove(legalMove: LegalMove, field: Field): MoveInfo {
            val externalFinishReason = (legalMove as? GameResult)?.toExternalFinishReason()
            return if (externalFinishReason != null) {
                createFinishingMove(legalMove.player, externalFinishReason)
            } else {
                MoveInfo(legalMove.position.toXY(field.realWidth), legalMove.player)
            }
        }
    }

    constructor(positionXY: PositionXY, player: Player, parsedNode: ParsedNode? = null) :
            this(positionXY, player, null, parsedNode)

    init {
        require(positionXY == null && externalFinishReason != null || positionXY != null && externalFinishReason == null)
    }
}

val IgnoreParseNodeComparator: Comparator<MoveInfo> = object : Comparator<MoveInfo> {
    override fun compare(a: MoveInfo, b: MoveInfo): Int {
        ((a.positionXY?.position ?: 0) - (b.positionXY?.position ?: 0)).let {
            if (it != 0) return it
        }

        (a.player.value - b.player.value).let {
            if (it != 0) return it
        }

        ((a.externalFinishReason?.ordinal ?: 0) - (b.externalFinishReason?.ordinal ?: 0)).let {
            if (it != 0) return it
        }

        return 0
    }
}

fun MoveInfo.equalsIgnoringParseNode(other: MoveInfo): Boolean {
    return IgnoreParseNodeComparator.compare(this, other) == 0
}

data class Label(val positionXY: PositionXY, val text: String)

const val ThisAppName: String = "DotsGame"

enum class AppType(val value: String) {
    Zagram("zagram.org"),
    Notago("NOTAGO"),
    Playdots("Спортивные Точки (playdots.ru)"),
    Katago("katago"),
    DotsGame(ThisAppName),
    Unknown(""),
}