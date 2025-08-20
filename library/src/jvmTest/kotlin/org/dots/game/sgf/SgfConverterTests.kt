package org.dots.game.sgf

import org.dots.game.DiagnosticSeverity
import org.dots.game.LineColumn
import org.dots.game.LineColumnDiagnostic
import org.dots.game.buildLineOffsets
import org.dots.game.core.AppInfo
import org.dots.game.core.AppType
import org.dots.game.core.Game
import org.dots.game.core.GameResult
import org.dots.game.core.GameTreeNode
import org.dots.game.core.MoveInfo
import org.dots.game.core.Player
import org.dots.game.core.PositionPlayer
import org.dots.game.core.Position
import org.dots.game.core.PositionXY
import org.dots.game.toLineColumnDiagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SgfConverterTests {
    @Test
    fun gameInfo() {
        val (gameInfo, rules) = parseConvertAndCheck(
            "(;GM[40]FF[4]CA[UTF-8]SZ[17:21]RU[russian]GN[Test Game]PB[Player1]BR[256]BT[Player1's Team]PW[Player2]WR[512]WT[Player2's Team]KM[0.5]DT[2025-01-19]GC[A game for SGF parser testing]C[Comment to node]PC[Amsterdam, Netherlands]EV[Test event]ON[Empty]AN[Ivan Kochurkin]CP[@]SO[https://zagram.org/eidokropki/index.html]TM[300]OT[0+25]AP[https\\://zagram.org/eidokropki/index.html:1]RO[1 (final)])",
            ).single()
        with (gameInfo) {
            assertEquals(17, rules.width)
            assertEquals(21, rules.height)
            assertEquals("Test Game", gameName)
            assertEquals("Player1", player1Name)
            assertEquals(256.0, player1Rating)
            assertEquals("Player1's Team", player1Team)
            assertEquals("Player2", player2Name)
            assertEquals(512.0, player2Rating)
            assertEquals("Player2's Team", player2Team)
            assertEquals(0.5, komi)
            assertEquals("2025-01-19", date)
            assertEquals("A game for SGF parser testing", description)
            assertEquals("Comment to node", comment)
            assertEquals("Amsterdam, Netherlands", place)
            assertEquals("Test event", event)
            assertEquals("Empty", opening)
            assertEquals("Ivan Kochurkin", annotator)
            assertEquals("@", copyright)
            assertEquals("https://zagram.org/eidokropki/index.html", source)
            assertEquals(300.0, time)
            assertEquals("0+25", overtime)
            assertEquals(AppInfo("https://zagram.org/eidokropki/index.html", "1"), appInfo)
            assertEquals("1 (final)", round)
        }
    }

    private val multiGamesSgf = """
            (;GM[40]FF[4]SZ[39:32]GN[game 1])
            (;GM[40]FF[4]SZ[20:20]GN[game 2])
        """.trimIndent()

    @Test
    fun multipleGames() {
        val games = parseConvertAndCheck(multiGamesSgf)
        val (gameInfo0, rules0) = games[0]
        assertEquals(39, rules0.width)
        assertEquals(32, rules0.height)
        assertEquals("game 1", gameInfo0.gameName)
        val (gameInfo1, rules1) = games[1]
        assertEquals(20, rules1.width)
        assertEquals(20, rules1.height)
        assertEquals("game 2", gameInfo1.gameName)
    }

    @Test
    fun multipleGamesWarning() {
        parseConvertAndCheck(
            multiGamesSgf,
            listOf(
                LineColumnDiagnostic(
                    "Only single game is supported. Other games will be ignored.", LineColumn(2, 1),
                    DiagnosticSeverity.Warning
                )
            ),
            warnOnMultipleGames = true
        )
    }

    @Test
    fun emptyFile() {
        assertTrue(
            parseConvertAndCheck(
                "", listOf(
                    LineColumnDiagnostic(
                        "Empty game trees.",
                        LineColumn(1, 1),
                        DiagnosticSeverity.Warning
                    )
                )
            ).isEmpty()
        )
    }

    @Test
    fun noNodes() {
        assertTrue(
            parseConvertAndCheck(
                "()", listOf(
                    LineColumnDiagnostic(
                        "Root node with game info is missing.",
                        LineColumn(1, 2),
                        DiagnosticSeverity.Error
                    )
                )
            ).isEmpty()
        )
    }

    @Test
    fun requiredPropertiesWithInvalidValues() {
        assertTrue(
            parseConvertAndCheck(
                "(;GM[1]FF[3]SZ[1234:5678])",
                listOf(
                    LineColumnDiagnostic(
                        "Property GM (Game Mode) has unsupported value `1` (Go). The only `40` (Kropki) is supported.",
                        LineColumn(1, 6),
                        DiagnosticSeverity.Critical
                    ),
                    LineColumnDiagnostic(
                        "Property FF (File Format) has unsupported value `3`. The only `4` is supported.",
                        LineColumn(1, 11),
                        DiagnosticSeverity.Critical
                    ),
                    LineColumnDiagnostic(
                        "Property SZ (Size) has invalid width: `1234`. Expected: 0..62.",
                        LineColumn(1, 16),
                        DiagnosticSeverity.Critical
                    ),
                    LineColumnDiagnostic(
                        "Property SZ (Size) has invalid height: `5678`. Expected: 0..62.",
                        LineColumn(1, 21),
                        DiagnosticSeverity.Critical
                    ),
                )
            ).isEmpty()
        )
    }

    @Test
    fun zeroSizeDimension() {
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[0])", listOf(
                LineColumnDiagnostic(
                    "Property SZ (Size) has zero value.",
                    LineColumn(1, 17),
                    DiagnosticSeverity.Warning
                )
            )
        )
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[0:0])",
            listOf(
                LineColumnDiagnostic(
                    "Property SZ (Size) has zero width.",
                    LineColumn(1, 17),
                    DiagnosticSeverity.Warning
                ),
                LineColumnDiagnostic(
                    "Property SZ (Size) has zero height.",
                    LineColumn(1, 19),
                    DiagnosticSeverity.Warning
                )
            )
        )
    }

    @Test
    fun differentGameModeAndSizeValues() {
        parseConvertAndCheck(
            "(;GM[100]FF[4]SZ[20])", listOf(
                LineColumnDiagnostic(
                    "Property GM (Game Mode) has unsupported value `100`. The only `40` (Kropki) is supported.",
                    LineColumn(1, 6),
                    DiagnosticSeverity.Critical
                )
            )
        )
        parseConvertAndCheck("(;GM[40]FF[4]SZ[20])")
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[str])", listOf(
                LineColumnDiagnostic(
                    "Property SZ (Size) has invalid value `str`. Expected: 0..62.",
                    LineColumn(1, 17),
                    DiagnosticSeverity.Critical,
                )
            )
        )
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[30:31:32])",
            listOf(
                LineColumnDiagnostic(
                    "Property SZ (Size) is defined in incorrect format: `30:31:32`. Expected: INT or INT:INT.",
                    LineColumn(1, 17),
                    DiagnosticSeverity.Critical
                )
            )
        )
    }

    @Test
    fun requiredPropertiesMissing() {
        assertTrue(
            parseConvertAndCheck(
                "(;)", listOf(
                    LineColumnDiagnostic(
                        "Property GM (Game Mode) should be specified.",
                        LineColumn(1, 3),
                        DiagnosticSeverity.Error
                    ),
                    LineColumnDiagnostic(
                        "Property FF (File Format) should be specified.",
                        LineColumn(1, 3),
                        DiagnosticSeverity.Error
                    ),
                    LineColumnDiagnostic(
                        "Property SZ (Size) should be specified.",
                        LineColumn(1, 3),
                        DiagnosticSeverity.Critical
                    ),
                )
            ).isEmpty()
        )
    }

    @Test
    fun duplicatedProperties() {
        parseConvertAndCheck(
            "(;GM[40]GM[40]FF[4]PB[Player1]SZ[39:32]PB[Player11]PB)", listOf(
                LineColumnDiagnostic(
                    "Property GM (Game Mode) is duplicated and ignored.",
                    LineColumn(1, 9),
                    DiagnosticSeverity.Warning
                ),
                LineColumnDiagnostic(
                    "Property PB (Player1 Name) is duplicated and ignored.",
                    LineColumn(1, 40),
                    DiagnosticSeverity.Warning
                ),
                LineColumnDiagnostic(
                    "Property PB (Player1 Name) is unspecified.",
                    LineColumn(1, 54),
                    DiagnosticSeverity.Error
                ),
                LineColumnDiagnostic(
                    "Property PB (Player1 Name) is duplicated and ignored.",
                    LineColumn(1, 52),
                    DiagnosticSeverity.Warning
                ),
            )
        )
    }

    @Test
    fun propertyHasMultipleValues() {
        parseConvertAndCheck(
            "(;GM[40][1]FF[4]PB[Player1][Player11]SZ[39:32])",
            listOf(
                LineColumnDiagnostic(
                    "Property GM (Game Mode) has unsupported value `1` (Go). The only `40` (Kropki) is supported.",
                    LineColumn(1, 10),
                    DiagnosticSeverity.Critical
                ),
                LineColumnDiagnostic(
                    "Property GM (Game Mode) has duplicated value `1` that's ignored.",
                    LineColumn(1, 10),
                    DiagnosticSeverity.Warning
                ),
                LineColumnDiagnostic(
                    "Property PB (Player1 Name) has duplicated value `Player11` that's ignored.",
                    LineColumn(1, 29),
                    DiagnosticSeverity.Warning
                ),
            )
        )
    }

    @Test
    fun unknownProperties() {
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[39:32]UP[text value]UP)", listOf(
                LineColumnDiagnostic("Property UP is unknown.", LineColumn(1, 23), DiagnosticSeverity.Warning),
                LineColumnDiagnostic("Property UP is unknown.", LineColumn(1, 37), DiagnosticSeverity.Warning),
            )
        )
    }

    @Test
    fun timeLeft() {
        val gameTree = parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[39:32]BL[315]WL[312];B[bc]BL[308.3];W[de]WL[294.23])",
        ).single().gameTree
        assertEquals(gameTree.player1TimeLeft, 315.0)
        assertEquals(gameTree.player2TimeLeft, 312.0)
        val fieldStride = gameTree.field.realWidth
        var nextNode = gameTree.rootNode.getNextNode(2, 3, fieldStride, Player.First)!!
        assertEquals(308.3, nextNode.timeLeft)
        nextNode = nextNode.getNextNode(4, 5, fieldStride, Player.Second)!!
        assertEquals(294.23, nextNode.timeLeft)
    }

    @Test
    fun incorrectFormatWarnings() {
        val gameInfo = parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[39:32]BR[asdf])", listOf(
                LineColumnDiagnostic(
                    "Property BR (Player1 Rating) has incorrect format: `asdf`. Expected: Real Number.",
                    LineColumn(1, 26),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single().gameInfo
        assertNull(gameInfo.player1Rating)
    }

    @Test
    fun playdotsSgf() {
        val gameInfo = parseConvertAndCheck(
            "(;AP[Спортивные Точки (playdots.ru)]GM[40]FF[4]SZ[39:32]BR[Нет звания, 1200]WR[Второй разряд, 1300])"
        ).single().gameInfo

        assertEquals(AppType.Playdots, gameInfo.appInfo!!.appType)
        assertEquals(1200.0, gameInfo.player1Rating)
        assertEquals(1300.0, gameInfo.player2Rating)
    }

    @Test
    fun gameResultIsInvalid() {
        val valuesToErrors = listOf<Triple<String, LineColumnDiagnostic, GameResult?>>(
            Triple("",
                LineColumnDiagnostic(
                    "Property RE (Result) has invalid player ``. Allowed values: B or W",
                    LineColumn(1, 26),
                    DiagnosticSeverity.Error
                ), null),
            Triple("_",
                LineColumnDiagnostic(
                    "Property RE (Result) has invalid player `_`. Allowed values: B or W",
                    LineColumn(1, 26),
                    DiagnosticSeverity.Error
                ), null),
            Triple("B_",
                LineColumnDiagnostic(
                    "Property RE (Result) value `B_` is written in invalid format. Correct format is 0 (Draw) or X+Y where X is B or W, Y is Number, R (Resign), T (Time) or ? (Unknown)",
                    LineColumn(1, 27),
                    DiagnosticSeverity.Error
                ), GameResult.UnknownWin(Player.First)),
            Triple("B+",
                LineColumnDiagnostic(
                    "Property RE (Result) has invalid result value ``. Correct value is Number, R (Resign), T (Time) or ? (Unknown)",
                    LineColumn(1, 28),
                    DiagnosticSeverity.Error
                ), GameResult.UnknownWin(Player.First)),
            Triple("B+X",
                LineColumnDiagnostic(
                    "Property RE (Result) has invalid result value `X`. Correct value is Number, R (Resign), T (Time) or ? (Unknown)",
                    LineColumn(1, 28),
                    DiagnosticSeverity.Error
                ), GameResult.UnknownWin(Player.First)),
            Triple("B+R_",
                LineColumnDiagnostic(
                    "Property RE (Result) has unexpected suffix `_`.",
                    LineColumn(1, 29),
                    DiagnosticSeverity.Error
                ), GameResult.ResignWin(Player.First)),
        )

        for ((value, diagnostic, expectedGameResult) in valuesToErrors) {
            val actualGameResult = parseConvertAndCheck(
                "(;GM[40]FF[4]SZ[39:32]RE[$value])",
                listOf(diagnostic)
            ).single().gameInfo.result
            if (expectedGameResult != null) {
                assertEquals(expectedGameResult, actualGameResult)
            }
        }
    }

    @Test
    fun gameResultIsValid() {
        val valuesToGameResults = listOf(
            Triple("0", GameResult.Draw(endGameKind = null), null),
            Triple("Draw", GameResult.Draw(endGameKind = null), null),
            Triple("B+R", GameResult.ResignWin(Player.First), null),
            Triple("B+T" , GameResult.TimeWin(Player.First), null),
            Triple("B+?" , GameResult.UnknownWin(Player.First), null),
            Triple("B+10" , GameResult.ScoreWin(10.0, endGameKind = null, Player.First),
                LineColumnDiagnostic(
                    "Property RE (Result) has value `10` that doesn't match score from game field `0`.",
                    LineColumn(1, 32),
                    DiagnosticSeverity.Warning
                )
            ),
            Triple("W+5" , GameResult.ScoreWin(5.0, endGameKind = null, Player.Second),
                LineColumnDiagnostic(
                    "Property RE (Result) has value `5` that doesn't match score from game field `0`.",
                    LineColumn(1, 31),
                    DiagnosticSeverity.Warning
                )
            ),
        )

        for ((value, expectedGameResult, diagnostic) in valuesToGameResults) {
            val actualGameResult = parseConvertAndCheck(
                "(;GM[40]FF[4]SZ[39:32]RE[$value])",
                listOfNotNull(diagnostic)
            ).single().gameInfo.result
            assertEquals(expectedGameResult, actualGameResult)
        }
    }

    @Test
    fun komi() {
        parseConvertAndCheck("(;GM[40]FF[4]SZ[39:32]KM[0.5]RE[W+0.5])").single().gameInfo.result
        parseConvertAndCheck(
            "(;GM[40]FF[4]SZ[39:32]KM[0]RE[W+0.5])",
            listOfNotNull(
                LineColumnDiagnostic(
                    "Property RE (Result) has value `0.5` that doesn't match score from game field `0`.",
                    LineColumn(1, 38),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single().gameInfo.result
    }
}

internal fun parseConvertAndCheck(input: String, expectedDiagnostics: List<LineColumnDiagnostic> = emptyList(), warnOnMultipleGames: Boolean = false): List<Game> {
    val lineOffsets by lazy(LazyThreadSafetyMode.NONE) { input.buildLineOffsets() }
    val actualDiagnostics = mutableListOf<LineColumnDiagnostic>()
    val games = Sgf.parseAndConvert(input, warnOnMultipleGames) {
        actualDiagnostics.add(it.toLineColumnDiagnostic(lineOffsets))
    }
    assertEquals(expectedDiagnostics, actualDiagnostics)
    return games
}

internal fun checkMoveDisregardExtraInfo(x: Int, y: Int, expectedPlayer: Player, actualMoveInfo: MoveInfo) {
    assertEquals(PositionXY(x, y), actualMoveInfo.positionXY)
    assertEquals(expectedPlayer, actualMoveInfo.player)
}

internal fun GameTreeNode.getNextNode(x: Int, y: Int, fieldStride: Int, player: Player): GameTreeNode? {
    return nextNodes[PositionPlayer(Position(x, y, fieldStride), player)]
}