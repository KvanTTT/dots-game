package org.dots.game.sgf

import org.dots.game.core.Game
import org.dots.game.core.GameProperty
import org.dots.game.core.GameTreeNode
import org.dots.game.core.Player
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_MOVE_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TIME_LEFT_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_MOVE_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_TIME_LEFT_KEY
import org.dots.game.sgf.SgfMetaInfo.sgfPropertyInfoToKey
import kotlin.reflect.KProperty

const val SUPPORTED_FILE_FORMAT = 4

class SgfGameMode {
    companion object {
        const val SUPPORTED_GAME_MODE_NAME = "Kropki"

        val gameModes: Map<Int, String> = mapOf(
            1 to "Go",
            2 to "Othello",
            3 to "Chess",
            4 to "Gomoku/Renju",
            5 to "Nine Men's Morris",
            6 to "Backgammon",
            7 to "Xiangqi (Chinese chess)",
            8 to "Shogi",
            9 to "Lines of Action",
            10 to "Ataxx",
            11 to "Hex",
            12 to "Jungle",
            13 to "Neutron",
            14 to "Phutball (Philosopher's Football)",
            15 to "Quadrature",
            16 to "Trax",
            17 to "Tantrix",
            18 to "Amazons",
            19 to "Octi",
            20 to "Gess",
            21 to "Twixt",
            22 to "Zèrtz",
            23 to "Plateau",
            24 to "Yinsh",
            25 to "Punct",
            26 to "Gobblet",
            27 to "Hive",
            28 to "Exxit",
            29 to "Hnefatafl (Tafl games)",
            30 to "Kuba",
            31 to "Tripples",
            32 to "Chase",
            33 to "Tumbling Down",
            34 to "Sahara",
            35 to "Byte",
            36 to "Focus",
            37 to "Dvonn",
            38 to "Tamsk",
            39 to "Gipf",
            40 to SUPPORTED_GAME_MODE_NAME,
        )

        val gameModeNameToKey: Map<String, Int> = gameModes.entries.associateBy({ it.value }) { it.key }.also {
            require(it.size == gameModes.size)
        }

        val SUPPORTED_GAME_MODE_KEY: Int = gameModeNameToKey[SUPPORTED_GAME_MODE_NAME]!!
    }
}

enum class SgfPropertyType {
    Number,
    Double,
    SimpleText,
    Text,
    Size,
    MovePosition,
    Position,
    Label,
    AppInfo,
    GameResult,
}

enum class SgfPropertyScope {
    Root,
    Move,
    Both,
}

data class SgfPropertyInfo(
    val name: String,
    val gameInfoProperty: KProperty<*>,
    val type: SgfPropertyType = SgfPropertyType.SimpleText,
    val multipleValues: Boolean = false,
    val scope: SgfPropertyScope = SgfPropertyScope.Root,
    val isKnown: Boolean = true,
)

fun SgfPropertyInfo.getPlayer(): Player {
    return when (val key = sgfPropertyInfoToKey[this]) {
        PLAYER1_NAME_KEY, PLAYER1_RATING_KEY, PLAYER1_TEAM_KEY, PLAYER1_ADD_DOTS_KEY, PLAYER1_TIME_LEFT_KEY, PLAYER1_MOVE_KEY -> Player.First
        PLAYER2_NAME_KEY, PLAYER2_RATING_KEY, PLAYER2_TEAM_KEY, PLAYER2_ADD_DOTS_KEY, PLAYER2_TIME_LEFT_KEY, PLAYER2_MOVE_KEY -> Player.Second
        else -> error("The function should be called only for player-related properties but not for `${key ?: name}`")
    }
}

object SgfMetaInfo {
    const val PLAYER1_MARKER = 'B'
    const val PLAYER2_MARKER = 'W'

