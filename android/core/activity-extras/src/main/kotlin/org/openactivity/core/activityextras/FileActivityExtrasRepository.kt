// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ActivityExtrasRepository] backed by plain files: `<root>/<activityId>/extras.json` plus one JPEG per
 * photo in the same folder. Reads are cached in memory; writes are serialised by a mutex and written
 * atomically (temp file and rename), so a crash never leaves a half-written index.
 */
@Singleton
public class FileActivityExtrasRepository internal constructor(
    private val root: File,
    resolver: ContentResolver,
    private val io: CoroutineDispatcher,
) : ActivityExtrasRepository {

    @Inject
    public constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, DIRECTORY), context.contentResolver, Dispatchers.IO)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cache = MutableStateFlow<Map<Long, ActivityExtras>>(emptyMap())
    private val mutex = Mutex()
    private val encoder = PhotoEncoder(resolver)

    override fun observe(activityId: Long): Flow<ActivityExtras> =
        cache.onStart { ensureLoaded(activityId) }
            .map { it[activityId] ?: ActivityExtras.NONE }
            .distinctUntilChanged()

    override suspend fun get(activityId: Long): ActivityExtras = ensureLoaded(activityId)

    override suspend fun setMuted(activityId: Long, muted: Boolean): Unit = edit(activityId) { it.copy(muted = muted) }

    override suspend fun addPhotos(activityId: Long, uris: List<Uri>): AddPhotosResult = withContext(io) {
        mutex.withLock {
            var current = load(activityId)
            var added = 0
            var failed = 0
            var skipped = 0
            for (uri in uris) {
                if (current.photos.size >= ActivityExtras.MAX_PHOTOS) {
                    skipped++
                    continue
                }
                val id = UUID.randomUUID().toString()
                val file = File(folder(activityId), "$id.jpg")
                val encoded = try {
                    encoder.encode(uri, file)
                } catch (e: CancellationException) {
                    file.delete()
                    throw e
                } catch (e: Exception) {
                    // A broken or revoked picture must not stop the others.
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
                if (encoded == null) {
                    file.delete()
                    failed++
                } else {
                    current = current.copy(photos = current.photos + ActivityPhoto(id, file, encoded.width, encoded.height))
                    added++
                }
            }
            if (added > 0) store(activityId, current)
            AddPhotosResult(added, skipped, failed)
        }
    }

    override suspend fun removePhoto(activityId: Long, photoId: String): Unit = edit(activityId) { extras ->
        extras.photos.firstOrNull { it.id == photoId }?.file?.delete()
        extras.copy(photos = extras.photos.filterNot { it.id == photoId })
    }

    override suspend fun deleteAll(activityId: Long): Unit = withContext(io) {
        mutex.withLock {
            folder(activityId).deleteRecursively()
            cache.update { it - activityId }
        }
    }

    private suspend fun edit(activityId: Long, change: (ActivityExtras) -> ActivityExtras): Unit = withContext(io) {
        mutex.withLock {
            val current = load(activityId)
            val next = change(current)
            if (next != current) store(activityId, next)
        }
    }

    private suspend fun ensureLoaded(activityId: Long): ActivityExtras {
        cache.value[activityId]?.let { return it }
        return withContext(io) { mutex.withLock { load(activityId) } }
    }

    /** Reads [activityId] into the cache. Caller holds [mutex]. */
    private fun load(activityId: Long): ActivityExtras {
        cache.value[activityId]?.let { return it }
        val index = File(folder(activityId), INDEX)
        val extras = if (index.isFile) readIndex(activityId, index) else ActivityExtras.NONE
        cache.update { it + (activityId to extras) }
        return extras
    }

    private fun readIndex(activityId: Long, index: File): ActivityExtras = try {
        val stored = json.decodeFromString(StoredExtras.serializer(), index.readText())
        ActivityExtras(
            muted = stored.muted,
            photos = stored.photos.mapNotNull { p ->
                File(folder(activityId), p.fileName).takeIf { it.isFile }?.let { ActivityPhoto(p.id, it, p.width, p.height) }
            },
        )
    } catch (e: SerializationException) {
        ActivityExtras.NONE
    } catch (e: IllegalArgumentException) {
        ActivityExtras.NONE
    } catch (e: IOException) {
        ActivityExtras.NONE
    }

    /** Writes [extras] for [activityId] and publishes it. Caller holds [mutex]. */
    private fun store(activityId: Long, extras: ActivityExtras) {
        val dir = folder(activityId)
        if (extras == ActivityExtras.NONE) {
            dir.deleteRecursively()
        } else {
            dir.mkdirs()
            val stored = StoredExtras(extras.muted, extras.photos.map { StoredPhoto(it.id, it.file.name, it.width, it.height) })
            val tmp = File(dir, "$INDEX.tmp")
            tmp.writeText(json.encodeToString(StoredExtras.serializer(), stored))
            replaceAtomically(tmp, File(dir, INDEX))
        }
        cache.update { it + (activityId to extras) }
    }

    private fun folder(activityId: Long) = File(root, activityId.toString())

    @Serializable
    private data class StoredExtras(val muted: Boolean = false, val photos: List<StoredPhoto> = emptyList())

    @Serializable
    private data class StoredPhoto(val id: String, val fileName: String, val width: Int, val height: Int)

    internal companion object {
        const val DIRECTORY = "activity-extras"
        const val INDEX = "extras.json"
    }
}
