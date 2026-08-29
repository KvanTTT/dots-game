package org.dots.game.localization

import org.dots.game.core.BaseMode
import org.dots.game.core.InitPosGenType
import org.dots.game.core.InitPosType
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.KataGoDotsSettingsFileType
import org.dots.game.views.PolygonDrawMode

object EnglishStrings : Strings {
    override fun boolToString(bool: Boolean): String = if (bool) "Yes" else "No"

    // Common UI
    override val new = "New"
    override val reset = "Reset"
    override val load = "Load"
    override val save = "Save"
    override val saveAs = "Save As"
    override val settings = "Settings"
    override val open = "Open"
    override val browse = "Browse"
    override val aiSettings = "AI Settings"

    // Game info
    override val width = "Width"
    override val height = "Height"
    override val move = "Move"
    override val game = "Game"
    override val komi = "Komi"
    override val firstPlayerDefaultName = "First"
    override val secondPlayerDefaultName = "Second"

    // New Game Dialog
    override val initPosType = "Init Pos Type"
    override val baseMode = "Base Mode"
    override val initPosGenType = "Generation Type"
    override val captureByBorder = "Capture by border"
    override val suicideAllowed = "Suicide allowed"
    override val drawIsAllowed = "Draw is allowed"
    override val createNewGame = "Create game"

    override fun initPosTypeLabel(type: InitPosType): String = when (type) {
        InitPosType.Empty -> "Empty"
        InitPosType.Single -> "Single"
        InitPosType.Cross -> "Cross"
        InitPosType.DoubleCross -> "Double Cross"
        InitPosType.QuadrupleCross -> "Quadruple Cross"
        InitPosType.Custom -> "Custom"
    }

    override fun baseModeLabel(mode: BaseMode): String = when (mode) {
        BaseMode.AtLeastOneOpponentDot -> "At Least One Opponent Dot"
        BaseMode.AnySurrounding -> "Any Surrounding"
        BaseMode.OnlyOpponentDots -> "Only Opponent Dots (like Go game)"
    }

    override fun initPosGenTypeLabel(type: InitPosGenType): String = when (type) {
        InitPosGenType.Static -> "Static"
        InitPosGenType.RandomNotago -> "Random (Notago)"
        InitPosGenType.RandomMarlov -> "Random (Marlov)"
    }

    // Open Dialog
    override val pathOrContent = "Path or Content"
    override val pathOrContentPlaceholder = "Enter path to .sgf(s) file, to a directory with such files or its content"
    override val rewindToEnd = "Rewind to End"
    override val addFinishingMove = "Add Finishing Move"
    override val openSgfFile = "Open SGF File"

    // Save Dialog
    override val sgf = "SGF"
    override val fieldRepresentation = "Field Representation"
    override val printNumbers = "Print numbers"
    override val printCoordinates = "Print coordinates"
    override val debugInfo = "Debug info"
    override val padding = "Padding"
    override val path = "Path"
    override val link = "Link"
    override val copy = "Copy"
    override val saveDialogTitle = "Save game"
    override val refine = "Refine"
    override val refinementIsFailed = "The games can't be refined, thus they are not saved"
    override val tooLongLinkMessage = "Unavailable (too long link)"

    // Settings Dialog
    override val connectionDrawMode = "Connection Draw Mode"
    override val polygonDrawMode = "Polygon Draw Mode"
    override val diagonalConnections = "Diagonal Connections"
    override val threats = "Threats"
    override val surroundings = "Surroundings"
    override val developerMode = "Developer Mode"
    override val experimentalMode = "Experimental Mode"
    override val version: String = "Version"

    // AI Settings
    override fun aiSettingsFilePath(fileType: KataGoDotsSettingsFileType): String {
        return "$fileType file"
    }
    override fun aiSettingsSelectFile(fileType: KataGoDotsSettingsFileType): String {
        return "Select${fileType.extensions.filter { it.isNotEmpty() }.joinToString(",") { " .${it}" }} file"
    }
    override val default: String = "Default"
    override val initialization: String = "Initialization..."
    override val initialize: String = "Initialize"

    override fun connectionDrawModeLabel(mode: ConnectionDrawMode): String = when (mode) {
        ConnectionDrawMode.None -> "None"
        ConnectionDrawMode.Lines -> "Lines"
        ConnectionDrawMode.PolygonOutline -> "Polygon Outline"
        ConnectionDrawMode.PolygonFill -> "Polygon Fill"
        ConnectionDrawMode.PolygonOutlineAndFill -> "Polygon Outline And Fill"
    }

    override fun polygonDrawModeLabel(mode: PolygonDrawMode): String = when (mode) {
        PolygonDrawMode.Outline -> "Outline"
        PolygonDrawMode.Fill -> "Fill"
        PolygonDrawMode.OutlineAndFill -> "Outline And Fill"
    }

    override val language = "Language"
    override val languageName = "English"

    override val nextPlayer = "Next player"
    override val ground = "Ground"
    override val resign = "Resign"
    override val nextGame = "Next game"
    override val previousGame = "Previous game"
    override val aiMove = "AI move"
    override val aiThinking = "AI is thinking..."
    override val aiMoveDescription = """Makes the engine move for the player to move.
Long press to turn on the auto move mode, in which the engine answers every move automatically."""
    override val autoMoveDescription = """The auto move mode is on: the engine answers every move automatically.
Long press to turn it off."""

    override val analyzing = "Analyzing..."
    override val moveAnalysis = "Move Analysis"
    override val moveAnalysisDescription = """Every candidate move is evaluated for the player to move.
Green marks the best moves, red the worst ones (both the win rate and the score lead are taken into account).
The more transparent a move is, the fewer visits it got, thus the less reliable its evaluation is."""
    override val scoreLead = "Score Lead"
    override val prior = "Prior"
    override val variation = "Variation"
    override val utility = "Utility"
    override val lowerConfidenceBound = "Lower Confidence Bound"
    override val deviation = "Deviation"
    override val edgeVisits = "Edge Visits"
    override val symmetryOf = "Symmetry of"
    override val candidateMoves = "Candidate Moves"
    override val candidateMovesDescription = """Highlights the moves the engine considers on the field, the best one is circled.
Point at a move to see its win rate.
The evaluation of every shown move is still listed below no matter whether the highlighting is on."""
    override val ownership = "Ownership"
    override val ownershipDescription = """Shades every position with the color of the player who is expected to capture it.
The more saturated a position is, the more certain the engine is about its owner.
Point at a position to see its exact value."""
    override fun moreAnalyzedMoves(count: Int): String = "and $count more"

    override val winRate = "Win Rate"
    override val score = "Score"
    override val weight = "Weight"
    override val visits = "Visits"

    override val winRateDescription = """100% - Second player wins
50% - Draw
0% - First player wins"""
    override val scoreDescription = """> 0 Second player wins
= 0 Draw
< 0 First player wins"""
    override val weightDescription = "The more the weight, the more important this move is for training"

    override val sgfComment = "SGF Comment"
    override val sgfStats = "SGF Stats"
    override val avgRemainingMoves = "Remaining Moves (Average)"
    override val avgRemainingMovesComment = """The closer the value is to 100%, the more refined the games are.
Values closer to zero mean that games were played almost until all legal moves were exhausted.
Typically, such games are unrealistic and "garbage" because humans don't play until the very end (they usually resign much earlier)."""
    override val draws = "Draws"

    override val result = "Result"
    override val reason = "Reason"
    override val draw = "Draw"
    override val win = "win"
    override val interrupt = "interrupt"
    override val resignation = "resignation"
    override val time = "time"
    override val unknown = "unknown"
}
