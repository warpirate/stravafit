// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Stores rendered thumbnails by [ThumbnailRequest.cacheKey]. */
interface ThumbnailCache {
    suspend fun get(key: String): Bitmap?
    suspend fun put(key: String, bitmap: Bitmap)
}

/**
 * [ThumbnailCache] on disk, as JPEG files in [directory] (the app cache dir, so the system may
 * clear it). Least recently used files are deleted once the total exceeds [maxBytes].
 */
class DiskThumbnailCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ThumbnailCache {
    private val mutex = Mutex()

    override suspend fun get(key: String): Bitmap? = withContext(dispatcher) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext null
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) {
            file.delete() // Corrupt or partial: drop it so it is re-rendered.
        } else {
            file.setLastModified(System.currentTimeMillis())
        }
        bitmap
    }

    override suspend fun put(key: String, bitmap: Bitmap): Unit = withContext(dispatcher) {
        mutex.withLock {
            try {
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create $directory")
                val target = fileFor(key)
                val temp = File(directory, "${target.name}.tmp")
                temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (!temp.renameTo(target)) {
                    temp.delete()
                    throw IOException("cannot move thumbnail into place")
                }
                trim()
            } catch (e: IOException) {
                Log.w(TAG, "Could not cache thumbnail", e)
            }
        }
    }

    /** Deletes the oldest files until the cache fits in [maxBytes]. */
    internal fun trim() {
        val files = directory.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }

    private fun fileFor(key: String): File {
        require(key.isNotEmpty() && key.all { it.isLetterOrDigit() }) { "invalid cache key" }
        return File(directory, key + SUFFIX)
    }

    companion object {
        /** Default size budget: a few hundred list-card thumbnails. */
        const val DEFAULT_MAX_BYTES: Long = 24L * 1024 * 1024
        private const val SUFFIX = ".jpg"
        private const val JPEG_QUALITY = 88
        private const val TAG = "ThumbnailCache"
    }
}