    const val GAME_MODE_KEY = "GM"
    const val FILE_FORMAT_KEY = "FF"
    const val CHARSET_KEY = "CA"
    const val SIZE_KEY = "SZ"
    const val RULES_KEY = "RU"
    const val RESULT_KEY = "RE"
    const val GAME_NAME_KEY = "GN"
    const val PLAYER1_NAME_KEY = "P${PLAYER1_MARKER}"
    const val PLAYER1_RATING_KEY = "${PLAYER1_MARKER}R"
    const val PLAYER1_TEAM_KEY = "${PLAYER1_MARKER}T"
    const val PLAYER2_NAME_KEY = "P${PLAYER2_MARKER}"
    const val PLAYER2_RATING_KEY = "${PLAYER2_MARKER}R"
    const val PLAYER2_TEAM_KEY = "${PLAYER2_MARKER}T"
    const val KOMI_KEY = "KM"
    const val DATE_KEY = "DT"
    const val GAME_COMMENT_KEY = "GC"
    const val COMMENT_KEY = "C"
    const val PLACE_KEY = "PC"
    const val EVENT_KEY = "EV"
    const val OPENING_KEY = "ON"
    const val ANNOTATOR_KEY = "AN"
    const val COPYRIGHT_KEY = "CP"
    const val SOURCE_KEY = "SO"
    const val TIME_KEY = "TM"
    const val OVERTIME_KEY = "OT"
    const val APP_INFO_KEY = "AP"
    const val PLAYER1_ADD_DOTS_KEY = "A${PLAYER1_MARKER}"
    const val PLAYER2_ADD_DOTS_KEY = "A${PLAYER2_MARKER}"
    const val PLAYER1_TIME_LEFT_KEY = "${PLAYER1_MARKER}L"
    const val PLAYER2_TIME_LEFT_KEY = "${PLAYER2_MARKER}L"
    const val ROUND_KEY = "RO"
    const val HANDICAP_KEY = "HA"

    const val PLAYER1_MOVE_KEY = PLAYER1_MARKER.toString()
    const val PLAYER2_MOVE_KEY = PLAYER2_MARKER.toString()
    const val LABEL_KEY = "LB"
    const val CIRCLE_KEY = "CR"
    const val SQUARE_KEY = "SQ"

    const val UNKNOWN_KEY = ""

    const val RESIGN_WIN_GAME_RESULT = 'R'
    const val TIME_WIN_GAME_RESULT = 'T'
    const val UNKNOWN_WIN_GAME_RESULT = '?'
    const val GROUNDING_WIN_GAME_RESULT = 'G'

