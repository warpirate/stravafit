// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/** How often the automatic backup runs. */
enum class AutoBackupFrequency(val days: Long) {
    DAILY(1),
    WEEKLY(7),
}

/**
 * The automatic backup setup. It belongs to this phone (a folder permission cannot travel), so it is
 * kept outside the data a backup carries.
 *
 * @property folderUri the Storage Access Framework tree the user picked, or null before one was picked.
 * @property folderName that folder's name as its app shows it ("Backups" in Drive), for Settings.
 */
data class AutoBackupConfig(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val folderName: String? = null,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.WEEKLY,
)

/** Why an automatic backup did not happen. Each one says what to do, as shown in Settings and the inbox. */
enum class AutoBackupFailure(val message: String, val retryable: Boolean = false) {
    NO_FOLDER("Choose a folder for automatic backups."),
    FOLDER_REVOKED("StravaFit can no longer open the backup folder. Choose it again."),
    FOLDER_MISSING("The backup folder was moved or deleted. Choose a folder again."),
    NO_SPACE("There is not enough free space for the backup. Free up space on the phone or in the backup folder."),
    DATABASE_UNREADABLE("The activity database could not be read. Try again later."),
    WRITE_FAILED("The backup could not be written to the folder. StravaFit will try again.", retryable = true),
    ;

    companion object {
        /** The failure behind [error], judged by what the platform throws for each case. */
        fun of(error: Throwable): AutoBackupFailure = when {
            error is AutoBackupFolderException -> error.failure
            error.isOutOfSpace() -> NO_SPACE
            // A revoked grant or a provider that no longer lets us in.
            error is SecurityException -> FOLDER_REVOKED
            // The folder (or its provider) is gone: a missing document or an id the provider rejects.
            error is FileNotFoundException || error is IllegalArgumentException || error is IllegalStateException -> FOLDER_MISSING
            error is SQLiteException -> DATABASE_UNREADABLE
            else -> WRITE_FAILED
        }

        /** ENOSPC anywhere in the cause chain ("write failed: ENOSPC (No space left on device)"). */
        private fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause }.take(8).any { e ->
            val text = e.message.orEmpty()
            e is AutoBackupNoSpaceException || "ENOSPC" in text || "No space left" in text
        }
    }
}

/** The folder cannot be used, for [failure]. */
class AutoBackupFolderException(val failure: AutoBackupFailure) : IOException(failure.message)

/** The phone lacks room for the database snapshot a backup starts with. */
class AutoBackupNoSpaceException : IOException("No space left on the phone for the backup")

/**
 * What the last automatic (or "Back up now") runs did.
 *
 * @property lastFailure set when the latest run failed; cleared by the next success.
 */
data class AutoBackupStatus(
    val lastSuccessAt: Instant? = null,
    val lastSuccessFile: String? = null,
    val lastActivityCount: Int? = null,
    val lastFailureAt: Instant? = null,
    val lastFailure: AutoBackupFailure? = null,
)

/** Stores [AutoBackupConfig] and [AutoBackupStatus]. */
interface AutoBackupStore {
    val config: kotlinx.coroutines.flow.Flow<AutoBackupConfig>
    val status: kotlinx.coroutines.flow.Flow<AutoBackupStatus>

    suspend fun updateConfig(change: (AutoBackupConfig) -> AutoBackupConfig)

    suspend fun recordSuccess(at: Instant, fileName: String, activityCount: Int)

    suspend fun recordFailure(at: Instant, failure: AutoBackupFailure)
}

/** One file in the backup folder. [id] is whatever the folder needs to open or delete it. */
data class BackupFolderFile(val id: String, val name: String)

/** A folder automatic backups are written to. Calls may block: use them off the main thread. */
interface BackupFolder {
    /** The files directly in the folder. */
    fun list(): List<BackupFolderFile>

    /** Creates an empty file called [name]. */
    fun create(name: String): BackupFolderFile

    /** Opens [file] for writing from the start. */
    fun openOutput(file: BackupFolderFile): OutputStream

    /** Deletes [file]; returns false when it could not be deleted. */
    fun delete(file: BackupFolderFile): Boolean
}

/** Opens the folder the user picked. */
fun interface BackupFolders {
    /**
     * The folder at [uri], checked to exist and to be writable.
     *
     * @throws AutoBackupFolderException when the permission is gone or the folder is missing.
     */
    fun open(uri: String): BackupFolder
}

