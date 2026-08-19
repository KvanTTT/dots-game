package org.dots.game

import org.dots.game.core.Games
import org.dots.game.core.InitPosGenType
import org.dots.game.core.Rules
import org.dots.game.dump.FieldParser
import org.dots.game.sgf.Sgf

object GameLoader {
    /**
     * The maximum number of files that are checked on a directory traversal.
     * It protects from freezing on a huge directory that can be entered by accident
     * (any intermediate directory is entered while a path is being typed).
     */
    const val MAX_SCANNED_FILES_COUNT = 10000

    /** The maximum number of sgf(s) files that are merged into a single sgf content. */
    const val MAX_MERGED_FILES_COUNT = 1000

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
                                random = rules?.random ?: Rules.Standard.random,
                                initPosGenType = InitPosGenType.Static,
                                komi = rules?.komi ?: Rules.Standard.komi,
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

                is InputType.SgfDirectory -> {
                    sgfContent = readAndMergeSgfFiles(inputType, diagnosticReporter)
                }

                is InputType.OtherFile -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(
                            Diagnostic("Incorrect file `${inputType.name}`. The only .sgf and .sgfs files or directories with them are supported", textSpan = null),
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
                        GameLoaderDiagnostic(Diagnostic("Insert a path to .sgf(s) file, a directory with such files or a link to zagram.org game", textSpan = null), isContent = false))
                    sgfContent = null
                }

                is InputType.Other -> {
                    diagnosticReporter(
                        GameLoaderDiagnostic(Diagnostic("Unrecognized input type. Insert a path to .sgf(s) file, a directory with such files or a link to zagram.org game", textSpan = null), isContent = false))
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

    /**
     * Traverses [directory] recursively and merges the content of all found sgf(s) files into a single sgf content.
     * Returns `null` if there is nothing to merge.
     */
    private fun readAndMergeSgfFiles(
        directory: InputType.SgfDirectory,
        diagnosticReporter: (GameLoaderDiagnostic) -> Unit,
    ): String? {
        fun report(message: String, severity: DiagnosticSeverity) {
            diagnosticReporter(GameLoaderDiagnostic(Diagnostic(message, textSpan = null, severity), isContent = false))
        }

        // Take one extra file to detect that the directory is bigger than the limit
        val scannedFiles = listFilesRecursively(directory.refinedPath).take(MAX_SCANNED_FILES_COUNT + 1).toList()
        if (scannedFiles.size > MAX_SCANNED_FILES_COUNT) {
            report(
                "The directory `${directory.name}` contains more than $MAX_SCANNED_FILES_COUNT files, the rest of them are not scanned",
                DiagnosticSeverity.Warning
            )
        }

        val sgfFiles = scannedFiles.asSequence()
            .take(MAX_SCANNED_FILES_COUNT)
            .filter { InputTypeDetector.sgfExtensionRegex.matches(it.lowercase()) }
            // Sort to get the same merged content on every loading (a traversal order is file system dependent)
            .sorted()
            .toList()

        if (sgfFiles.isEmpty()) {
            report("The directory `${directory.name}` doesn't contain .sgf or .sgfs files", DiagnosticSeverity.Error)
            return null
        }

        val filesToMerge = if (sgfFiles.size > MAX_MERGED_FILES_COUNT) {
            report(
                "The directory `${directory.name}` contains ${sgfFiles.size} sgf(s) files, the only first $MAX_MERGED_FILES_COUNT of them are used",
                DiagnosticSeverity.Warning
            )
            sgfFiles.take(MAX_MERGED_FILES_COUNT)
        } else {
            sgfFiles
        }

        val mergedContent = StringBuilder()
        var mergedFilesCount = 0
        for (file in filesToMerge) {
            val content = try {
                readFileText(file)
            } catch (e: Exception) {
                report("Unable to read the file `$file`: ${e.message ?: e.toString()}", DiagnosticSeverity.Error)
                continue
            }
            val trimmedContent = content.trim()
            if (trimmedContent.isEmpty()) continue
            mergedContent.append(trimmedContent).append('\n')
            mergedFilesCount++
        }

        if (mergedFilesCount == 0) {
            report("All sgf(s) files of the directory `${directory.name}` are empty or unreadable", DiagnosticSeverity.Error)
            return null
        }

        report("Merged $mergedFilesCount sgf(s) file(s) of the directory `${directory.name}`", DiagnosticSeverity.Info)

        return mergedContent.toString()
    }
}

class LoadResult(
    val inputType: InputType,
    val content: String?,
    val games: Games,
)