    val propertyInfos: Map<String, SgfPropertyInfo> = mapOf(
        GAME_MODE_KEY to SgfPropertyInfo("Game Mode", Game::sgfGameMode, SgfPropertyType.Number),
        FILE_FORMAT_KEY to SgfPropertyInfo("File Format", Game::sgfFileFormat, SgfPropertyType.Number),
        CHARSET_KEY to SgfPropertyInfo("Charset", Game::charset),
        SIZE_KEY to SgfPropertyInfo("Size", Game::size, SgfPropertyType.Size),
        RULES_KEY to SgfPropertyInfo("Rules", Game::rules),
        RESULT_KEY to SgfPropertyInfo("Result", Game::result, SgfPropertyType.GameResult),
        GAME_NAME_KEY to SgfPropertyInfo("Game Name", Game::gameName),
        PLAYER1_NAME_KEY to SgfPropertyInfo("Player1 Name", Game::player1Name),
        PLAYER1_RATING_KEY to SgfPropertyInfo( "Player1 Rating", Game::player1Rating, SgfPropertyType.Double),
        PLAYER1_TEAM_KEY to SgfPropertyInfo("Player1 Team", Game::player1Team),
        PLAYER2_NAME_KEY to SgfPropertyInfo("Player2 Name", Game::player2Name),
        PLAYER2_RATING_KEY to SgfPropertyInfo("Player2 Rating", Game::player2Rating, SgfPropertyType.Double),
        PLAYER2_TEAM_KEY to SgfPropertyInfo("Player2 Team", Game::player2Team),
        KOMI_KEY to SgfPropertyInfo("Komi", Game::komi, SgfPropertyType.Double),
        DATE_KEY to SgfPropertyInfo("Date", Game::date),
        GAME_COMMENT_KEY to SgfPropertyInfo("Game Comment", Game::description, SgfPropertyType.Text),
        COMMENT_KEY to SgfPropertyInfo("Comment", Game::comment, SgfPropertyType.Text, scope = SgfPropertyScope.Both),
        PLACE_KEY to SgfPropertyInfo("Place", Game::place),
        EVENT_KEY to SgfPropertyInfo("Event", Game::event),
        OPENING_KEY to SgfPropertyInfo("Opening", Game::opening),
        ANNOTATOR_KEY to SgfPropertyInfo("Annotator", Game::annotator),
        COPYRIGHT_KEY to SgfPropertyInfo("Copyright", Game::copyright),
        SOURCE_KEY to SgfPropertyInfo("Source", Game::source),
        TIME_KEY to SgfPropertyInfo("Time", Game::time, SgfPropertyType.Double),
        OVERTIME_KEY to SgfPropertyInfo("Overtime", Game::overtime),
        APP_INFO_KEY to SgfPropertyInfo("App Info", Game::appInfo, SgfPropertyType.AppInfo),
        PLAYER1_ADD_DOTS_KEY to SgfPropertyInfo("Player1 initial dots", Game::player1AddDots, SgfPropertyType.MovePosition, multipleValues = true),
        PLAYER2_ADD_DOTS_KEY to SgfPropertyInfo("Player2 initial dots", Game::player2AddDots, SgfPropertyType.MovePosition, multipleValues = true),
        PLAYER1_TIME_LEFT_KEY to SgfPropertyInfo("Player1 time left", Game::player1TimeLeft, SgfPropertyType.Double, scope = SgfPropertyScope.Both),
        PLAYER2_TIME_LEFT_KEY to SgfPropertyInfo("Player2 time left", Game::player2TimeLeft, SgfPropertyType.Double, scope = SgfPropertyScope.Both),
        ROUND_KEY to SgfPropertyInfo("Round", Game::round),
        HANDICAP_KEY to SgfPropertyInfo("Handicap", Game::handicap, SgfPropertyType.Number),

        PLAYER1_MOVE_KEY to SgfPropertyInfo("Player1 move", GameTreeNode::moveResult, SgfPropertyType.MovePosition, multipleValues = true, scope = SgfPropertyScope.Move),
        PLAYER2_MOVE_KEY to SgfPropertyInfo("Player2 move", GameTreeNode::moveResult, SgfPropertyType.MovePosition, multipleValues = true, scope = SgfPropertyScope.Move),
        LABEL_KEY to SgfPropertyInfo("Label", GameTreeNode::labels, SgfPropertyType.Label, multipleValues = true, scope = SgfPropertyScope.Move),
        CIRCLE_KEY to SgfPropertyInfo("Circle", GameTreeNode::circles, SgfPropertyType.Position, multipleValues = true, scope = SgfPropertyScope.Move),
        SQUARE_KEY to SgfPropertyInfo("Square", GameTreeNode::squares, SgfPropertyType.Position, multipleValues = true, scope = SgfPropertyScope.Move),

        UNKNOWN_KEY to SgfPropertyInfo("Unknown property", Game::unknownProperties, SgfPropertyType.SimpleText, multipleValues = true, scope = SgfPropertyScope.Both, isKnown = false)
    )

    val sgfPropertyInfoToKey: Map<SgfPropertyInfo, String> = propertyInfos.entries.associateBy({ it.value }) { it.key }.also {
        require(it.size == propertyInfos.size)
    }

    val gameToSgfProperty: Map<KProperty<*>, SgfPropertyInfo> = propertyInfos.values.associateBy({ it.gameInfoProperty }) { it }
}

class SgfProperty<T>(val info: SgfPropertyInfo, val node: SgfPropertyNode, val value: T?)

fun <T> Map<String, SgfProperty<*>>.getPropertyValue(propertyKey: String): T? {
    @Suppress("UNCHECKED_CAST")
    return this[propertyKey]?.value as? T
}