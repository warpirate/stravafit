// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.openactivity.metrics.ElevationMetrics
import org.openactivity.metrics.ElevationTotals
import org.openactivity.model.LatLng
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Terrain elevation from Open-Meteo's elevation API (Copernicus 90 m DEM). Batches up to 100 points per
 * request and remembers elevations by position (about 11 m grid) for the life of the process.
 */
class ElevationClient(
    private val transport: HttpTransport,
    private val limiter: RateLimiter,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val memory = object : LinkedHashMap<Long, Double>(MEMORY_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Double>?) = size > MEMORY_SIZE
    }

    /**
     * Elevation in metres of each of [points], in order.
     *
     * @throws java.io.IOException when the service cannot be reached or answers with an error.
     */
    suspend fun elevations(points: List<LatLng>): List<Double> {
        val missing = synchronized(memory) { points.map(::key).distinct().filter { it !in memory } }
        val byKey = points.associateBy(::key)
        for (batch in missing.chunked(MAX_PER_REQUEST)) {
            val batchPoints = batch.map { byKey.getValue(it) }
            val response = limiter.run { transport.get(url(batchPoints)) }
            if (response.code != HTTP_OK) throw RoutingServiceException("Elevation service answered ${response.code}")
            val values = parse(response.body)
            if (values.size != batch.size) throw RoutingServiceException("Elevation service returned ${values.size} of ${batch.size} points")
            synchronized(memory) { batch.zip(values).forEach { (k, v) -> memory[k] = v } }
        }
        return synchronized(memory) { points.map { memory.getValue(key(it)) } }
    }

    fun url(points: List<LatLng>): String {
        val lat = points.joinToString(",") { String.format(Locale.ROOT, "%.5f", it.latitude) }
        val lon = points.joinToString(",") { String.format(Locale.ROOT, "%.5f", it.longitude) }
        return "$baseUrl/v1/elevation?latitude=$lat&longitude=$lon"
    }

    @Serializable
    internal data class ElevationResponse(val elevation: List<Double?> = emptyList())

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com"
        const val MAX_PER_REQUEST = 100
        private const val HTTP_OK = 200
        private const val MEMORY_SIZE = 4_000
        private val json = Json { ignoreUnknownKeys = true }

        /** Elevations from an Open-Meteo response; missing values (no data) read as sea level. */
        fun parse(body: String): List<Double> = try {
            json.decodeFromString<ElevationResponse>(body).elevation.map { it?.takeIf { v -> v.isFinite() } ?: 0.0 }
        } catch (e: SerializationException) {
            throw RoutingServiceException("Unreadable elevation response")
        } catch (e: IllegalArgumentException) {
            throw RoutingServiceException("Unreadable elevation response")
        }

        /** Position key on a 1e-4 degree grid. */
        private fun key(p: LatLng): Long {
            val lat = (p.latitude * 1e4).roundToLong()
            val lon = (p.longitude * 1e4).roundToLong()
            return lat * 4_000_000L + lon
        }
    }
}

/** Gain and loss over a planned route's elevation profile. */
object RouteElevation {
    /** Terrain-model elevations are smooth, so a small band filters only the DEM's step noise. */
    private const val HYSTERESIS_METERS = 1.0
    private const val MEDIAN_WINDOW = 5

    /**
     * Gain and loss of [elevations] in route order, or null with fewer than two. The last value is
     * repeated so the trailing median also counts a final climb or descent.
     */
    fun totals(elevations: List<Double>): ElevationTotals? {
        if (elevations.size < 2) return null
        val flushed = elevations + List(MEDIAN_WINDOW / 2) { elevations.last() }
        return ElevationMetrics.compute(flushed.toDoubleArray(), HYSTERESIS_METERS, MEDIAN_WINDOW)
    }
}
