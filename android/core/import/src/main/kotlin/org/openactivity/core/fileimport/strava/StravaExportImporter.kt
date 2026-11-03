// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.openactivity.core.fileimport.ActivityFileParser
import org.openactivity.core.fileimport.ImportException
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ManualActivity
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Stores what [StravaExportImporter] reads. Implemented by the app over its database and photo storage. */
public interface StravaImportSink {
    /** Saves a recorded activity read from its track file. [row] has the name, type and description to use. */
    public suspend fun saveTrack(row: StravaActivityRow, activity: ImportedActivity): RowOutcome

    /** Saves an activity known only from its `activities.csv` row (a manual entry). */
    public suspend fun saveManual(row: StravaActivityRow, activity: ManualActivity): RowOutcome

    /** Attaches the photo [path] (read from [input]) to [activityId]. False when it could not be stored. */
    public suspend fun attachPhoto(activityId: Long, path: String, input: InputStream): Boolean
}

/** How far an import is. */
public data class StravaImportProgress(val phase: Phase, val done: Int, val total: Int) {
    public enum class Phase {
        /** Looking for `activities.csv`. */
        READING_LIST,

        /** Importing activities ([done] of [total]). */
        ACTIVITIES,

        /** A last pass for photos stored before their activity in the ZIP. */
        PHOTOS,
    }
}

/** The end of an import: every row's outcome and how many photos were attached. */
public data class StravaImportResult(val entries: List<JournalEntry>, val total: Int, val photosAttached: Int) {
    val imported: Int get() = entries.count { it.outcome is RowOutcome.Imported }
    val duplicates: Int get() = entries.count { it.outcome is RowOutcome.Duplicate }
    val failed: List<JournalEntry> get() = entries.filter { it.outcome is RowOutcome.Failed }
}

/** The ZIP cannot be imported at all. */
public class StravaExportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Imports the ZIP from strava.com (Settings, My Account, "Download or delete your account"): every row
 * of `activities.csv`, with its track from `activities/` (GPX, TCX or FIT, often gzipped) and its photos
 * from `media/`. Rows without a file (manual entries) become activities without a map.
 *
 * The ZIP is streamed, never loaded whole, because exports run to several gigabytes; only one track
 * file is in memory at a time. A ZIP is read front to back, so entries that come before
 * `activities.csv` (or photos that come before their activity) are picked up by another pass over the
 * file; usually one pass is enough. Every row and photo is written to the [StravaImportJournal] when it
 * is done, and rows already in it are skipped, so a stopped import resumes without duplicates.
 */
public class StravaExportImporter(private val parser: ActivityFileParser = ActivityFileParser()) {

