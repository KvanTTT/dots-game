package org.dots.game

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.russhwolf.settings.Settings
import java.io.File
import java.net.URI

actual var appSettings: Settings? = null

/** Provides access to application context for non-composable platform calls. */
object AndroidContextHolder {
    lateinit var appContext: Context
}

@Composable
actual fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {}

@Composable
actual fun HorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {}

actual fun readFileText(filePath: String): String {
    // If it's a direct content URI, read via ContentResolver
    if (filePath.startsWith("content://")) {
        val uri = Uri.parse(filePath)
        return AndroidContextHolder.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Failed to open content URI: $filePath")
    }

    // If it was picked through the dialog, resolve its stored URI and read it
    AndroidPickedFiles.resolve(filePath)?.let { uri ->
        return AndroidContextHolder.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Failed to open stored content URI for: $filePath")
    }

    // Fallback to real filesystem path
    return File(filePath).readText()
}

actual fun fileExists(filePath: String): Boolean {
    // Consider content URIs and previously picked files as existing, or check real FS
    return filePath.startsWith("content://") || AndroidPickedFiles.exists(filePath) || File(filePath).exists()
}

actual suspend fun downloadFileText(fileUrl: String): String = URI.create(fileUrl).toURL().openStream().use {
    it.readBytes().decodeToString()
}

@Composable
actual fun OpenFileDialog(
    title: String,
    selectedFile: String?,
    allowedExtensions: List<String>,
    onFileSelected: (String?) -> Unit
) {
    // Use Activity Result API, read content, store in virtual FS, and return file name for display
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onFileSelected(null)
            return@rememberLauncherForActivityResult
        }

        try {
            // Extract file name from URI for display
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            } ?: "document.sgf"

            // Store the association name -> content URI (no in-memory file copy)
            val displayName = AndroidPickedFiles.put(fileName, uri)
            onFileSelected(displayName)
        } catch (t: Throwable) {
            android.util.Log.e("PlatformUtils", "Error reading file from URI: $uri", t)
            onFileSelected(null)
        }
    }

    // Launch once when this composable is shown
    val mimeTypes = remember(allowedExtensions) {
        // Build MIME types array. SGF has no official MIME type, so we allow text/* and */*
        if (allowedExtensions.isEmpty()) {
            arrayOf("*/*")
        } else {
            // For SGF files, accept both text/* and */* to maximize compatibility
            arrayOf("text/*", "*/*")
        }
    }
    LaunchedEffect(Unit) {
        launcher.launch(mimeTypes)
    }
}

/**
 * Lightweight storage for files picked via SAF: maps display names to content URIs.
 * Avoids copying file contents into memory while keeping UX consistent (showing a file-like name).
 */
private object AndroidPickedFiles {
    private val nameToUri = mutableMapOf<String, Uri>()

    fun put(name: String?, uri: Uri): String {
        val baseName = (name?.takeIf { it.isNotBlank() } ?: "document.sgf").trim()

        if (!nameToUri.containsKey(baseName)) {
            nameToUri[baseName] = uri
            return baseName
        }

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
        } while (nameToUri.containsKey(uniqueName))

        nameToUri[uniqueName] = uri
        return uniqueName
    }

    fun resolve(path: String): Uri? = nameToUri[path]

    fun exists(path: String): Boolean = nameToUri.containsKey(path)
}

actual val platform: String = "android"