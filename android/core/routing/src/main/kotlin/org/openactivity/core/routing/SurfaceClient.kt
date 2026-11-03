// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.openactivity.geo.PlanarGeometry
import org.openactivity.geo.Spherical
import org.openactivity.polyline.DouglasPeucker
import org.openactivity.model.LatLng
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.floor

/**
 * How much of a route is paved, unpaved or of unknown surface, in metres, from OpenStreetMap tags.
 */
@Serializable
data class SurfaceMix(val pavedMeters: Double, val unpavedMeters: Double, val unknownMeters: Double) {
    private val total: Double get() = pavedMeters + unpavedMeters + unknownMeters

    /** Share of the route on paved ways, 0 to 1 (unknown counts as not paved). */
    val pavedShare: Double get() = if (total > 0) pavedMeters / total else 0.0

    /** Share of the route on unpaved ways (gravel, dirt, grass, ...), 0 to 1. */
    val unpavedShare: Double get() = if (total > 0) unpavedMeters / total else 0.0
}

/** What a way is made of, from its `surface` tag or, without one, its `highway` type. */
enum class Surface { PAVED, UNPAVED, UNKNOWN }

/**
 * Looks up road surfaces along routes with one Overpass query: every way with a `highway` tag within a
 * few metres of each route line, with tags and geometry. Points every [SAMPLE_METERS] along a route are
 * then matched to the nearest of those ways, so the result is measured along the route.
 *
 * Geometry is matched rather than OSRM node ids: OSRM prints large ids as rounded floats.
 *
 * @param endpoints Overpass interpreters to try in order; the public ones are often busy.
 */