/** Free space for the database snapshot every backup starts with. */
fun interface LocalSpaceCheck {
    /** True when the phone has room to copy the database before writing the backup. */
    fun hasRoomForSnapshot(): Boolean
}

/**
 * Names of the files automatic backups write, `stravafit-auto-backup-2026-11-03-081205.zip`. Only files
 * named exactly like this are ever deleted, so a manual backup or anything else in the folder is safe.
 */
object AutoBackupNames {
    private const val PREFIX = "stravafit-auto-backup-"
    private const val SUFFIX = ".zip"
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    private val PATTERN = Regex("""^stravafit-auto-backup-(\d{4}-\d{2}-\d{2}-\d{6})\.zip$""")

    /** How many automatic backups are kept; older ones go. */
    const val KEEP: Int = 4

    /** The name for a backup made at [at], in local time. */
    fun nameFor(at: Instant, clock: Clock): String = PREFIX + STAMP.format(LocalDateTime.ofInstant(at, clock.zone)) + SUFFIX

    /** When an automatic backup called [name] was made, or null when this feature did not make it. */
    fun timeOf(name: String): LocalDateTime? {
        val stamp = PATTERN.matchEntire(name)?.groupValues?.get(1) ?: return null
        return try {
            LocalDateTime.parse(stamp, STAMP)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** The automatic backups among [files] beyond the newest [keep]; every other file is left alone. */
    fun toDelete(files: List<BackupFolderFile>, keep: Int = KEEP): List<BackupFolderFile> =
        files.mapNotNull { f -> timeOf(f.name)?.let { f to it } }
            .sortedByDescending { it.second }
            .drop(keep)
            .map { it.first }
}

/** What one run did. */
sealed interface AutoBackupOutcome {
    data class Success(val fileName: String, val activityCount: Int, val deleted: Int) : AutoBackupOutcome

    data class Failed(val failure: AutoBackupFailure) : AutoBackupOutcome

    /** Another backup was being written; this run did nothing. */
    data object AlreadyRunning : AutoBackupOutcome
}

/**
 * Writes one automatic backup: a dated file in the chosen folder made by the same writer as "Back up
 * everything", then deletes the automatic backups beyond the newest [AutoBackupNames.KEEP]. One run at a
 * time; a second one while a backup is being written returns [AutoBackupOutcome.AlreadyRunning].
 */
@Singleton
class AutoBackupRunner @Inject constructor(
    private val store: AutoBackupStore,
    private val folders: BackupFolders,
    private val backup: DataBackup,
    private val space: LocalSpaceCheck,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)

    /** True while a backup is being written. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun run(): AutoBackupOutcome {
        if (!mutex.tryLock()) return AutoBackupOutcome.AlreadyRunning
        _running.value = true
        try {
            val outcome = write()
            when (outcome) {
                is AutoBackupOutcome.Success -> store.recordSuccess(clock.instant(), outcome.fileName, outcome.activityCount)
                is AutoBackupOutcome.Failed -> store.recordFailure(clock.instant(), outcome.failure)
                AutoBackupOutcome.AlreadyRunning -> Unit
            }
            return outcome
        } finally {
            _running.value = false
            mutex.unlock()
        }
    }

    private suspend fun write(): AutoBackupOutcome {
        val uri = store.config.first().folderUri ?: return AutoBackupOutcome.Failed(AutoBackupFailure.NO_FOLDER)
        return try {
            if (!space.hasRoomForSnapshot()) throw AutoBackupNoSpaceException()
            val folder = folders.open(uri)
            val name = AutoBackupNames.nameFor(clock.instant(), clock)
            val file = folder.create(name)
            val manifest = try {
                folder.openOutput(file).use { backup.backUp(it) }
            } catch (e: Throwable) {
                // Never leave a half-written file that looks like a backup.
                runCatching { folder.delete(file) }
                throw e
            }
            // Retention only after the new file is complete, and only among our own files.
            val stale = AutoBackupNames.toDelete(folder.list())
            val deleted = stale.count { runCatching { folder.delete(it) }.getOrDefault(false) }
            AutoBackupOutcome.Success(name, manifest.activityCount, deleted)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AutoBackupOutcome.Failed(AutoBackupFailure.of(e))
        }
    }
}
