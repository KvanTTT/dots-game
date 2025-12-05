package org.dots.game

import org.dots.game.core.Games
import org.dots.game.core.Rules
import org.dots.game.dump.FieldParser
import org.dots.game.sgf.Sgf

object GameLoader {
    data class GameLoaderDiagnostic(val diagnostic: Diagnostic, val isContent: Boolean) {
        override fun toString(): String = diagnostic.toString()
    }

    /**
     * [rules] can be used when parsing raw fields that don't have extra info about rules.
     */
    suspend fun openOrLoad(
        pathOrContent: String,
        rules: Rules?,
        addFinishingMove: Boolean,
        diagnosticReporter: ((GameLoaderDiagnostic) -> Unit) = { println(it) }
    ): LoadResult {
        try {
            val inputType = InputTypeDetector.getInputType(pathOrContent)
            var sgfContent: String?

            when (inputType) {
                InputType.FieldContent -> {
                    val field = FieldParser.parseAndConvert(
                        pathOrContent,
                        initializeRules = { width, height ->
                            Rules.createAndDetectInitPos(
                                width,
                                height,
                                captureByBorder = rules?.captureByBorder ?: Rules.Standard.captureByBorder,
                                baseMode = rules?.baseMode ?: Rules.Standard.baseMode,
                                suicideAllowed = rules?.suicideAllowed ?: Rules.Standard.suicideAllowed,
                                initialMoves = emptyList(),
                                komi = Rules.Standard.komi,
                            ).rules
                        }, diagnosticReporter = {
                            diagnosticReporter(GameLoaderDiagnostic(it, isContent = true))
                        }
                    )
                    return LoadResult(inputType, content = pathOrContent, Games.fromField(field))
                }

                InputType.SgfContent -> {
                    sgfContent = pathOrContent
                }

                is InputType.SgfFile -> {
                    sgfContent = readFileText(inputType.refinedPath)
                }

                is InputType.OtherFile -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(
                            Diagnostic("Incorrect file `${inputType.name}`. The only .sgf and .sgfs files are supported", textSpan = null),
                            isContent = false
                        )
                    )
                    sgfContent = null
                }

                is InputType.SgfServerUrl -> {
                    sgfContent = downloadFileText(inputType.refinedPath)
                }

                is InputType.SgfClientUrl -> {
                    val gameSettings = GameSettings.parseUrlParams(inputType.params, inputType.paramsOffset) {
                        diagnosticReporter(GameLoaderDiagnostic(it, isContent = false))
                    }
                    sgfContent = gameSettings.sgf
                }

                is InputType.OtherUrl -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Incorrect url. The only `${InputTypeDetector.ZAGRAM_LINK_PREFIX}` and `$THIS_APP_SERVER_URL` are supported", textSpan = null), isContent = false))
                    sgfContent = null
                }

                is InputType.Empty -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Insert a path to .sgf(s) file or a link to zagram.org game", textSpan = null), isContent = false))
                    sgfContent = null
                }

                is InputType.Other -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Unrecognized input type. Insert a path to .sgf(s) file or a link to zagram.org game", textSpan = null), isContent = false))
                    sgfContent = null
                }
            }

            return LoadResult(inputType, sgfContent, sgfContent?.let {
                Sgf.parseAndConvert(it, onlySingleGameSupported = false, addFinishingMove = addFinishingMove, diagnosticReporter = { diagnostic ->
                    diagnosticReporter(GameLoaderDiagnostic(diagnostic, isContent = true))
                })
            } ?: Games())
        } catch (e: Exception) {
            diagnosticReporter(
                GameLoaderDiagnostic(Diagnostic(e.message ?: e.toString(), textSpan = null, DiagnosticSeverity.Critical), isContent = false)
            )
        }
        return LoadResult(InputType.Other, pathOrContent, Games())
    }
}

class LoadResult(
    val inputType: InputType,
    val content: String?,
    val games: Games,
)
