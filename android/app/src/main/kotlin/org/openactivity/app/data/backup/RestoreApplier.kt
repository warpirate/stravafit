// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Swaps a staged backup into place. Runs at process start, before anything opens the database or the
 * settings files, so no live handle ever sees half-replaced data.
 *
 * The data it replaces is moved to [AppDataLocations.previousDir] (kept until the next restore) and put
 * back if the swap fails part way.
 */
class RestoreApplier(private val locations: AppDataLocations) {

    /** Applies a pending restore, if one was confirmed; returns true when data was replaced. */
    fun applyPending(): Boolean {
        if (!locations.pendingMarker.isFile) return false
        val staging = locations.stagingDir
        if (!File(staging, BackupLayout.DATABASE).isFile) {
            clear()
            return false
        }
        val previous = locations.previousDir.apply { deleteRecursively(); mkdirs() }
        val moved = mutableListOf<Pair<File, File>>()
        return try {
            for (file in databaseFiles()) {
                if (file.exists()) moved += move(file, File(previous, "database/${file.name}"))
            }
            for (name in AppDataLocations.FILE_DIRS) {
                val dir = File(locations.filesDir, name)
                if (dir.exists()) moved += move(dir, File(previous, "files/$name"))
            }
            move(File(staging, BackupLayout.DATABASE), locations.databaseFile)
            for (name in AppDataLocations.FILE_DIRS) {
                val dir = File(staging, BackupLayout.FILES_PREFIX + name)
                if (dir.exists()) move(dir, File(locations.filesDir, name))
            }
            clear()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Restore failed; putting the previous data back", e)
            rollBack(moved)
            clear()
            false
        }
    }

    private fun rollBack(moved: List<Pair<File, File>>) {
        for (file in databaseFiles()) file.delete()
        for (name in AppDataLocations.FILE_DIRS) File(locations.filesDir, name).deleteRecursively()
        for ((original, saved) in moved.asReversed()) {
            if (!saved.renameTo(original)) Log.e(TAG, "Could not put back ${original.name}")
        }
    }

    /** The pending marker goes first so a crash below never retries the swap in a loop. */
    private fun clear() {
        locations.pendingMarker.delete()
        locations.stagingDir.deleteRecursively()
    }

    private fun databaseFiles(): List<File> {
        val db = locations.databaseFile
        return listOf(db) + listOf("-wal", "-shm", "-journal").map { File(db.path + it) }
    }

    private fun move(from: File, to: File): Pair<File, File> {
        to.parentFile?.mkdirs()
        if (to.exists() && !to.deleteRecursively()) throw IOException("Cannot replace ${to.name}")
        if (!from.renameTo(to)) throw IOException("Cannot move ${from.name}")
        return from to to
    }

    private companion object {
        const val TAG = "RestoreApplier"
    }
}
