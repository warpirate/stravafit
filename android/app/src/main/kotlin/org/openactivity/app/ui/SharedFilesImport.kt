// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openactivity.app.data.route.RouteImporter
import org.openactivity.app.feature.explore.ExploreViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** What a file shared to the app should become. */
enum class SharedFilesTarget {
    /** Recorded activities: the import screen, which skips ones you already have. */
    Activity,

    /** A route to follow, saved on the Maps tab. */
    Route,
}

/** Outcome of importing shared files as routes. */
sealed interface SharedRouteImportResult {
    /** Show [message]; open route [openRouteId] when it is not null. */
    data class Done(val message: String, val openRouteId: Long?) : SharedRouteImportResult
}

/**
 * Imports files shared to the app as routes ("Open with", the share sheet). Activity imports go to
 * the import screen instead, which reports per file.
 */
@HiltViewModel
class SharedFilesViewModel @Inject constructor(
    private val routeImporter: RouteImporter,
    private val stager: SharedFileStager,
) : ViewModel() {
    private val results = Channel<SharedRouteImportResult>(Channel.BUFFERED)
    private val stagedFiles = Channel<StagedFiles>(Channel.BUFFERED)
    private val zips = Channel<Uri>(Channel.BUFFERED)

    /** A shared ZIP, taken for a Strava archive; collect once. */
    val stravaExports: Flow<Uri> = zips.receiveAsFlow()

    /** One-shot results; collect once. */
    val routeResults: Flow<SharedRouteImportResult> = results.receiveAsFlow()

    /** Shared files once copied in, ready to ask about; collect once. */
    val staged: Flow<StagedFiles> = stagedFiles.receiveAsFlow()

    /**
     * Copies [uris] into the app before asking what they are. A single ZIP goes to the Strava import
     * instead, which copies it itself once the user starts (archives run to gigabytes).
     */
    fun stage(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val zip = uris.singleOrNull()?.takeIf { stager.isZip(it) }
            if (zip != null) zips.send(zip) else stagedFiles.send(stager.stage(uris))
        }
    }

    /** Saves [uris] as routes, then reports how it went and which route to open (a single new one). */
    fun importRoutes(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val summary = routeImporter.import(uris)
            results.send(
                SharedRouteImportResult.Done(
                    message = ExploreViewModel.importMessage(summary.importedIds.size, summary.failures),
                    openRouteId = summary.importedIds.singleOrNull(),
                ),
            )
        }
    }
}

/**
 * Asks what [count] shared files are: activities you recorded, or a route to follow. GPX in
 * particular can be either; the app cannot tell a planned route from a recording reliably.
 */
@Composable
fun SharedFilesDialog(count: Int, onChoose: (SharedFilesTarget) -> Unit, onDismiss: () -> Unit) {
    val title = if (count == 1) "Import this file" else "Import these $count files"
    val body = if (count == 1) {
        "Add it to your activities, or save it as a route to follow on the Maps tab."
    } else {
        "Add them to your activities, or save them as routes to follow on the Maps tab."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Row {
                TextButton(onClick = { onChoose(SharedFilesTarget.Route) }) { Text("Save as route") }
                TextButton(onClick = { onChoose(SharedFilesTarget.Activity) }) { Text("Add activity") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shared files copied into the app; [rejected] were readable but not activity files, [unreadable] could not
 * be read at all (the sharing app gave no access, or the file went away).
 */
data class StagedFiles(val files: List<Uri>, val rejected: Int, val unreadable: Int = 0)

/**
 * Copies files shared to the app into its cache as they arrive. A shared content URI is readable only
 * while the sharing grant lasts (until the task goes), and the import happens later, after the
 * "activity or route?" question. Files that are not GPX, TCX or FIT (optionally gzipped) are turned
 * away here, so a shared photo or PDF never reaches the importers.
 */
class SharedFileStager @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun stage(uris: List<Uri>): StagedFiles = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
        var rejected = 0
        var unreadable = 0
        val files = uris.mapIndexedNotNull { index, uri ->
            // One folder per file keeps two shared files of the same name apart without renaming them: the
            // import screen shows the name as the user knows it.
            val copy = File(File(dir, index.toString()).apply { mkdirs() }, safeName(displayName(uri) ?: uri.lastPathSegment ?: "file"))
            val read = try {
                copy(uri, copy)
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
            when {
                !read -> null.also { copy.delete(); unreadable++ }
                !looksLikeActivityFile(copy) -> null.also { copy.delete(); rejected++ }
                else -> Uri.fromFile(copy)
            }
        }
        StagedFiles(files, rejected, unreadable)
    }

    /** Whether the shared file is a ZIP: by its type or name, or else by its first bytes. */
    suspend fun isZip(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val type = try {
            context.contentResolver.getType(uri)
        } catch (e: RuntimeException) {
            null
        }
        if (type in ZIP_TYPES) return@withContext true
        if (displayName(uri)?.endsWith(".zip", ignoreCase = true) == true) return@withContext true
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(4)
                input.read(head) == 4 && looksLikeZip(head)
            } ?: false
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Copies at most [MAX_BYTES]; false when the file is bigger or unreadable. */
    private fun copy(from: Uri, to: File): Boolean {
        val input = context.contentResolver.openInputStream(from) ?: return false
        input.use { source ->
            to.outputStream().use { sink ->
                val buffer = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return false
                    sink.write(buffer, 0, n)
                }
            }
        }
        return true
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: RuntimeException) {
        null
    }

    internal companion object {
        private const val DIR = "shared-imports"
        private const val MAX_BYTES = 100L * 1024 * 1024
        private const val BUFFER = 64 * 1024
        private const val HEADER = 2048

        /** Keeps the extension the importers go by; drops path separators and odd characters. */
        fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(MAX_NAME).ifEmpty { "file" }

        private const val MAX_NAME = 80

        private val ZIP_TYPES = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")

        /** The local-file-header signature every ZIP starts with ("PK" 3 4). */
        fun looksLikeZip(head: ByteArray): Boolean =
            head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()

        fun looksLikeActivityFile(file: File): Boolean {
            val head = ByteArray(HEADER)
            val n = file.inputStream().use { it.read(head) }
            return n > 0 && looksLikeActivityFile(head.copyOf(n))
        }

        /** FIT (".FIT" at byte 8), gzip, or XML whose root is gpx or TrainingCenterDatabase. */
        fun looksLikeActivityFile(head: ByteArray): Boolean {
            if (head.size >= 12 && String(head, 8, 4, Charsets.US_ASCII) == ".FIT") return true
            if (head.size >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()) return true
            val text = String(head, Charsets.UTF_8)
            return text.contains("<gpx", ignoreCase = true) || text.contains("<TrainingCenterDatabase", ignoreCase = true)
        }
    }
}
