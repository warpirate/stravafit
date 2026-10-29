// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Answers whether the device can reach the internet right now. */
fun interface NetworkMonitor {
    fun isOnline(): Boolean
}

/** [NetworkMonitor] backed by [ConnectivityManager]. */
class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/**
 * Loads route thumbnails for list cards: the disk cache first, then a real map snapshot when
 * online, and the offline [fallback] drawing otherwise. Only map snapshots are cached, so a card
 * seen offline gets its proper map next time the device is online.
 *
 * At most [MAX_CONCURRENT_SNAPSHOTS] snapshots render at once; each one is a full off-screen map.
 */
class RouteThumbnailLoader(
    private val cache: ThumbnailCache,
    private val snapshot: RouteThumbnailRenderer,
    private val fallback: RouteThumbnailRenderer,
    private val network: NetworkMonitor,
    private val snapshotTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val permits = Semaphore(MAX_CONCURRENT_SNAPSHOTS)

    /** The thumbnail for [request], or null when the route has fewer than two points. */
    suspend fun load(request: ThumbnailRequest): RouteThumbnail? {
        if (request.points.size < 2) return null
        cache.get(request.cacheKey)?.let { return RouteThumbnail(it, ThumbnailSource.MapSnapshot) }
        if (network.isOnline()) {
            val bitmap = permits.withPermit { withTimeoutOrNull(snapshotTimeoutMs) { snapshot.render(request) } }
            if (bitmap != null) {
                cache.put(request.cacheKey, bitmap)
                return RouteThumbnail(bitmap, ThumbnailSource.MapSnapshot)
            }
        }
        return fallback.render(request)?.let { RouteThumbnail(it, ThumbnailSource.Offline) }
    }

    companion object {
        const val MAX_CONCURRENT_SNAPSHOTS: Int = 2
        const val DEFAULT_TIMEOUT_MS: Long = 20_000
    }
}
