package org.dots.game

import org.dots.game.dump.FieldParser
import org.dots.game.sgf.SgfParser

object InputTypeDetector {
    const val ZAGRAM_LINK_PREFIX = "https://zagram.org/eidokropki/"
    private const val ZAGRAM_DOWNLOAD_LINK_PREFIX = ZAGRAM_LINK_PREFIX + "backend/download.py?id="
    private const val ID_GROUP_NAME = "id"
    private const val URL_GROUP_NAME = "url"
    private const val URL_PARAMS_GROUP_NAME = "params"

    private val zagramIdRegex = Regex("""zagram\d+""")
    private val zagramDownloadLinkRegex = Regex("""\s*""" + Regex.escape(ZAGRAM_DOWNLOAD_LINK_PREFIX) + """(?<$ID_GROUP_NAME>${zagramIdRegex.pattern})\s*""")
    private val zagramGameViewLinkRegex = Regex("""\s*""" + Regex.escape(ZAGRAM_LINK_PREFIX) + """index\.html#url:(?<$ID_GROUP_NAME>${zagramIdRegex.pattern})\s*""")
    private val thisAppLinkRegex = Regex("""\s*(?<$URL_GROUP_NAME>((${Regex.escape(THIS_APP_LOCAL_URL)}|${Regex.escape(THIS_APP_SERVER_URL)})/?))(?<$URL_PARAMS_GROUP_NAME>\?\S*)?\s*""")
    private val filePathRegex = Regex(""".*[\\/](?!.*[\\/])(.*)""")

    private val nameInParamsRegex = Regex("""${GameSettings::path.name}=(?<$ID_GROUP_NAME>[^&]+)""")
    private val httpRegex = Regex("""\s*https?://""")
    val sgfExtensionRegex = Regex(""".*\.sgfs?\s*$""")

    internal fun getInputType(input: String): InputType {
        if (input.isBlank()) return InputType.Empty

        if (tryParseSgf(input)) return InputType.SgfContent
        if (tryParseField(input)) return InputType.FieldContent

        tryGetInputTypeForPath(input)?.let { return it }

        return InputType.Other
    }

    internal fun tryGetInputTypeForPath(input: String): InputType.InputTypeWithPath? {
        if (input.isBlank()) return null

        if (httpRegex.matchAt(input, 0) != null) {
            val zagramDownloadLinkMatch = zagramDownloadLinkRegex.matchEntire(input)
            if (zagramDownloadLinkMatch != null) {
                return InputType.SgfServerUrl(input.trim(), zagramDownloadLinkMatch.groups[ID_GROUP_NAME]!!.value)
            }

            val zagramGameViewLinkMatch = zagramGameViewLinkRegex.matchEntire(input)
            if (zagramGameViewLinkMatch != null) {
                val id = zagramGameViewLinkMatch.groups[ID_GROUP_NAME]!!.value
                return InputType.SgfServerUrl(ZAGRAM_DOWNLOAD_LINK_PREFIX + id, id)
            }

            val thisAppLinkMatch = thisAppLinkRegex.matchEntire(input)
            if (thisAppLinkMatch != null) {
                val path = thisAppLinkMatch.groups[URL_GROUP_NAME]!!.value
                val paramsGroup = thisAppLinkMatch.groups[URL_PARAMS_GROUP_NAME]
                val urlEncodedName = paramsGroup?.let {
                    nameInParamsRegex.findAll(it.value).firstOrNull()?.groups?.get(ID_GROUP_NAME)?.value
                } ?: ""
                val name = try {
                    UrlEncoderDecoder.decode(urlEncodedName)
                } catch (_: Exception) {
                    ""
                }
                return InputType.SgfClientUrl(input.trim(), name, paramsGroup?.value ?: "", path.length)
            }

            return InputType.OtherUrl(input)
        }

        if (zagramIdRegex.matchEntire(input) != null) {
            return InputType.SgfServerUrl(ZAGRAM_DOWNLOAD_LINK_PREFIX + input, input)
        }

        fun extractFileName(filePath: String): String {
            return filePath.substring(filePath.lastIndexOfAny(charArrayOf('/', '\\')) + 1)
        }

        val refinedPath = input.trim().removeSurrounding("\"")
        val lower = refinedPath.lowercase()
        if (sgfExtensionRegex.matches(lower)) {
            return InputType.SgfFile(refinedPath, extractFileName(refinedPath))
        }

        if (fileExists(refinedPath) || filePathRegex.matchEntire(refinedPath) != null) {
            return InputType.OtherFile(refinedPath, extractFileName(refinedPath))
        }

        return null
    }

    private fun tryParseSgf(input: String): Boolean {
        var sgfContainsAwkwardError = false

        val _ = SgfParser.parse(input) {
            sgfContainsAwkwardError = sgfContainsAwkwardError ||
                    // Missing token errors don't matter a lot because parser can restore on them, and they don't say that the general structure is broken
                    (it.severity >= DiagnosticSeverity.Error && it.textSpan?.let { textSpan -> textSpan.size > 0 } == true)
        }

        return !sgfContainsAwkwardError
    }

    private fun tryParseField(input: String): Boolean {
        var fieldContainsError = false

        val _ = FieldParser.parse(input) {
            fieldContainsError = fieldContainsError || it.severity >= DiagnosticSeverity.Error
        }

        return !fieldContainsError
    }
}

sealed class InputType {
    sealed class Content : InputType()
    object SgfContent : Content()
    object FieldContent : Content()

    sealed class InputTypeWithPath(val refinedPath: String, val name: String, val isIncorrect: Boolean) : InputType() {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as InputTypeWithPath
            return isIncorrect == other.isIncorrect && refinedPath == other.refinedPath && name == other.name
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + isIncorrect.hashCode()
            result = 31 * result + refinedPath.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }
    }

    sealed class File(refinedPath: String, name: String, isIncorrect: Boolean = false) : InputTypeWithPath(refinedPath, name, isIncorrect)
    class SgfFile(refinedPath: String, name: String, isIncorrect: Boolean = false) : File(refinedPath, name, isIncorrect)
    class OtherFile(refinedPath: String, name: String) : File(refinedPath, name, true)

    sealed class Url(refinedPath: String, name: String, isIncorrect: Boolean = false) : InputTypeWithPath(refinedPath, name, isIncorrect)
    class SgfServerUrl(refinedPath: String, name: String, isIncorrect: Boolean = false) : Url(refinedPath, name, isIncorrect)
    class SgfClientUrl(refinedPath: String, name: String, val params: String, val paramsOffset: Int, isIncorrect: Boolean = false) : Url(refinedPath, name, isIncorrect) {
        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) return false
            other as SgfClientUrl
            return params == other.params && paramsOffset == other.paramsOffset
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + params.hashCode()
            result = 31 * result + paramsOffset.hashCode()
            return result
        }
    }
    class OtherUrl(path: String) : Url(path, "", true)

    object Empty : InputType()
    object Other : InputType()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}