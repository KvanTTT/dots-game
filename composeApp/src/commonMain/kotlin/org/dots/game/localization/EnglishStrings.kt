package org.dots.game.localization

import org.dots.game.core.BaseMode
import org.dots.game.core.InitPosType
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.PolygonDrawMode

object EnglishStrings : Strings {
    // Common UI
    override val new = "New"
    override val reset = "Reset"
    override val load = "Load"
    override val save = "Save"
    override val settings = "Settings"
    override val open = "Open"
    override val browse = "Browse"
    override val create = "Create"
    override val aiSettings = "AI Settings"

    // Game info
    override val width = "Width"
    override val height = "Height"
    override val move = "Move"
    override val game = "Game"
    override val komi = "Komi"

    // New Game Dialog
    override val initPosType = "Init Pos Type"
    override val baseMode = "Base Mode"
    override val captureByBorder = "Capture by border"
    override val suicideAllowed = "Suicide allowed"
    override val roundDraw = "Round Draw"
    override val createNewGame = "Create new game"
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
        BaseMode.AllOpponentDots -> "All Opponent Dots"
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

    // Settings Dialog
    override val connectionDrawMode = "Connection Draw Mode"
    override val polygonDrawMode = "Polygon Draw Mode"
    override val diagonalConnections = "Diagonal Connections"
    override val threats = "Threats"
    override val surroundings = "Surroundings"
    override val developerMode = "Developer Mode"

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
}
