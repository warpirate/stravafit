// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.openactivity.model.LatLng
import java.io.File
import java.io.IOException
import kotlin.math.floor

/**
 * Suggestions cached as small JSON files in the app's cache directory, keyed by a ~500 m location cell,
 * the sport and the target distance, so reopening Home or Maps does not route again. Entries are fresh
 * for [ttlMillis] (24 h); older ones are still returned as a fallback when the services cannot be reached.
 * The rounded position appears only in the file name; the routes themselves start at the real position.
 */
class SuggestionCache(
    private val directory: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    @Serializable
    internal data class Entry(val createdAtMillis: Long, val routes: List<RouteSuggestion>)

    /** The cache key for loops of [distanceMeters] for [sport] near [start]. */
    fun key(start: LatLng, sport: SuggestionSport, distanceMeters: Double): String {
        val lat = floor(start.latitude / CELL_DEGREES).toLong()
        val lon = floor(start.longitude / CELL_DEGREES).toLong()
        return "${sport.name.lowercase()}_${distanceMeters.toLong()}_${lat}_$lon"
    }

    /** Cached routes for [key]: fresh ones only, or also expired ones with [allowExpired]. Null when absent. */
    suspend fun get(key: String, allowExpired: Boolean = false): List<RouteSuggestion>? = withContext(Dispatchers.IO) {
        val entry = read(file(key)) ?: return@withContext null
        val age = nowMillis() - entry.createdAtMillis
        if (!allowExpired && (age > ttlMillis || age < 0)) null else entry.routes
    }

    suspend fun put(key: String, routes: List<RouteSuggestion>) = withContext(Dispatchers.IO) {
        try {
            directory.mkdirs()
            val target = file(key)
            val temp = File(directory, "${target.name}.tmp")
            temp.writeText(json.encodeToString(Entry.serializer(), Entry(nowMillis(), routes)))
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            prune()
        } catch (e: IOException) {
            // A cache that cannot be written only means routing again next time.
        }
    }

    /** A cached route by id, from any entry (fresh or not), or null. */
    suspend fun find(id: String): RouteSuggestion? = withContext(Dispatchers.IO) {
        directory.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty()
            .asSequence()
            .mapNotNull { read(it) }
            .flatMap { it.routes.asSequence() }
            .firstOrNull { it.id == id }
    }

    /** Deletes every cached suggestion (when the athlete turns suggestions off). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun prune() {
        val cutoff = nowMillis() - MAX_KEEP_MILLIS
        directory.listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private fun file(key: String) = File(directory, "$key$SUFFIX")

    private fun read(file: File): Entry? = try {
        if (file.isFile) json.decodeFromString(Entry.serializer(), file.readText()) else null
    } catch (e: IOException) {
        null
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 24L * 60 * 60 * 1000

        /** Entries older than a week are deleted, even as an offline fallback. */
        private const val MAX_KEEP_MILLIS = 7L * DEFAULT_TTL_MILLIS

        /** 0.005 degrees: about 550 m of latitude. */
        const val CELL_DEGREES = 0.005
        private const val SUFFIX = ".json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
