// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.route

import org.openactivity.geo.LocalTangentPlane
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.math.hypot

/**
 * Where a position lies relative to a route.
 *
 * @property segment index of the route segment (from point [segment] to point `segment + 1`).
 * @property alongMeters distance from the route start to the closest point on that segment.
 * @property distanceMeters distance from the position to that closest point (cross-track).
 */
public data class RouteProjection(val segment: Int, val alongMeters: Double, val distanceMeters: Double)

/**
 * A route polyline prepared for matching: consecutive duplicate points are dropped, cumulative distance is
 * measured along the great circle, and projections use one local tangent plane (accurate to well under a
 * metre at the 40 m scale off-route detection works at, even for routes a hundred kilometres long).
 *
 * @throws IllegalArgumentException when fewer than two distinct points remain.
 */
public class RoutePath(points: List<LatLng>) {
    /** The route points without consecutive duplicates. */
    public val points: List<LatLng>

    /** Cumulative distance from the start at each of [points], in metres. */
    private val cumulative: DoubleArray
    private val plane: LocalTangentPlane
    private val xs: DoubleArray
    private val ys: DoubleArray

    init {
        val distinct = ArrayList<LatLng>(points.size)
        for (p in points) {
            if (!p.latitude.isFinite() || !p.longitude.isFinite()) continue
            if (distinct.isEmpty() || distinct.last() != p) distinct += p
        }
        require(distinct.size >= 2) { "a route needs at least two distinct points" }
        this.points = distinct
        plane = LocalTangentPlane(distinct.first())
        cumulative = DoubleArray(distinct.size)
        xs = DoubleArray(distinct.size)
        ys = DoubleArray(distinct.size)
        for (i in distinct.indices) {
            xs[i] = plane.east(distinct[i])
            ys[i] = plane.north(distinct[i])
            if (i > 0) cumulative[i] = cumulative[i - 1] + Spherical.distance(distinct[i - 1], distinct[i])
        }
    }

    /** Total length in metres. */
    public val lengthMeters: Double get() = cumulative.last()

    /** Number of segments. */
    public val segmentCount: Int get() = points.size - 1

    public val start: LatLng get() = points.first()

    public val finish: LatLng get() = points.last()

    /** Cumulative distance at the start of [segment]. */
    public fun alongAtSegmentStart(segment: Int): Double = cumulative[segment]

    /** Cumulative distance at the end of [segment]. */
    public fun alongAtSegmentEnd(segment: Int): Double = cumulative[segment + 1]

    /** Projects [p] onto [segment]. */
    public fun project(p: LatLng, segment: Int): RouteProjection {
        val px = plane.east(p)
        val py = plane.north(p)
        return projectPlanar(px, py, segment)
    }

    /** The closest point of the whole route to [p] (the earliest one on ties). */
    public fun nearest(p: LatLng): RouteProjection {
        val px = plane.east(p)
        val py = plane.north(p)
        var best: RouteProjection? = null
        for (s in 0 until segmentCount) {
            val candidate = projectPlanar(px, py, s)
            if (best == null || candidate.distanceMeters < best.distanceMeters) best = candidate
        }
        return requireNotNull(best)
    }

    /** Projections of [p] onto every segment, in route order (for callers that rank candidates themselves). */
    public fun projectAll(p: LatLng): List<RouteProjection> {
        val px = plane.east(p)
        val py = plane.north(p)
        return List(segmentCount) { projectPlanar(px, py, it) }
    }

    /** The point [alongMeters] from the start (clamped to the route). */
    public fun pointAt(alongMeters: Double): LatLng {
        val d = alongMeters.coerceIn(0.0, lengthMeters)
        var lo = 0
        var hi = segmentCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cumulative[mid] <= d) lo = mid else hi = mid - 1
        }
        val length = cumulative[lo + 1] - cumulative[lo]
        val t = if (length > 0.0) (d - cumulative[lo]) / length else 0.0
        return plane.toLatLng(xs[lo] + t * (xs[lo + 1] - xs[lo]), ys[lo] + t * (ys[lo + 1] - ys[lo]))
    }

    private fun projectPlanar(px: Double, py: Double, s: Int): RouteProjection {
        val ax = xs[s]
        val ay = ys[s]
        val dx = xs[s + 1] - ax
        val dy = ys[s + 1] - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val along = cumulative[s] + t * (cumulative[s + 1] - cumulative[s])
        return RouteProjection(s, along, hypot(px - cx, py - cy))
    }
}
