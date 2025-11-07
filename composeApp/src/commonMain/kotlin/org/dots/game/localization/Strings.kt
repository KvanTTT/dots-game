package org.dots.game.localization

import org.dots.game.core.BaseMode
import org.dots.game.core.InitPosType
import org.dots.game.views.ConnectionDrawMode
import org.dots.game.views.PolygonDrawMode

/**
 * Localization strings interface.
 * Pure Kotlin implementation that works across all platforms including WASM.
 */
interface Strings {
    // Common UI
    val new: String
    val reset: String
    val load: String
    val save: String
    val settings: String
    val open: String
    val browse: String
    val create: String
    val aiSettings: String

    // Game info
    val width: String
    val height: String
    val move: String
    val game: String
    val komi: String

    // New Game Dialog
    val initPosType: String
    val baseMode: String
    val captureByBorder: String
    val suicideAllowed: String
    val roundDraw: String
    val createNewGame: String
    val randomStartPosition: String

    // InitPosType enum labels
    fun initPosTypeLabel(type: InitPosType): String

    // BaseMode enum labels
    fun baseModeLabel(mode: BaseMode): String

    // Open Dialog
    val pathOrContent: String
    val pathOrContentPlaceholder: String
    val rewindToEnd: String
    val addFinishingMove: String
    val openSgfFile: String

    // Save Dialog
    val sgf: String
    val fieldRepresentation: String
    val printNumbers: String
    val printCoordinates: String
    val debugInfo: String
    val padding: String

    // Settings Dialog
    val connectionDrawMode: String
    val polygonDrawMode: String
    val diagonalConnections: String
    val threats: String
    val surroundings: String
    val developerMode: String

    // ConnectionDrawMode enum labels
    fun connectionDrawModeLabel(mode: ConnectionDrawMode): String

    // PolygonDrawMode enum labels
    fun polygonDrawModeLabel(mode: PolygonDrawMode): String

    // Language settings
    val language: String
    val languageName: String
}
