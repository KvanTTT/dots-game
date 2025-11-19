package org.dots.game.sgf

import org.dots.game.DiagnosticSeverity
import org.dots.game.LineColumn
import org.dots.game.LineColumnDiagnostic
import org.dots.game.buildLineOffsets
import org.dots.game.core.AppInfo
import org.dots.game.core.AppType
import org.dots.game.core.BaseMode
import org.dots.game.core.GameResult
import org.dots.game.core.GameTreeNode
import org.dots.game.core.Games
import org.dots.game.core.InitPosType
import org.dots.game.core.MoveInfo
import org.dots.game.core.MoveInfo.Companion.IgnoreParseNodeComparator
import org.dots.game.core.Player
import org.dots.game.core.PositionXY
import org.dots.game.core.Rules
import org.dots.game.toLineColumnDiagnostic
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.collections.single
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SgfConverterTests {
    @Test
    fun gameInfo() {
        val games = checkParseAndUnparse(sgfTestDataWithFullInfo)
        val game = games.single()
        with (game) {
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
        val games = checkParseAndUnparse(multiGamesSgf)
        val game0 = games[0]
        assertEquals(39, game0.rules.width)
        assertEquals(32, game0.rules.height)
        assertEquals("game 1", game0.gameName)
        val game1 = games[1]
        assertEquals(20, game1.rules.width)
        assertEquals(20, game1.rules.height)
        assertEquals("game 2", game1.gameName)
    }

    @Test
    fun multipleGamesWarning() {
        checkParseAndUnparse(
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
            checkParseAndUnparse(
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
        checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[0])", listOf(
                LineColumnDiagnostic(
                    "Property SZ (Size) has zero value.",
                    LineColumn(1, 17),
                    DiagnosticSeverity.Warning
                )
            )
        )
        checkParseAndUnparse(
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
        checkParseAndUnparse("(;GM[40]FF[4]SZ[20])")
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
    fun majorPropertiesAreMissing() {
        assertTrue(
            parseConvertAndCheck(
                "(;)", listOf(
                    LineColumnDiagnostic(
                        "Property GM (Game Mode) must be specified.",
                        LineColumn(1, 3),
                        DiagnosticSeverity.Error
                    ),
                    LineColumnDiagnostic(
                        "Property FF (File Format) is recommended to be specified.",
                        LineColumn(1, 3),
                        DiagnosticSeverity.Warning
                    ),
                    LineColumnDiagnostic(
                        "Property SZ (Size) must be specified.",
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
    fun incorrectFormatWarnings() {
        val gameInfo = checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]BR[asdf])", listOf(
                LineColumnDiagnostic(
                    "Property BR (Player1 Rating) has incorrect format: `asdf`. Expected: Real Number.",
                    LineColumn(1, 26),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single()
        assertNull(gameInfo.player1Rating)
    }

    @Test
    fun playdotsSgf() {
        val gameInfo = checkParseAndUnparse(
            "(;AP[Спортивные Точки (playdots.ru)]GM[40]FF[4]SZ[39:32]BR[Нет звания, 1200]WR[Второй разряд, 1300])"
        ).single()

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
            val actualGameResult = checkParseAndUnparse(
                "(;GM[40]FF[4]SZ[39:32]RE[$value])",
                listOf(diagnostic)
            ).single().result
            if (expectedGameResult != null) {
                assertEquals(expectedGameResult, actualGameResult)
            }
        }
    }

    @Test
    fun gameResultIsValid() {
        val valuesToGameResults = listOf(
            Triple("0", GameResult.Draw(endGameKind = null, player = null), null),
            Triple("Draw", GameResult.Draw(endGameKind = null, player = null), null),
            Triple("B+R", GameResult.ResignWin(Player.First), null),
            Triple("B+T" , GameResult.TimeWin(Player.First), null),
            Triple("B+?" , GameResult.UnknownWin(Player.First), null),
            Triple("B+10" , GameResult.ScoreWin(10.0, endGameKind = null, Player.First, player = null),
                LineColumnDiagnostic(
                    "Property RE (Result) has value `10` that doesn't match score from game field `0`.",
                    LineColumn(1, 32),
                    DiagnosticSeverity.Warning
                )
            ),
            Triple("W+5" , GameResult.ScoreWin(5.0, endGameKind = null, Player.Second, player = null),
                LineColumnDiagnostic(
                    "Property RE (Result) has value `5` that doesn't match score from game field `0`.",
                    LineColumn(1, 31),
                    DiagnosticSeverity.Warning
                )
            ),
        )

        for ((value, expectedGameResult, diagnostic) in valuesToGameResults) {
            val actualGameResult = checkParseAndUnparse(
                "(;GM[40]FF[4]SZ[39:32]RE[$value])",
                listOfNotNull(diagnostic)
            ).single().result
            assertEquals(expectedGameResult, actualGameResult)
        }
    }

    @Test
    fun komi() {
        val game = checkParseAndUnparse("(;GM[40]FF[4]SZ[39:32]KM[0.5]RE[W+0.5])").single()
        assertEquals(0.5, game.komi)
        checkParseAndUnparse(
            "(;GM[40]FF[4]SZ[39:32]KM[0]RE[W+0.5])",
            listOfNotNull(
                LineColumnDiagnostic(
                    "Property RE (Result) has value `0.5` that doesn't match score from game field `0`.",
                    LineColumn(1, 38),
                    DiagnosticSeverity.Warning
                ),
            )
        ).single().result
    }

    @Test
    fun handicap() {
        val zeroHandicapAndEmpty = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[0];B[on])").single()
        assertEquals(0, zeroHandicapAndEmpty.handicap)
        val doubleHandicapAndEmpty = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[2];B[on];B[pq];W[ss])").single()
        assertEquals(2, doubleHandicapAndEmpty.handicap)
        val zeroHandicapAndCross = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[0]AB[jj][kk]AW[kj][jk];B[on])").single()
        assertEquals(0, zeroHandicapAndCross.handicap)
        val doubleHandicapAndCross = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[2]AB[oi][jj][kk]AW[kj][jk];B[on])").single()
        assertEquals(2, doubleHandicapAndCross.handicap)
    }

    @Test
    fun handicapIncorrect() {
        checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[x]AB[jj][kk]AW[kj][jk])", listOf(
            LineColumnDiagnostic("Property HA (Handicap) has incorrect format: `x`. Expected: Number.", LineColumn(1, 23), DiagnosticSeverity.Warning),
        )).single()

        // Handicap always starts with 2 dots (as for Go Game)
        val zeroHandicapAndEmpty = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[0];B[on];B[pq];W[ss])", listOf(
            LineColumnDiagnostic("Property HA (Handicap) has `0` value but expected value from field is `2`", LineColumn(1, 22), DiagnosticSeverity.Warning),
        )).single()

        assertEquals(0, zeroHandicapAndEmpty.handicap) // Preserve the value from game info
        val singleHandicapAndEmpty = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[1];B[on];B[pq];W[ss])", listOf(
            LineColumnDiagnostic("Property HA (Handicap) has `1` value but expected value from field is `2`", LineColumn(1, 22), DiagnosticSeverity.Warning),
        )).single()
        assertEquals(1, singleHandicapAndEmpty.handicap)

        val tripleFromPropertyButDoubleFromField = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]HA[3]AB[jj][kk][on]AW[kj][jk];B[mo];W[ck])", listOf(
            LineColumnDiagnostic("Property HA (Handicap) has `3` value but expected value from field is `2`", LineColumn(1, 22), DiagnosticSeverity.Warning),
        )).single()
        assertEquals(3, tripleFromPropertyButDoubleFromField.handicap)
    }

    @Test
    fun remainingInitMoves() {
        val gameTree = checkParseAndUnparse("(;GM[40]FF[4]SZ[20]AB[jj][kk][bb]AW[kj][jk])").single().gameTree
        val field = gameTree.field
        assertEquals(4, field.rules.initialMoves.size)
        assertEquals(0, IgnoreParseNodeComparator.compare(MoveInfo(PositionXY(2, 2,), Player.First), field.rules.remainingInitMoves.single()))

        // Check the remaining init dot is placed
        with(field) {
            assertEquals(Player.First, field.getPositionIfWithinBounds(2, 2)!!.getState().getActivePlayer())
        }

        // Check there are no real moves
        assertEquals(gameTree.rootNode, gameTree.currentNode)
    }

    @Test
    fun kataGoRules() {
        val configuredRules = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[dotsCaptureEmptyBase1sui1])").single().rules
        assertEquals(BaseMode.AnySurrounding, configuredRules.baseMode)
        assertTrue(configuredRules.suicideAllowed)

        val configuredRulesReversed = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[sui1dotsCaptureEmptyBase1])").single().rules
        assertEquals(BaseMode.AnySurrounding, configuredRulesReversed.baseMode)
        assertTrue(configuredRulesReversed.suicideAllowed)

        val nonConfiguredRules = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[dotsCaptureEmptyBase0sui0])").single().rules
        assertEquals(BaseMode.AtLeastOneOpponentDot, nonConfiguredRules.baseMode)
        assertFalse(nonConfiguredRules.suicideAllowed)

        val singleExtraRule = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[dotsCaptureEmptyBase1])").single().rules
        assertEquals(BaseMode.AnySurrounding, singleExtraRule.baseMode)
        assertEquals(Rules.Standard.suicideAllowed, singleExtraRule.suicideAllowed)

        val detectInitPosRules = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[4]RU[startPosIsRandom0]AB[bb][cc]AW[cb][bc])").single().rules
        assertEquals(InitPosType.Cross, detectInitPosRules.initPosType)
        assertFalse(detectInitPosRules.initPosIsRandom)
    }

    @Test
    fun kataGoRulesIncorrect() {
        val rulesWithIncorrectValues = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[dotsCaptureEmptyBaseXsuiX])", listOf(
            LineColumnDiagnostic("Property RU (Rules) Invalid value `X`. Expected: `0` or `1`.", LineColumn(1, 56), DiagnosticSeverity.Error),
        )).single().rules
        assertEquals(Rules.Standard.baseMode, rulesWithIncorrectValues.baseMode)
        assertEquals(Rules.Standard.suicideAllowed, rulesWithIncorrectValues.suicideAllowed)

        val rulesWithIncorrectKeys = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[error0error1])", listOf(
            LineColumnDiagnostic("Property RU (Rules) Unrecognized KataGo key `error0error1`.", LineColumn(1, 36), DiagnosticSeverity.Error),
        )).single().rules
        assertEquals(Rules.Standard.baseMode, rulesWithIncorrectKeys.baseMode)
        assertEquals(Rules.Standard.suicideAllowed, rulesWithIncorrectValues.suicideAllowed)

        val rulesPartiallyCorrect = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[39:32]RU[dotsCaptureEmptyBase1suiX])", listOf(
            LineColumnDiagnostic("Property RU (Rules) Invalid value `X`. Expected: `0` or `1`.", LineColumn(1, 60), DiagnosticSeverity.Error),
        )).single().rules
        assertEquals(BaseMode.AnySurrounding, rulesPartiallyCorrect.baseMode)
        assertEquals(Rules.Standard.suicideAllowed, rulesWithIncorrectValues.suicideAllowed)

        val randomInitPosFromMovesButNotRandomFromRules = checkParseAndUnparse("(;GM[40]FF[4]AP[katago]SZ[4]RU[startPosIsRandom0]AB[cb][bc]AW[bb][cc])", listOf(
            LineColumnDiagnostic(
                "Property RU (Rules) specifies strict `Cross` but random is detected.",
                LineColumn(1, 29),
                DiagnosticSeverity.Warning
            )
        )).single().rules
        assertEquals(InitPosType.Cross, randomInitPosFromMovesButNotRandomFromRules.initPosType)
        assertTrue(randomInitPosFromMovesButNotRandomFromRules.initPosIsRandom)
    }

    @Test
    fun thisAppRulesCorrect() {
        val defaultRules = checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[])").single().rules
        assertEquals(Rules.Standard.captureByBorder, defaultRules.captureByBorder)
        assertEquals(Rules.Standard.suicideAllowed, defaultRules.suicideAllowed)
        assertEquals(Rules.Standard.baseMode, defaultRules.baseMode)
        assertEquals(Rules.Standard.initPosIsRandom, defaultRules.initPosIsRandom)

        val rules = checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[Border,Suicide=0,BaseMode=2,StartIsRandom=1,,])").single().rules
        assertTrue(rules.captureByBorder)
        assertFalse(rules.suicideAllowed)
        assertEquals(BaseMode.AllOpponentDots, rules.baseMode)
        assertTrue(rules.initPosIsRandom)
    }

    @Test
    fun thisAppRulesIncorrect() {
        checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[IncorrectKey=1,Suicide=0,IncorrectKey2])", listOf(
            LineColumnDiagnostic("Property RU (Rules) has unrecognized or unspecified key `IncorrectKey`.", LineColumn(1, 38), DiagnosticSeverity.Error),
            LineColumnDiagnostic("Property RU (Rules) has unrecognized or unspecified key `IncorrectKey2`.", LineColumn(1, 63), DiagnosticSeverity.Error),
        )).single().rules

        checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[Suicide=IncorrectBooleanValue,Border=1,BaseMode=IncorrectIntValue])", listOf(
            LineColumnDiagnostic("Property RU (Rules) has key `Suicide` with invalid value `IncorrectBooleanValue`. Expected: `0` or `1`.", LineColumn(1, 46), DiagnosticSeverity.Error),
            LineColumnDiagnostic("Property RU (Rules) has key `BaseMode` with invalid or out of range value `IncorrectIntValue`. Expected: 0, 1, 2.", LineColumn(1, 86), DiagnosticSeverity.Error),
        )).single().rules

        checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[BaseMode=,Border=1])", listOf(
            LineColumnDiagnostic(
                "Property RU (Rules) has key `BaseMode` with invalid or out of range value ``. Expected: 0, 1, 2.",
                LineColumn(1, 47), DiagnosticSeverity.Error
            )
        ))

        checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[Border=0,Border=1])", listOf(
            LineColumnDiagnostic(
                "Property RU (Rules) has duplicated key `Border`.",
                LineColumn(1, 47), DiagnosticSeverity.Warning
            )
        ))

        checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[=0])", listOf(
            LineColumnDiagnostic(
                "Property RU (Rules) has unrecognized or unspecified key ``.",
                LineColumn(1, 38), DiagnosticSeverity.Error
            )
        ))

        checkParseAndUnparse("(;GM[40]FF[4]AP[DotsGame]SZ[39:32]RU[StartIsRandom=0]AB[cb][bc]AW[bb][cc])", listOf(
            LineColumnDiagnostic(
                "Property RU (Rules) specifies strict `Cross` but random is detected.",
                LineColumn(1, 35), DiagnosticSeverity.Warning
            )
        ))
    }
}

internal fun checkParseAndUnparse(input: String, expectedDiagnostics: List<LineColumnDiagnostic> = emptyList(), warnOnMultipleGames: Boolean = false): Games {
    val games = parseConvertAndCheck(input, expectedDiagnostics, warnOnMultipleGames)
    assertEquals(input, SgfWriter.write(games))
    return games
}

internal fun parseConvertAndCheck(input: String, expectedDiagnostics: List<LineColumnDiagnostic> = emptyList(), warnOnMultipleGames: Boolean = false): Games {
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

internal fun GameTreeNode.getNextNode(x: Int, y: Int, player: Player): GameTreeNode? {
    val moveInfo = MoveInfo(PositionXY(x, y), player)

    for (child in children) {
        if (player == Player.First && child.player1Moves?.singleOrNull()?.let { IgnoreParseNodeComparator.compare(moveInfo, it) == 0 } == true ||
            player == Player.Second && child.player2Moves?.singleOrNull()?.let { IgnoreParseNodeComparator.compare(moveInfo, it) == 0 } == true
        ) {
            return child
        }
    }
    return null
}