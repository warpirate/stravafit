// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * Shape measures for routed loops: how much of a loop runs over itself (out-and-back stretches to a
 * waypoint that snapped onto a dead end), and how similar two loops are.
 */
object RouteShape {
    private const val SAMPLE_STEP_METERS = 10.0
    private const val REUSE_CELL_METERS = 20.0

    /** A stretch only counts as reused when it comes back to a place passed at least this much earlier. */
    private const val MIN_RETURN_GAP_METERS = 150.0
    private const val SIMILARITY_CELL_METERS = 60.0

    /**
     * Fraction (0..1) of the route's length that runs within about 20-40 m of a part of the same route
     * passed at least 150 m earlier.
     */
    fun reuseFraction(points: List<LatLng>): Double {
        val samples = resample(points, SAMPLE_STEP_METERS)
        if (samples.size < 2) return 0.0
        val projection = Projection(samples.first().second)
        // The first distance along the route at which each grid cell was visited.
        val firstVisit = HashMap<Long, Double>()
        var reused = 0
        for ((s, p) in samples) {
            val (cx, cy) = projection.cell(p, REUSE_CELL_METERS)
            if (visitedEarlier(firstVisit, cx, cy, s)) reused++
            firstVisit.putIfAbsent(key(cx, cy), s)
        }
        return reused.toDouble() / samples.size
    }

    private fun visitedEarlier(firstVisit: Map<Long, Double>, cx: Int, cy: Int, s: Double): Boolean {
        for (dx in -1..1) {
            for (dy in -1..1) {
                val earlier = firstVisit[key(cx + dx, cy + dy)] ?: continue
                if (s - earlier > MIN_RETURN_GAP_METERS) return true
            }
        }
        return false
    }

    /** Jaccard similarity (0..1) of the ~60 m grid cells two routes pass through. */
    fun similarity(a: List<LatLng>, b: List<LatLng>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val projection = Projection(a.first())
        val ca = cells(a, projection)
        val cb = cells(b, projection)
        val union = (ca union cb).size
        return if (union == 0) 0.0 else (ca intersect cb).size.toDouble() / union
    }

    /** Points every [stepMeters] along [points], each paired with its distance from the start. */
    fun resample(points: List<LatLng>, stepMeters: Double): List<Pair<Double, LatLng>> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Double, LatLng>>()
        out += 0.0 to points.first()
        var travelled = 0.0
        var next = stepMeters
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = Spherical.distance(a, b)
            if (d <= 0.0) continue
            while (next <= travelled + d) {
                val t = (next - travelled) / d
                out += next to LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                next += stepMeters
            }
            travelled += d
        }
        if (out.last().first < travelled) out += travelled to points.last()
        return out
    }

    private fun cells(points: List<LatLng>, projection: Projection): Set<Long> =
        resample(points, SIMILARITY_CELL_METERS / 2)
            .map { (_, p) -> projection.cell(p, SIMILARITY_CELL_METERS).let { key(it.first, it.second) } }
            .toSet()

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

    /** Equirectangular projection around an origin; accurate to well under a metre over a few km. */
    private class Projection(origin: LatLng) {
        private val metersPerDegLat = Spherical.EARTH_RADIUS_M * PI / 180.0
        private val metersPerDegLon = metersPerDegLat * cos(origin.latitude * PI / 180.0)

        fun cell(p: LatLng, size: Double): Pair<Int, Int> =
            floor(p.longitude * metersPerDegLon / size).toInt() to floor(p.latitude * metersPerDegLat / size).toInt()
    }
}
