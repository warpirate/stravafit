// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Answers whether the device can reach the internet, and waits until it can. */
fun interface NetworkMonitor {
    fun isOnline(): Boolean

    /** Suspends until [isOnline]. The default polls; platform monitors listen for network changes. */
    suspend fun awaitOnline() {
        while (!isOnline()) delay(OFFLINE_POLL_MS)
    }

    companion object {
        const val OFFLINE_POLL_MS: Long = 5_000
    }
}

/** [NetworkMonitor] backed by [ConnectivityManager]. */
class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        // VALIDATED is left out on purpose: emulators and captive-portal-free networks often never
        // validate, yet tiles load fine (the live map does not check it either). A failed fetch just
        // falls back to the offline drawing and is retried.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun awaitOnline() {
        if (isOnline()) return
        val manager = connectivity ?: return super.awaitOnline()
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) trySend(Unit)
                }
            }
            manager.registerDefaultNetworkCallback(callback)
            if (isOnline()) trySend(Unit)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.first()
    }
}

/**
 * Decoded thumbnails kept in memory, so a card scrolled back into view shows its map at once
 * instead of flashing the offline drawing while the file is decoded again.
 */
class ThumbnailMemoryCache(maxBytes: Int) {
    private val lru = object : LruCache<String, Bitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = lru.get(key)

    fun put(key: String, bitmap: Bitmap) {
        lru.put(key, bitmap)
    }

    companion object {
        /** A sixteenth of the heap, at most 24 MB: about 30 full-width feed maps. */
        fun forRuntime(): ThumbnailMemoryCache =
            ThumbnailMemoryCache((Runtime.getRuntime().maxMemory() / 16).coerceAtMost(24L * 1024 * 1024).toInt())
    }
}

/**
 * Loads route thumbnails for list cards: memory, then the disk cache, then a real map snapshot when
 * online, and the offline [fallback] drawing otherwise. Only map snapshots are cached, so a card
 * seen offline gets its proper map once the device is online again.
 *
 * Snapshots run in [scope] when one is given (the app-wide scope in production), not in the
 * caller's: a card scrolled off screen or a tab switched away must not throw away a half-rendered
 * snapshot, or a slow first render (style, sprites and tiles to fetch) would never finish and the
 * card would show the plain drawing forever. Requests for the same picture share one render.
 *
 * At most [MAX_CONCURRENT_SNAPSHOTS] snapshots render at once; each one is a full off-screen map.
 */
class RouteThumbnailLoader(
    private val cache: ThumbnailCache,
    private val snapshot: RouteThumbnailRenderer,
    private val fallback: RouteThumbnailRenderer,
    private val network: NetworkMonitor,
    private val snapshotTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val scope: CoroutineScope? = null,
    private val memory: ThumbnailMemoryCache? = null,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    private val permits = Semaphore(MAX_CONCURRENT_SNAPSHOTS)
    private val inFlight = HashMap<String, Deferred<Bitmap?>>()

    /** The map snapshot already in memory for [request], without suspending; null otherwise. */
    fun peek(request: ThumbnailRequest): RouteThumbnail? =
        if (request.points.size < 2) null else memory?.get(request.cacheKey)?.let { RouteThumbnail(it, ThumbnailSource.MapSnapshot) }

    /** The thumbnail for [request] after at most one snapshot attempt, or null for fewer than two points. */
    suspend fun load(request: ThumbnailRequest): RouteThumbnail? {
        if (request.points.size < 2) return null
        cached(request)?.let { return it }
        if (network.isOnline()) snapshotOrNull(request)?.let { return it }
        return offline(request)
    }

    /**
     * Like [load], but never leaves a card blank and keeps trying: on a cache miss it emits the
     * offline drawing first, then the map snapshot once it is ready. Offline, it waits for the network
     * and tries again; a failed render is retried after each of [retryDelaysMs]. Emits nothing for a
     * route with fewer than two points.
     */
    fun loadProgressively(request: ThumbnailRequest): Flow<RouteThumbnail> = flow {
        if (request.points.size < 2) return@flow
        cached(request)?.let {
            emit(it)
            return@flow
        }
        offline(request)?.let { emit(it) }
        for (attempt in 0..retryDelaysMs.size) {
            network.awaitOnline()
            snapshotOrNull(request)?.let {
                emit(it)
                return@flow
            }
            if (attempt < retryDelaysMs.size) delay(retryDelaysMs[attempt])
        }
    }

    private suspend fun cached(request: ThumbnailRequest): RouteThumbnail? {
        val key = request.cacheKey
        val bitmap = memory?.get(key) ?: cache.get(key)?.also { memory?.put(key, it) } ?: return null
        return RouteThumbnail(bitmap, ThumbnailSource.MapSnapshot)
    }

    private suspend fun snapshotOrNull(request: ThumbnailRequest): RouteThumbnail? {
        val owner = scope
        val bitmap = if (owner == null) {
            render(request)
        } else {
            val key = request.cacheKey
            val job = synchronized(inFlight) {
                inFlight.getOrPut(key) {
                    owner.async {
                        try {
                            render(request)
                        } finally {
                            synchronized(inFlight) { inFlight.remove(key) }
                        }
                    }
                }
            }
            job.await()
        }
        return bitmap?.let { RouteThumbnail(it, ThumbnailSource.MapSnapshot) }
    }

    private suspend fun render(request: ThumbnailRequest): Bitmap? {
        val bitmap = permits.withPermit { withTimeoutOrNull(snapshotTimeoutMs) { snapshot.render(request) } } ?: return null
        memory?.put(request.cacheKey, bitmap)
        cache.put(request.cacheKey, bitmap)
        return bitmap
    }

    private suspend fun offline(request: ThumbnailRequest): RouteThumbnail? =
        fallback.render(request)?.let { RouteThumbnail(it, ThumbnailSource.Offline) }

    companion object {
        const val MAX_CONCURRENT_SNAPSHOTS: Int = 2

        /** A cold first snapshot fetches the style, sprites, glyphs and tiles; give it time. */
        const val DEFAULT_TIMEOUT_MS: Long = 30_000

        /** Waits between failed snapshot attempts of one visible card. */
        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(3_000, 15_000, 60_000)
    }
}
