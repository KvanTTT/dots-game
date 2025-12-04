package org.dots.game

import org.dots.game.GameLoader.getInputType
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
import org.dots.game.sgf.TextSpan
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GameLoaderTests {
    val examplePath: String = "&example=.sgf"
    val exampleSgf: String
    val exampleSgfParams: String

    init {
        val firstGameTree = GameTree(Field.create(Rules.create(10, 10, captureByBorder = true, BaseMode.AnySurrounding, suicideAllowed = false,
            InitPosType.QuadrupleCross, Random.Default, komi = 0.5))).apply {
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
        exampleSgf = SgfWriter.write(games)

        val gameSettings = GameSettings(examplePath, exampleSgf, 1, 2)
        exampleSgfParams = gameSettings.toUrlParams()
    }

    @Test
    fun detectInputTypes() {
        checkInputType(InputType.Empty, "")
        checkInputType(InputType.Empty, "   ")

        checkInputType(InputType.FieldContent, """
            . * .
            * + *
            . * .
        """)
        checkInputType(InputType.FieldContent, """
            *0 +2
            ^3 *4
        """) // Field data with error

        checkInputType(InputType.SgfContent, "     (;GM[100]") // Sgf with error
        checkInputType(InputType.SgfContent, "(;GM[40]FF[4]CA[UTF-8]SZ[0:0]SO[path/to/file.sgf])") // File regex should match the entire input but not a part

        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgf", "test.sgf"), "F:\\Dots\\test.sgf")
        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgfs", "test.sgfs"), "F:\\Dots\\test.sgfs")
        checkInputType(InputType.SgfFile("F:\\Dots\\test.sgf", "test.sgf"), "\"F:\\Dots\\test.sgf\"")
        checkInputType(InputType.SgfFile("/home/user/docs/test.sgf", "test.sgf"), "/home/user/docs/test.sgf")
        checkInputType(InputType.SgfFile("test.sgf", "test.sgf"), "test.sgf")
        checkInputType(InputType.SgfFile("path/to/test.md", "test.md", isIncorrect = true), "path/to/test.md")

        checkInputType(InputType.SgfServerUrl("https://zagram.org/eidokropki/backend/download.py?id=zagram377454", "zagram377454"), "https://zagram.org/eidokropki/backend/download.py?id=zagram377454")
        checkInputType(InputType.SgfServerUrl("https://zagram.org/eidokropki/backend/download.py?id=zagram377454", "zagram377454"), "https://zagram.org/eidokropki/index.html#url:zagram377454")
        checkInputType(InputType.SgfServerUrl("https://zagram.org/eidokropki/backend/download.py?id=zagram377454", "zagram377454"), "zagram377454")
        checkInputType(InputType.OtherUrl("https://zagram.org/"), "https://zagram.org/")

        checkInputType(
            InputType.SgfClientUrl("${THIS_APP_LOCAL_URL}/$exampleSgfParams", examplePath, exampleSgfParams, 22),
            "${THIS_APP_LOCAL_URL}/${exampleSgfParams}"
        )
        checkInputType(
            InputType.SgfClientUrl("${THIS_APP_LOCAL_URL}$exampleSgfParams", examplePath, exampleSgfParams, 21),
            "${THIS_APP_LOCAL_URL}${exampleSgfParams}"
        )
        checkInputType(
            InputType.SgfClientUrl("${THIS_APP_SERVER_URL}/$exampleSgfParams", examplePath, exampleSgfParams, 36),
            "${THIS_APP_SERVER_URL}/${exampleSgfParams}"
        )

        checkInputType(InputType.Other, "test")
        checkInputType(InputType.Other, "(")
    }

    @Test
    fun correctGameSettingsUrlParsing() {
        val decodedGameSettings = GameSettings.parseUrlParams(exampleSgfParams, 0) {
            assertFails("The error isn't expected: $it") {}
        }

        assertEquals(examplePath, decodedGameSettings.path)
        assertEquals(exampleSgf, decodedGameSettings.sgf)
        assertEquals(1, decodedGameSettings.game)
        assertEquals(2, decodedGameSettings.node)
    }

    @Test
    fun emptyGameSettingsUrlParsing() {
        val gameSettings = GameSettings(null, null, null, null)
        assertTrue(gameSettings.toUrlParams().isEmpty())
    }

    @Test
    fun incorrectGameSettingsUrlParsing() {
        fun parseAndCheckDiagnostics(str: String, vararg expectedDiagnostics: Diagnostic) {
            val diagnostics = buildList {
                val _ = GameSettings.parseUrlParams(str, 1) { add(it) }
            }
            assertEquals(expectedDiagnostics.toList(), diagnostics)
        }

        parseAndCheckDiagnostics("^%[)",
            Diagnostic("Parameter `^%[)` doesn't have a value", TextSpan(1, 4))
        )

        parseAndCheckDiagnostics("&=value&path&unknownkey=123&",
            Diagnostic("Empty parameter name", TextSpan(1, 0)),
            Diagnostic("Empty parameter name", TextSpan(2, 0)),
            Diagnostic("Parameter `path` doesn't have a value", TextSpan(9, 4)),
            Diagnostic("Parameter `unknownkey` is unknown", TextSpan(14, 10))
        )

        parseAndCheckDiagnostics("path=%-42&sgf=invalid_base64&game=999999999999999999&node=-1234",
            Diagnostic("Parameter `path` have invalid value `%-42` (URLDecoder: Illegal hex characters in escape (%) pattern - negative value)", TextSpan(6, 4)),
            Diagnostic("Parameter `sgf` have invalid value `invalid_base64` (The pad bits must be zeros)", TextSpan(15, 14)),
            Diagnostic("Parameter `game` have invalid value `999999999999999999` (Invalid number format: '999999999999999999')", TextSpan(35, 18)),
            Diagnostic("Parameter `node` have invalid value `-1234` (Invalid number format: '-1234')", TextSpan(59, 5))
        )
    }

    private fun checkInputType(expectedInputType: InputType, text: String) {
        assertEquals(expectedInputType, getInputType(text))
    }
}