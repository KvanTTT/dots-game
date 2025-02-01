package org.dots.game.sgf

import org.dots.game.core.Player
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER1_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_ADD_DOTS_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_NAME_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_RATING_KEY
import org.dots.game.sgf.SgfMetaInfo.PLAYER2_TEAM_KEY
import org.dots.game.sgf.SgfMetaInfo.propertyInfoToKey

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
    AppInfo,
    Position,
}

data class SgfPropertyInfo(
    val name: String,
    val type: SgfPropertyType = SgfPropertyType.SimpleText,
    val multipleValues: Boolean = false,
    val isKnown: Boolean = true,
)

fun SgfPropertyInfo.getPlayer(): Player {
    return when (val key = propertyInfoToKey[this]) {
        PLAYER1_NAME_KEY, PLAYER1_RATING_KEY, PLAYER1_TEAM_KEY, PLAYER1_ADD_DOTS_KEY -> Player.First
        PLAYER2_NAME_KEY, PLAYER2_RATING_KEY, PLAYER2_TEAM_KEY, PLAYER2_ADD_DOTS_KEY -> Player.Second
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

    val propertyInfos: Map<String, SgfPropertyInfo> = mapOf(
        GAME_MODE_KEY to SgfPropertyInfo("Game Mode", SgfPropertyType.Number),
        FILE_FORMAT_KEY to SgfPropertyInfo("File Format", SgfPropertyType.Number),
        CHARSET_KEY to SgfPropertyInfo( "Charset"),
        SIZE_KEY to SgfPropertyInfo( "Size", SgfPropertyType.Size),
        RULES_KEY to SgfPropertyInfo( "Rules"),
        RESULT_KEY to SgfPropertyInfo( "Result"),
        GAME_NAME_KEY to SgfPropertyInfo( "Game Name"),
        PLAYER1_NAME_KEY to SgfPropertyInfo( "Player1 Name"),
        PLAYER1_RATING_KEY to SgfPropertyInfo( "Player1 Rating", SgfPropertyType.Double),
        PLAYER1_TEAM_KEY to SgfPropertyInfo( "Player1 Team"),
        PLAYER2_NAME_KEY to SgfPropertyInfo( "Player2 Name"),
        PLAYER2_RATING_KEY to SgfPropertyInfo( "Player2 Rating", SgfPropertyType.Double),
        PLAYER2_TEAM_KEY to SgfPropertyInfo( "Player2 Team"),
        KOMI_KEY to SgfPropertyInfo( "Komi", SgfPropertyType.Double),
        DATE_KEY to SgfPropertyInfo( "Date"),
        GAME_COMMENT_KEY to SgfPropertyInfo( "Game Comment", SgfPropertyType.Text),
        COMMENT_KEY to SgfPropertyInfo( "Comment", SgfPropertyType.Text),
        PLACE_KEY to SgfPropertyInfo( "Place"),
        EVENT_KEY to SgfPropertyInfo( "Event"),
        OPENING_KEY to SgfPropertyInfo("Opening"),
        ANNOTATOR_KEY to SgfPropertyInfo("Annotator"),
        COPYRIGHT_KEY to SgfPropertyInfo("Copyright"),
        SOURCE_KEY to SgfPropertyInfo("Source"),
        TIME_KEY to SgfPropertyInfo("Time", SgfPropertyType.Double),
        OVERTIME_KEY to SgfPropertyInfo("Overtime"),
        APP_INFO_KEY to SgfPropertyInfo("App Info", SgfPropertyType.AppInfo),

        PLAYER1_ADD_DOTS_KEY to SgfPropertyInfo("Player1 initial dots", SgfPropertyType.Position, multipleValues = true),
        PLAYER2_ADD_DOTS_KEY to SgfPropertyInfo("Player2 initial dots", SgfPropertyType.Position, multipleValues = true),
    )

    val propertyInfoToKey: Map<SgfPropertyInfo, String> = propertyInfos.entries.associateBy({ it.value }) { it.key }.also {
        require(it.size == propertyInfos.size)
    }
}

class SgfProperty<T>(val info: SgfPropertyInfo, val value: T?)