    /**
     * Runs the import. [open] returns a fresh stream over the ZIP each time it is called.
     *
     * @throws StravaExportException when the ZIP cannot be read or has no `activities.csv`.
     */
    public suspend fun run(
        open: () -> InputStream,
        journal: StravaImportJournal,
        sink: StravaImportSink,
        onProgress: suspend (StravaImportProgress) -> Unit = {},
    ): StravaImportResult {
        val run = Run(journal, sink, onProgress)
        run.load()
        var pass = 0
        do {
            pass++
            try {
                ZipInputStream(BufferedInputStream(open(), BUFFER)).use { zip -> run.pass(zip, pass) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StravaExportException) {
                throw e
            } catch (e: IOException) {
                throw StravaExportException("The ZIP file could not be read: ${e.message ?: e.javaClass.simpleName}", e)
            } catch (e: IllegalArgumentException) {
                // ZipInputStream rejects malformed entry names this way.
                throw StravaExportException("The ZIP file is damaged: ${e.message}", e)
            }
            if (!run.foundList) throw StravaExportException(NOT_AN_EXPORT)
        } while (run.needsAnotherPass() && pass < MAX_PASSES)
        run.finishMissing()
        return StravaImportResult(journal.entries(), run.total, run.photosAttached)
    }

    private inner class Run(
        val journal: StravaImportJournal,
        val sink: StravaImportSink,
        val onProgress: suspend (StravaImportProgress) -> Unit,
    ) {
        val done = HashMap<String, RowOutcome>()
        val photosDone = HashSet<String>()
        var rows: List<StravaActivityRow> = emptyList()
        var foundList = false
        var root = ""
        val rowByFile = HashMap<String, StravaActivityRow>()
        val rowByPhoto = HashMap<String, StravaActivityRow>()

        /** Entry keys seen in the first pass before `activities.csv` was found. */
        val seenEarly = HashSet<String>()

        /** Photos seen before their row was done. */
        val pendingPhotos = HashSet<String>()
        var photosAttached = 0
        val total get() = rows.size

        suspend fun load() {
            journal.entries().forEach { done[it.rowId] = it.outcome }
            photosDone += journal.photos()
        }

        suspend fun pass(zip: ZipInputStream, pass: Int) {
            if (pass == 1) onProgress(StravaImportProgress(StravaImportProgress.Phase.READING_LIST, 0, 0))
            val photosOnly = pass > 1 && rowsLeftInZip().isEmpty()
            if (pass > 1) onProgress(progress(if (photosOnly) StravaImportProgress.Phase.PHOTOS else StravaImportProgress.Phase.ACTIVITIES))
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                if (!foundList) {
                    if (isActivityList(name)) {
                        readList(name, zip)
                    } else {
                        seenEarly += name
                    }
                    continue
                }
                handle(key(name), zip)
            }
        }

        private suspend fun readList(name: String, zip: ZipInputStream) {
            root = name.substring(0, name.length - LIST_NAME.length)
            rows = StravaActivitiesCsv.parse(InputStreamReader(NonClosing(zip), Charsets.UTF_8)).distinctBy { it.id }
            foundList = true
            for (row in rows) {
                row.fileName?.let { rowByFile[normalize(it)] = row }
                row.media.forEach { rowByPhoto[normalize(it)] = row }
            }
            onProgress(progress(StravaImportProgress.Phase.ACTIVITIES))
            // Manual entries have nothing else in the ZIP: import them straight away.
            for (row in rows) {
                if (row.fileName == null && row.id !in done) {
                    coroutineContext.ensureActive()
                    finish(row, manual(row, null))
                }
            }
        }

        private suspend fun handle(key: String, input: InputStream) {
            rowByFile[key]?.let { row ->
                if (row.id !in done) finish(row, track(row, key, input))
                return
            }
            val row = rowByPhoto[key] ?: return
            if (key in photosDone) return
            when (val outcome = done[row.id]) {
                is RowOutcome.Imported -> attach(outcome.activityId, key, input)
                null -> pendingPhotos += key
                else -> Unit // a duplicate keeps the photos it has; a failed row has nowhere to put them
            }
        }

        private suspend fun attach(activityId: Long, key: String, input: InputStream) {
            pendingPhotos -= key
            val stored = try {
                sink.attachPhoto(activityId, key, NonClosing(input))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (stored) photosAttached++
            // Written down either way: an unreadable photo is not retried on every resume.
            photosDone += key
            journal.recordPhoto(key)
        }

        private suspend fun track(row: StravaActivityRow, key: String, input: InputStream): RowOutcome {
            val fileName = key.substringAfterLast('/')
            val activity: ImportedActivity = try {
                parser.parse(fileName, NonClosing(input)).first()
            } catch (e: ImportException) {
                return manual(row, "Its file ${row.fileName} could not be read (${e.error.message})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return manual(row, "Its file ${row.fileName} could not be read (${e.message ?: e.javaClass.simpleName})")
            } catch (e: OutOfMemoryError) {
                return manual(row, "Its file ${row.fileName} is too large for this phone")
            }
            return save { sink.saveTrack(row, activity) }
        }

        /**
         * Saves [row] from its CSV summary. [problem] says why the track could not be used; with no
         * usable summary either, the row fails with that reason.
         */
        private suspend fun manual(row: StravaActivityRow, problem: String?): RowOutcome {
            val start = row.start
            val elapsed = row.elapsedSeconds?.takeIf { it > 0 }
            if (start == null || elapsed == null) {
                return RowOutcome.Failed(problem ?: "activities.csv has no date or duration for it")
            }
            val activity = ManualActivity(
                sport = row.sport,
                startTime = start,
                elapsedSeconds = elapsed,
                movingSeconds = row.movingSeconds?.takeIf { it in 1..elapsed },
                distanceMeters = row.distanceMeters ?: 0.0,
                elevationGainMeters = row.elevationGainMeters,
                averageHeartRate = row.averageHeartRate,
                maxHeartRate = row.maxHeartRate,
            )
            return save { sink.saveManual(row, activity) }
        }

        private suspend fun save(block: suspend () -> RowOutcome): RowOutcome = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RowOutcome.Failed("It could not be saved: ${e.message ?: e.javaClass.simpleName}")
        }

        private suspend fun finish(row: StravaActivityRow, outcome: RowOutcome) {
            done[row.id] = outcome
            journal.record(JournalEntry(row.id, displayName(row), outcome))
            onProgress(progress(StravaImportProgress.Phase.ACTIVITIES))
        }

        /** Rows whose file is in the ZIP but was passed before the list was known. */
        private fun rowsLeftInZip(): List<StravaActivityRow> =
            seenEarly.mapNotNull { rowByFile[key(it)] }.filter { it.id !in done }

        fun needsAnotherPass(): Boolean {
            if (rowsLeftInZip().isNotEmpty()) return true
            val earlyPhotos = seenEarly.map(::key).filter { it in rowByPhoto && it !in photosDone }
            return (pendingPhotos + earlyPhotos).any { key -> rowByPhoto[key]?.let { done[it.id] } is RowOutcome.Imported }
        }

        /** Rows whose file the ZIP does not have: saved from their summary, or failed. */
        suspend fun finishMissing() {
            for (row in rows) {
                if (row.id in done) continue
                coroutineContext.ensureActive()
                finish(row, manual(row, "The export has no file ${row.fileName}"))
            }
        }

        private fun progress(phase: StravaImportProgress.Phase) = StravaImportProgress(phase, done.count { it.key in rowIds }, total)

        private val rowIds: Set<String> by lazy { rows.mapTo(HashSet()) { it.id } }

        private fun key(entryName: String): String = normalize(entryName.removePrefix(root))
    }

    /** Reading the ZIP entry by entry must not close it when a reader is done with one entry. */
    private class NonClosing(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    public companion object {
        private const val LIST_NAME = "activities.csv"
        private const val BUFFER = 64 * 1024
        private const val MAX_PASSES = 3
        internal const val NOT_AN_EXPORT =
            "This ZIP has no activities.csv, so it is not a Strava export. Use the file Strava emails you a link to."

        /** `activities.csv` at the top of the ZIP, or in the one folder some unzip-and-rezip tools add. */
        internal fun isActivityList(name: String): Boolean {
            if (!name.endsWith(LIST_NAME, ignoreCase = true)) return false
            val parent = name.dropLast(LIST_NAME.length).trimEnd('/')
            return parent.isEmpty() || (!parent.contains('/') && !parent.equals("activities", ignoreCase = true))
        }

        /** Paths compare without case and with forward slashes; the CSV and the ZIP sometimes differ in both. */
        internal fun normalize(path: String): String = path.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)

        private fun displayName(row: StravaActivityRow): String = row.name.ifBlank { row.typeName.ifBlank { "Activity ${row.id}" } }
    }
}
