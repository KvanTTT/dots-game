package org.dots.game

import org.dots.game.sgf.TextSpan

data class Diagnostic(
    val message: String,
    val textSpan: TextSpan?,
    val severity: DiagnosticSeverity = DiagnosticSeverity.Error,
) {
    override fun toString(): String = render(message, textSpan, severity)
}

enum class DiagnosticSeverity {
    Warning,
    Error,
    Critical,
}

data class LineColumnDiagnostic(
    val message: String,
    val lineColumn: LineColumn?,
    val severity: DiagnosticSeverity = DiagnosticSeverity.Error,
) {
    override fun toString(): String = render(message, lineColumn, severity)
}

fun Diagnostic.toLineColumnDiagnostic(lineOffsets: List<Int>): LineColumnDiagnostic =
    LineColumnDiagnostic(message, textSpan?.start?.getLineColumn(lineOffsets), severity)

private fun render(message: String, textSpan: Any?, severity: DiagnosticSeverity): String {
    val lineColumnSuffix = if (textSpan != null) " at $textSpan" else ""
    return "$severity$lineColumnSuffix: $message"
}