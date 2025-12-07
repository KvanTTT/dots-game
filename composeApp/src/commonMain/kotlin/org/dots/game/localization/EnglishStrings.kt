package org.dots.game.localization

import org.dots.game.core.BaseMode
import org.dots.game.core.InitPosType
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.KataGoDotsSettingsFileType
import org.dots.game.views.PolygonDrawMode

object EnglishStrings : Strings {
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
    override val captureByBorder = "Capture by border"
    override val suicideAllowed = "Suicide allowed"
    override val drawIsAllowed = "Draw is allowed"
    override val createNewGame = "Create game"
    override val randomStartPosition = "Random start position"

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

    // Open Dialog
    override val pathOrContent = "Path or Content"
    override val pathOrContentPlaceholder = "Enter path to .sgf(s) file or its content"
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
    override val firstPlayer = "Player 1"
    override val secondPlayer = "Player 2"
    override val ground = "Ground"
    override val resign = "Resign"
    override val nextGame = "Next game"
    override val previousGame = "Previous game"
    override val aiMove = "AI move"
    override val aiThinking = "AI is thinking..."
    override val autoMove = "Auto"
}
