// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Swaps a staged backup into place. Runs at process start, before content providers (WorkManager) and
 * anything else can open the database or the settings files, so no live handle sees half-replaced data.
 *
 * The swap is a two-step journal in [AppDataLocations.pendingMarker]: [STEP_PENDING] moves the live data
 * aside into [AppDataLocations.previousDir], [STEP_ASIDE] moves the staged backup in. Every move is a
 * rename on one filesystem and each step skips what is already done, so a process killed part way
 * finishes the same swap on the next start. If a step fails, the data set aside is put back.
 */
class RestoreApplier(private val locations: AppDataLocations) {

    /** Applies a confirmed restore, if one is pending; returns true when data was replaced. Never throws. */
    fun applyPending(): Boolean {
        val marker = locations.pendingMarker
        if (!marker.isFile) return false
        return try {
            if (marker.readText().trim() != STEP_ASIDE) {
                if (!File(locations.stagingDir, BackupLayout.DATABASE).isFile) {
                    clear()
                    return false
                }
                for ((live, aside) in items()) {
                    if (live.exists() && !aside.exists()) move(live, aside)
                }
                writeStep(STEP_ASIDE)
            }
            for ((live, staged) in stagedItems()) {
                if (staged.exists()) move(staged, live)
            }
            clear()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed; putting the previous data back", e)
            rollBack()
            clear()
            false
        }
    }

    /** Puts every item set aside back in place, dropping whatever the failed swap moved in. */
    private fun rollBack() {
        for ((live, aside) in items()) {
            if (!aside.exists()) continue
            live.deleteRecursively()
            if (!aside.renameTo(live)) Log.e(TAG, "Could not put back ${live.name}")
        }
    }

    private fun clear() {
        locations.pendingMarker.delete()
        locations.stagingDir.deleteRecursively()
    }

    private fun writeStep(step: String) {
        val tmp = File(locations.workDir, "pending.tmp")
        tmp.writeText(step)
        Files.move(tmp.toPath(), locations.pendingMarker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Each live item paired with its place in [AppDataLocations.previousDir]. */
    private fun items(): List<Pair<File, File>> {
        val previous = locations.previousDir
        return databaseFiles().map { it to File(previous, "database/${it.name}") } +
            AppDataLocations.FILE_DIRS.map { File(locations.filesDir, it) to File(previous, "files/$it") }
    }

    /** Each live item paired with its replacement in the staging folder; the database has no WAL there. */
    private fun stagedItems(): List<Pair<File, File>> {
        val staging = locations.stagingDir
        return listOf(locations.databaseFile to File(staging, BackupLayout.DATABASE)) +
            AppDataLocations.FILE_DIRS.map { File(locations.filesDir, it) to File(staging, BackupLayout.FILES_PREFIX + it) }
    }

    private fun databaseFiles(): List<File> {
        val db = locations.databaseFile
        return listOf(db) + listOf("-wal", "-shm", "-journal").map { File(db.path + it) }
    }

    private fun move(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (to.exists() && !to.deleteRecursively()) throw IOException("Cannot replace ${to.name}")
        if (!from.renameTo(to)) throw IOException("Cannot move ${from.name}")
    }

    companion object {
        private const val TAG = "RestoreApplier"

        /** Marker contents: the user confirmed; nothing has moved yet (or only part of the live data). */
        const val STEP_PENDING: String = "pending"

        /** Marker contents: all live data is aside; the backup is moving in. */
        const val STEP_ASIDE: String = "aside"
    }
}