class SurfaceClient(
    private val transport: HttpTransport,
    private val limiter: RateLimiter,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {
    /**
     * The surface mix of each route in [routes] (id to its line). Lines of fewer than two points are left out.
     *
     * @throws IOException when no Overpass server could answer.
     */
    suspend fun surfaces(routes: Map<String, List<LatLng>>): Map<String, SurfaceMix> {
        val lines = routes.filterValues { it.size >= 2 }
        if (lines.isEmpty()) return emptyMap()
        val form = "data=" + URLEncoder.encode(query(lines.values.toList()), "UTF-8")
        var lastError: IOException? = null
        for (endpoint in endpoints) {
            try {
                val response = limiter.run { transport.postForm(endpoint, form) }
                if (response.code != HTTP_OK) throw RoutingServiceException("Overpass answered ${response.code}")
                val index = WayIndex(parse(response.body))
                return lines.mapValues { (_, points) -> index.mix(points) }
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No Overpass server configured")
    }

    @Serializable
    internal data class OverpassResponse(val elements: List<Way> = emptyList())

    @Serializable
    internal data class Way(val id: Long = 0, val geometry: List<Point?> = emptyList(), val tags: Map<String, String> = emptyMap())

    @Serializable
    internal data class Point(val lat: Double, val lon: Double)

    /** Way segments in a grid of [CELL_DEGREES] cells, for nearest-way lookups. */
    internal class WayIndex(ways: List<Way>) {
        private class Segment(val a: LatLng, val b: LatLng, val surface: Surface)

        private val cells = HashMap<Long, MutableList<Segment>>()

        init {
            for (way in ways) {
                val surface = classify(way.tags)
                val points = way.geometry.filterNotNull().map { LatLng(it.lat, it.lon) }
                for ((a, b) in points.zipWithNext()) {
                    val segment = Segment(a, b, surface)
                    // A segment goes in every cell its bounding box touches.
                    for (x in cell(minOf(a.longitude, b.longitude))..cell(maxOf(a.longitude, b.longitude))) {
                        for (y in cell(minOf(a.latitude, b.latitude))..cell(maxOf(a.latitude, b.latitude))) {
                            cells.getOrPut(key(x, y)) { mutableListOf() } += segment
                        }
                    }
                }
            }
        }

        /** Surface of the nearest way within [MATCH_METERS] of [p], or null when none is that close. */
        fun surfaceAt(p: LatLng): Surface? {
            val cx = cell(p.longitude)
            val cy = cell(p.latitude)
            var best: Segment? = null
            var bestDistance = MATCH_METERS
            for (x in cx - 1..cx + 1) {
                for (y in cy - 1..cy + 1) {
                    for (s in cells[key(x, y)].orEmpty()) {
                        val d = PlanarGeometry.pointToSegmentDistance(p, s.a, s.b)
                        if (d <= bestDistance) {
                            bestDistance = d
                            best = s
                        }
                    }
                }
            }
            return best?.surface
        }

        /** Walks [points] in [SAMPLE_METERS] steps, adding each step to the surface found at its middle. */
        fun mix(points: List<LatLng>): SurfaceMix {
            var paved = 0.0
            var unpaved = 0.0
            var unknown = 0.0
            for ((a, b) in points.zipWithNext()) {
                val length = Spherical.distance(a, b)
                val steps = maxOf(1, (length / SAMPLE_METERS).toInt())
                val step = length / steps
                for (i in 0 until steps) {
                    val t = (i + 0.5) / steps
                    val mid = LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                    when (surfaceAt(mid)) {
                        Surface.PAVED -> paved += step
                        Surface.UNPAVED -> unpaved += step
                        Surface.UNKNOWN, null -> unknown += step
                    }
                }
            }
            return SurfaceMix(paved, unpaved, unknown)
        }

        private fun cell(degrees: Double): Int = floor(degrees / CELL_DEGREES).toInt()

        private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        val DEFAULT_ENDPOINTS: List<String> = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
        private const val HTTP_OK = 200

        /** Ways this close to a route line are fetched; a little wider than GPS-snapped geometry strays. */
        const val AROUND_METERS: Int = 12

        /** A route point matches a way at most this far away. */
        const val MATCH_METERS: Double = 15.0

        /** Route length per surface sample. */
        const val SAMPLE_METERS: Double = 25.0

        /** Route lines are simplified this much for the query, which keeps it short. */
        private const val QUERY_SIMPLIFY_METERS = 5.0

        /** Grid cell size, about 33 m north-south; a match is always within the 3×3 cells around a point. */
        private const val CELL_DEGREES = 0.0003

        private val json = Json { ignoreUnknownKeys = true }

        private val PAVED = setOf(
            "paved", "asphalt", "chipseal", "concrete", "concrete:plates", "concrete:lanes", "paving_stones",
            "sett", "cobblestone", "unhewn_cobblestone", "bricks", "metal", "wood", "rubber", "tartan",
        )
        private val UNPAVED = setOf(
            "unpaved", "compacted", "fine_gravel", "gravel", "rock", "pebblestone", "ground", "dirt", "earth",
            "grass", "grass_paver", "mud", "sand", "woodchips", "clay", "snow", "ice", "salt",
        )
        private val PAVED_HIGHWAYS = setOf(
            "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service",
            "living_street", "pedestrian", "footway", "cycleway", "steps", "busway",
            "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
        )
        private val UNPAVED_HIGHWAYS = setOf("track", "path", "bridleway")

        /** Every way with a highway tag within [AROUND_METERS] of any of [lines], with tags and geometry. */
        fun query(lines: List<List<LatLng>>): String = buildString {
            append("[out:json][timeout:25];(")
            for (line in lines) {
                append("way(around:").append(AROUND_METERS)
                for (p in DouglasPeucker.simplify(line, QUERY_SIMPLIFY_METERS)) {
                    append(',').append(String.format(Locale.ROOT, "%.6f,%.6f", p.latitude, p.longitude))
                }
                append(")[highway];")
            }
            append(");out tags geom;")
        }

        /**
         * Parses an Overpass response.
         *
         * @throws RoutingServiceException for a body that is not Overpass JSON (a busy server answers HTML).
         */
        internal fun parse(body: String): List<Way> = try {
            json.decodeFromString<OverpassResponse>(body).elements
        } catch (e: SerializationException) {
            throw RoutingServiceException("Unreadable Overpass response")
        } catch (e: IllegalArgumentException) {
            throw RoutingServiceException("Unreadable Overpass response")
        }

        /** A way's surface: the `surface` tag wins; otherwise tracks and paths count as unpaved, roads as paved. */
        fun classify(tags: Map<String, String>): Surface {
            val surface = tags["surface"]?.substringBefore(';')?.trim()
            if (surface in PAVED) return Surface.PAVED
            if (surface in UNPAVED) return Surface.UNPAVED
            val highway = tags["highway"] ?: return Surface.UNKNOWN
            if (highway == "track") return if (tags["tracktype"] == "grade1") Surface.PAVED else Surface.UNPAVED
            return when (highway) {
                in UNPAVED_HIGHWAYS -> Surface.UNPAVED
                in PAVED_HIGHWAYS -> Surface.PAVED
                else -> Surface.UNKNOWN
            }
        }
    }
}
