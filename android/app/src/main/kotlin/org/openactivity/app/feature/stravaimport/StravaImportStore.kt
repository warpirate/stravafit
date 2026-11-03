// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.stravaimport

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.openactivity.core.fileimport.strava.FileStravaImportJournal
import org.openactivity.core.fileimport.strava.StravaImportJournal
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where a Strava import is. */
@Serializable
enum class StravaImportState {
    /** Started; the worker has not run yet, or is waiting to run again after the app was killed. */
    QUEUED,
    RUNNING,

    /** Stopped by the user; it can continue where it stopped. */
    CANCELLED,
    DONE,

    /** The ZIP could not be read at all; [StravaImportJob.error] says why. */
    FAILED,
}

/** What the import is doing right now, for the progress line. */
@Serializable
enum class StravaImportPhase { COPYING, READING_LIST, ACTIVITIES, PHOTOS }

/** An activity the import could not bring over, and why. */
@Serializable
data class StravaImportFailure(val name: String, val reason: String)

/**
 * The one Strava import there is at a time. It lives in a file, so the progress screen shows the right
 * thing after the app was killed, and the worker knows what to continue.
 *
 * @property sourceUri the ZIP the user picked (a Storage Access Framework URI the app keeps access to),
 *   or the shared URI until [needsCopy] has copied it in.
 * @property needsCopy the ZIP was shared to the app: access to it ends with the sharing app's grant, so
 *   the worker first copies it into the app's own storage.
 * @property done rows of `activities.csv` handled so far, of [total].
 * @property failures the first [StravaImportJob.MAX_FAILURES_LISTED] activities that failed.
 */
@Serializable
data class StravaImportJob(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val sourceName: String,
    val needsCopy: Boolean = false,
    val state: StravaImportState = StravaImportState.QUEUED,
    val phase: StravaImportPhase = StravaImportPhase.READING_LIST,
    val done: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val photos: Int = 0,
    val failures: List<StravaImportFailure> = emptyList(),
    val error: String? = null,
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long? = null,
) {
    /** Still to finish: queued, running, or stopped and able to continue. */
    val isActive: Boolean get() = state == StravaImportState.QUEUED || state == StravaImportState.RUNNING

    companion object {
        const val MAX_FAILURES_LISTED = 200
    }
}

/**
 * Keeps the current [StravaImportJob], its row journal and the copy of a shared ZIP in the app's
 * no-backup storage. Thread-safe; the worker and the screen share the one instance.
 */
@Singleton
class StravaImportStore internal constructor(private val dir: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, "strava-import"))

    private val jobFile = File(dir, "job.json")
    private val lock = Any()
    private val _job = MutableStateFlow(read())

    /** The current import, or null when there has never been one (or it was cleared). */
    val job: StateFlow<StravaImportJob?> = _job.asStateFlow()

    /** The journal of the current import's rows. */
    val journal: StravaImportJournal = FileStravaImportJournal(File(dir, "journal.txt"))

    /** Where a shared ZIP is copied to. */
    val stagedZip: File = File(dir, "export.zip")

    /** Starts afresh with [job]: the old journal and staged copy go. */
    suspend fun begin(job: StravaImportJob) {
        journal.clear()
        synchronized(lock) {
            stagedZip.delete()
            write(job)
        }
    }

    /**
     * Changes the job with [transform]. With [persist] false only the in-memory state changes (frequent
     * progress); the next persisted change writes everything.
     */
    fun update(persist: Boolean = true, transform: (StravaImportJob) -> StravaImportJob): StravaImportJob? = synchronized(lock) {
        val current = _job.value ?: return null
        val next = transform(current)
        if (persist) write(next) else _job.value = next
        next
    }

    /** Forgets the import and its files (after it finished and the user starts another). */
    suspend fun clear() {
        journal.clear()
        synchronized(lock) {
            jobFile.delete()
            stagedZip.delete()
            _job.value = null
        }
    }

    private fun write(job: StravaImportJob) {
        _job.value = job
        try {
            dir.mkdirs()
            val tmp = File(dir, "job.json.tmp")
            tmp.writeText(JSON.encodeToString(StravaImportJob.serializer(), job))
            if (!tmp.renameTo(jobFile)) {
                jobFile.delete()
                tmp.renameTo(jobFile)
            }
        } catch (e: IOException) {
            // The in-memory state is still right; the journal keeps the work done safe.
        }
    }

    private fun read(): StravaImportJob? = try {
        if (jobFile.exists()) JSON.decodeFromString(StravaImportJob.serializer(), jobFile.readText()) else null
    } catch (e: IOException) {
        null
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
