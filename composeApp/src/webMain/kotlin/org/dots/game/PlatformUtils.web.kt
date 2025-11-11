package org.dots.game

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.fetch.Response

/**
 * External JavaScript function to encode a URI component.
 * Used for properly encoding file content in data URLs.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("encodeURIComponent")
external fun encodeURIComponent(value: String): String

@Composable
actual fun HorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) = HorizontalScrollbar(rememberScrollbarAdapter(scrollState), modifier)

@Composable
actual fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) = VerticalScrollbar(rememberScrollbarAdapter(scrollState), modifier)

actual fun readFileText(filePath: String): String = WasmVirtualFS.read(filePath)
    ?: error("File loading by path is not supported on Web (not found in virtual FS): $filePath")

actual fun fileExists(filePath: String): Boolean = WasmVirtualFS.exists(filePath)

@OptIn(ExperimentalWasmJsInterop::class)
actual suspend fun downloadFileText(fileUrl: String): String {
    val response = window.fetch(fileUrl).await() as Response
    if (!response.ok) error("HTTP ${response.status}: ${response.statusText}")
    return response.text().await()
}

@OptIn(ExperimentalWasmJsInterop::class)
@Composable
actual fun SaveFileDialog(
    title: String?,
    selectedFile: String?,
    extension: String,
    onFileSelected: (String?) -> Unit,
    content: String
) {
    // Trigger file download using browser's download mechanism
    LaunchedEffect(Unit) {
        try {
            // Determine file name: use selectedFile if provided, otherwise generate a default name
            val fileName = when {
                !selectedFile.isNullOrBlank() -> {
                    // If selectedFile doesn't have the extension, add it
                    if (selectedFile.endsWith(".$extension")) {
                        selectedFile
                    } else {
                        "$selectedFile.$extension"
                    }
                }
                else -> "document.$extension"
            }

            // Create a data URL with the content
            val dataUrl = "data:text/plain;charset=utf-8," + encodeURIComponent(content)

            // Create a temporary anchor element
            val anchor = document.createElement("a") as HTMLAnchorElement
            anchor.href = dataUrl
            anchor.download = fileName
            anchor.style.display = "none"

            // Add to document, click, and remove
            document.body?.appendChild(anchor)
            anchor.click()
            document.body?.removeChild(anchor)

            // Notify success by passing the file name
            onFileSelected(fileName)
        } catch (e: Throwable) {
            println("Error saving file: ${e.message}")
            onFileSelected(null)
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@Composable
actual fun OpenFileDialog(
    title: String?,
    selectedFile: String?,
    allowedExtensions: List<String>,
    onFileSelected: (String?) -> Unit
) {
    // Create an input[type=file], read content, store in virtual FS, and return file name for display
    LaunchedEffect(Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        // Use allowedExtensions parameter to build accept attribute
        input.accept = if (allowedExtensions.isNotEmpty()) {
            allowedExtensions.joinToString(",") { ".$it" }
        } else {
            "*/*"
        }

        input.onchange = {
            val file = input.files?.item(0)
            if (file == null) {
                onFileSelected(null)
            } else {
                val reader = FileReader()
                reader.onload = {
                    val result = reader.result
                    val text = (result as? String) ?: result?.toString()
                    if (text != null) {
                        // Store content in virtual FS and return unique file name for display
                        val displayPath = WasmVirtualFS.put(file.name, text)
                        onFileSelected(displayPath)
                    } else {
                        println("Error: Failed to read file content: result is null")
                        onFileSelected(null)
                    }
                }
                reader.onerror = { event ->
                    println("Error reading file: $event")
                    onFileSelected(null)
                }
                reader.readAsText(file)
            }
            null
        }
        input.click()
    }
}

/**
 * Virtual file system for WASM platform.
 * Stores file contents in memory since WASM has no access to real file system.
 * Files are identified by unique names to avoid collisions.
 */
private object WasmVirtualFS {
    private val files = mutableMapOf<String, String>()

    /**
     * Stores file content and returns a unique file name.
     * If a file with the same name already exists, appends a counter to make it unique.
     */
    fun put(name: String?, content: String): String {
        val baseName = (name?.takeIf { it.isNotBlank() } ?: "document.sgf").trim()

        // If name is already unique, use it directly
        if (!files.containsKey(baseName)) {
            files[baseName] = content
            return baseName
        }

        // Generate unique name by appending counter
        val dotIndex = baseName.lastIndexOf('.')
        val (nameWithoutExt, extension) = if (dotIndex > 0) {
            baseName.substring(0, dotIndex) to baseName.substring(dotIndex)
        } else {
            baseName to ""
        }

        var counter = 1
        var uniqueName: String
        do {
            uniqueName = "$nameWithoutExt ($counter)$extension"
            counter++
        } while (files.containsKey(uniqueName))

        files[uniqueName] = content
        return uniqueName
    }

    fun read(path: String): String? = files[path]

    fun exists(path: String): Boolean = files.containsKey(path)
}

actual val platform: Platform = Platform.WEB
