// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Imports files the user picked with the Storage Access Framework (see [ImportDocumentsContract]).
 *
 * Each file is read and parsed independently on [dispatcher]; a broken file yields a
 * [FileImportResult.Failure] and never stops the others. Nothing is stored: callers persist the
 * [ImportedActivity] values and use [ImportedActivity.dedupeKey] to skip activities they already have.
 */
public class ActivityImporter internal constructor(
    private val resolver: ContentResolver,
    private val parser: ActivityFileParser,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    public constructor(@ApplicationContext context: Context, parser: ActivityFileParser) :
        this(context.contentResolver, parser, Dispatchers.IO)

    /** Imports [uris] one after another, emitting each file's result as soon as it is known. */
    public fun importFiles(uris: List<Uri>): Flow<FileImportResult> = flow {
        for (uri in uris) {
            coroutineContext.ensureActive()
            emit(importOne(uri))
        }
    }.flowOn(dispatcher)

    /**
     * Imports all [uris] and returns their results in order. Activities that occur more than once in
     * the batch (the same recording picked as FIT and as GPX) are kept only the first time; later
     * copies are dropped from their file's result.
     */
    public suspend fun importAll(uris: List<Uri>): List<FileImportResult> {
        val seen = HashSet<String>()
        return importFiles(uris).toList().map { result ->
            if (result !is FileImportResult.Success) return@map result
            val fresh = result.activities.filter { seen.add(it.dedupeKey) }
            if (fresh.isEmpty()) {
                FileImportResult.Failure(result.displayName, ImportError(ImportErrorKind.EMPTY, "Already imported from another file in this batch"))
            } else {
                result.copy(activities = fresh)
            }
        }
    }

    private suspend fun importOne(uri: Uri): FileImportResult = withContext(dispatcher) {
        val name = displayName(uri)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("No data")
            val activities = input.use { parser.parse(name, it) }
            FileImportResult.Success(name, activities)
        } catch (e: ImportException) {
            FileImportResult.Failure(name, e.error)
        } catch (e: IOException) {
            FileImportResult.Failure(name, ImportError(ImportErrorKind.READ_FAILED, "Could not read the file: ${e.message}"))
        } catch (e: SecurityException) {
            FileImportResult.Failure(name, ImportError(ImportErrorKind.READ_FAILED, "Access to the file was revoked"))
        } catch (e: IllegalArgumentException) {
            // The readers reject impossible values (out-of-range coordinates in FIT, for example).
            FileImportResult.Failure(name, ImportError(ImportErrorKind.MALFORMED, "The file contains invalid data: ${e.message}"))
        }
    }

    private fun displayName(uri: Uri): String {
        val queried = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('\\') ?: uri.toString()
    }

    public companion object {
        /** Builds an importer without dependency injection. */
        public fun create(context: Context): ActivityImporter =
            ActivityImporter(context.applicationContext.contentResolver, ActivityFileParser(), Dispatchers.IO)
    }
}
