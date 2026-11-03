// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.app.BuildConfig
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.recordingservice.RecordingController
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full backup and restore of everything the app stores on the phone: activities with their GPS
 * streams, laps and best efforts, routes, goals, segments and their efforts, the inbox, photos and notes, the
 * profile and settings. Everything in the database travels, so new tables are included without listing them.
 */
interface DataBackup {
    /**
     * Writes a backup ZIP to the document at [destination].
     *
     * @throws IOException when the document cannot be written.
     */
    suspend fun backUp(destination: Uri): BackupManifest

    /**
     * Writes a backup ZIP into [output], which stays open for the caller. Automatic backups use this with
     * a file they created in the chosen folder.
     *
     * @throws IOException when the stream cannot be written.
     */
    suspend fun backUp(output: OutputStream): BackupManifest

    /**
     * Reads and checks the backup at [source] without changing any data, so the user can confirm.
     *
     * @throws BackupException when the file is not a usable backup.
     */
    suspend fun stageRestore(source: Uri): BackupManifest

    /** Forgets a staged backup the user did not confirm. */
    suspend fun discardRestore()

    /** Replaces all data with the staged backup by restarting the app; does not return normally. */
    suspend fun restoreAndRestart()
}

/** [DataBackup] over the app's private storage and Storage Access Framework documents. */
@Singleton
class LocalDataBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OpenActivityDatabase,
    private val restarter: AppRestarter,
) : DataBackup {

    private val locations = appDataLocations(context)

    override suspend fun backUp(destination: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination, "wt") ?: throw IOException("Cannot open the chosen file")
        output.use { backUp(it) }
    }

    override suspend fun backUp(output: OutputStream): BackupManifest = withContext(Dispatchers.IO) {
        BackupWriter(locations, BuildConfig.VERSION_NAME, OpenActivityDatabase.VERSION, ::snapshot).write(output)
    }

    override suspend fun stageRestore(source: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val input = try {
            context.contentResolver.openInputStream(source)
        } catch (e: RuntimeException) {
            // SecurityException / IllegalArgumentException from a revoked or invalid document.
            null
        } ?: throw BackupException("The chosen file could not be opened")
        input.use { BackupReader(locations, OpenActivityDatabase.VERSION).stage(it) }
    }

    override suspend fun discardRestore() = withContext(Dispatchers.IO) {
        locations.pendingMarker.delete()
        BackupReader(locations, OpenActivityDatabase.VERSION).discard()
    }

    override suspend fun restoreAndRestart() {
        withContext(Dispatchers.IO) {
            if (!File(locations.stagingDir, BackupLayout.DATABASE).isFile) throw BackupException("Choose the backup again")
            // The data set aside by an earlier restore goes now, never during a swap that might need it.
            locations.previousDir.deleteRecursively()
            locations.pendingMarker.writeText(RestoreApplier.STEP_PENDING)
        }
        val restarted = try {
            restarter.restart()
        } catch (e: RuntimeException) {
            false
        }
        // Still running: never leave a restore armed to fire at some later cold start.
        if (!restarted) {
            discardRestore()
            throw BackupException("StravaFit could not restart. Nothing was changed.")
        }
    }

    /** Copies the database and its WAL while holding the write lock, so the pair is consistent. */
    private fun snapshot(target: File) {
        val live = locations.databaseFile
        database.runInTransaction {
            live.copyTo(target, overwrite = true)
            val wal = File(live.path + "-wal")
            if (wal.isFile) wal.copyTo(File(target.path + "-wal"), overwrite = true)
        }
    }

    companion object {
        /** The real storage locations, also used by the application before Hilt starts. */
        fun appDataLocations(context: Context) = AppDataLocations(
            databaseFile = context.getDatabasePath(OpenActivityDatabase.NAME),
            filesDir = context.filesDir,
            workDir = File(context.noBackupFilesDir, "backup"),
        )
    }
}

/** Restarts the app process so a confirmed restore is applied at the next start. */
fun interface AppRestarter {
    /** Ends this process after scheduling a fresh start; returns false when the app cannot be relaunched. */
    fun restart(): Boolean
}

/** [AppRestarter] that relaunches the launcher activity in a fresh task and ends this process. */
class ProcessAppRestarter @Inject constructor(@ApplicationContext private val context: Context) : AppRestarter {
    override fun restart(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return false
        context.startActivity(Intent.makeRestartActivityTask(launch))
        Runtime.getRuntime().exit(0)
        return true
    }
}

/** Whether a recording is running or waiting to be saved; a restore would throw it away. */
fun interface UnsavedRecordingCheck {
    fun hasUnsavedRecording(): Boolean
}

/** [UnsavedRecordingCheck] over the live [RecordingController] state. */
class ControllerUnsavedRecordingCheck @Inject constructor(private val controller: RecordingController) : UnsavedRecordingCheck {
    override fun hasUnsavedRecording(): Boolean =
        controller.state.value.isActive || controller.interruptedRecording.value != null
}
