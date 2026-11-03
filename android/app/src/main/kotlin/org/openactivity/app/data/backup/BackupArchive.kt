// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * What a backup says about itself, stored as `manifest.json` at the root of the ZIP.
 *
 * @property format layout version of the archive; readers refuse a newer one.
 * @property databaseVersion Room schema version of the bundled database; older ones are migrated on open.
 * @property filesDir absolute `files/` path of the app that wrote it (debug and release builds differ),
 *   kept for diagnosing a restore.
 */
@Serializable
data class BackupManifest(
    val format: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val createdAt: String,
    val activityCount: Int,
    val filesDir: String,
)

/** Where the app keeps the data a backup carries. Everything else (caches, exports) is left out. */
data class AppDataLocations(
    /** The Room database file; its `-wal` and `-shm` siblings belong to it. */
    val databaseFile: File,
    /** The app's `files/` directory. */
    val filesDir: File,
    /** Private scratch space for snapshots and staged restores, outside `files/`. */
    val workDir: File,
) {
    val stagingDir: File get() = File(workDir, "staging")
    val previousDir: File get() = File(workDir, "previous")
    val pendingMarker: File get() = File(workDir, "pending")

    companion object {
        /** `files/` subdirectories holding user data: settings, activity photos and notes, the profile photo. */
        val FILE_DIRS: List<String> = listOf("datastore", "activity-extras", "profile")
    }
}

/** A backup that cannot be read or restored; [message] is shown to the user as is. */
class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Archive layout shared by [BackupWriter] and [BackupReader]. */
internal object BackupLayout {
    const val FORMAT: Int = 1
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database/openactivity.db"
    const val FILES_PREFIX = "files/"

    /** Recording tables are left out: restoring a half-finished recording would resurrect it as a crash. */
    val TRANSIENT_TABLES: List<String> = listOf("recording_journal_points", "recordings_journal")

    val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** True for entries a backup may contain; anything else (or a path escaping its folder) is rejected. */
    fun isAllowed(name: String): Boolean {
        if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." || it == "." }) return false
        if (name == MANIFEST || name == DATABASE) return true
        if (!name.startsWith(FILES_PREFIX)) return false
        val dir = name.removePrefix(FILES_PREFIX).substringBefore('/', missingDelimiterValue = "")
        return dir in AppDataLocations.FILE_DIRS
    }
}

/**
 * Writes a full backup: a consistent copy of the database plus every user-data file.
 *
 * @param snapshot copies the live database (and its WAL) into the given file while no one can write to it.
 */
class BackupWriter(
    private val locations: AppDataLocations,
    private val appVersion: String,
    private val databaseVersion: Int,
    private val snapshot: (File) -> Unit,
) {
    /** Streams the archive into [output] (left open for the caller) and returns its manifest. */
    fun write(output: OutputStream, now: Instant = Instant.now()): BackupManifest {
        val dir = File(locations.workDir, "snapshot").apply { deleteRecursively(); mkdirs() }
        try {
            val db = File(dir, "openactivity.db")
            snapshot(db)
            val activities = finishSnapshot(db)
            val manifest = BackupManifest(
                format = BackupLayout.FORMAT,
                appVersion = appVersion,
                databaseVersion = databaseVersion,
                createdAt = now.toString(),
                activityCount = activities,
                filesDir = locations.filesDir.absolutePath,
            )
            val zip = ZipOutputStream(output)
            zip.putNextEntry(ZipEntry(BackupLayout.MANIFEST))
            zip.write(BackupLayout.json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.addFile(BackupLayout.DATABASE, db)
            for (name in AppDataLocations.FILE_DIRS) {
                val root = File(locations.filesDir, name)
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.sortedBy { it.path }.forEach { file ->
                    zip.addFile(BackupLayout.FILES_PREFIX + name + "/" + file.relativeTo(root).invariantSeparatorsPath, file)
                }
            }
            zip.finish()
            zip.flush()
            return manifest
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Folds the WAL into a single file, drops recording scratch tables and counts the activities. */
    private fun finishSnapshot(db: File): Int {
        val sqlite = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            for (table in BackupLayout.TRANSIENT_TABLES) sqlite.execSQL("DELETE FROM `$table`")
            sqlite.rawQuery("PRAGMA journal_mode=DELETE", null).close()
            return sqlite.rawQuery("SELECT COUNT(*) FROM activities", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } finally {
            sqlite.close()
        }
    }

    private fun ZipOutputStream.addFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}

/** Unpacks and checks a backup into [AppDataLocations.stagingDir] without touching live data. */
class BackupReader(private val locations: AppDataLocations, private val currentDatabaseVersion: Int) {

    /**
     * Extracts [input] into the staging folder and validates it.
     *
     * @throws BackupException when the file is not a backup, is damaged, or comes from a newer app.
     */
    fun stage(input: InputStream): BackupManifest {
        val staging = locations.stagingDir.apply { deleteRecursively(); mkdirs() }
        try {
            var entries = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (!BackupLayout.isAllowed(entry.name)) throw BackupException("This file is not a StravaFit backup")
                    val target = File(staging, entry.name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    entries++
                }
            }
            if (entries == 0) throw BackupException("This file is not a StravaFit backup")
            val manifest = readManifest(File(staging, BackupLayout.MANIFEST))
            if (manifest.format > BackupLayout.FORMAT || manifest.databaseVersion > currentDatabaseVersion) {
                throw BackupException("This backup was made by a newer version of StravaFit. Update the app first.")
            }
            checkDatabase(File(staging, BackupLayout.DATABASE))
            return manifest
        } catch (e: BackupException) {
            staging.deleteRecursively()
            throw e
        } catch (e: IOException) {
            staging.deleteRecursively()
            throw BackupException("The backup could not be read. It may be damaged.", e)
        }
    }

    /** Drops a staged backup the user decided not to restore. */
    fun discard() {
        locations.stagingDir.deleteRecursively()
    }

    private fun readManifest(file: File): BackupManifest {
        if (!file.isFile) throw BackupException("This file is not a StravaFit backup")
        return try {
            BackupLayout.json.decodeFromString(BackupManifest.serializer(), file.readText())
        } catch (e: IllegalArgumentException) {
            throw BackupException("This file is not a StravaFit backup", e)
        }
    }

    private fun checkDatabase(file: File) {
        if (!file.isFile) throw BackupException("The backup has no activity database")
        val ok = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrity = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
                val hasActivities = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'activities'",
                    null,
                ).use { it.moveToFirst() }
                integrity && hasActivities
            }
        } catch (e: RuntimeException) {
            // SQLiteException for a file that is not a database at all.
            false
        }
        if (!ok) throw BackupException("The backup could not be read. It may be damaged.")
    }
}
