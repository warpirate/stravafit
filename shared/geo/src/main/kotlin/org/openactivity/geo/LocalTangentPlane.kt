// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.geo

import org.openactivity.model.LatLng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A flat east/north plane in metres around [origin] (equirectangular approximation).
 *
 * Accurate to well under 0.1 % within a few kilometres of the origin, which is all that local geometry
 * (filters, point-to-segment distances, simplification) needs. Longitude differences are wrapped, so the
 * plane works across the antimeridian. [toLatLng] is the exact inverse of [toEnu].
 */
public class LocalTangentPlane(public val origin: LatLng) {
    private val metersPerDegLat = Spherical.EARTH_RADIUS_M * PI / 180.0
    private val metersPerDegLon = metersPerDegLat * cos(origin.latitude * PI / 180.0).coerceAtLeast(1e-9)

    /** East offset of [p] from the origin, in metres. */
    public fun east(p: LatLng): Double = east(p.longitude)

    /** North offset of [p] from the origin, in metres. */
    public fun north(p: LatLng): Double = north(p.latitude)

    /** East offset of a longitude from the origin, in metres. */
    public fun east(longitude: Double): Double =
        Spherical.normalizeLongitude(longitude - origin.longitude) * metersPerDegLon

    /** North offset of a latitude from the origin, in metres. */
    public fun north(latitude: Double): Double = (latitude - origin.latitude) * metersPerDegLat

    /** Converts [p] to (east, north) metres. */
    public fun toEnu(p: LatLng): DoubleArray = doubleArrayOf(east(p), north(p))

    /** Converts (east, north) metres back to a position. */
    public fun toLatLng(east: Double, north: Double): LatLng {
        val lat = (origin.latitude + north / metersPerDegLat).coerceIn(-90.0, 90.0)
        val lon = Spherical.normalizeLongitude(origin.longitude + east / metersPerDegLon)
        return LatLng(lat, lon)
    }
}

/** Planar geometry helpers for short distances. */
public object PlanarGeometry {
    /**
     * Shortest distance in metres from [p] to the segment [a]–[b], computed in a tangent plane at [p].
     * Falls back to the distance to [a] when the segment is degenerate.
     */
    public fun pointToSegmentDistance(p: LatLng, a: LatLng, b: LatLng): Double {
        val plane = LocalTangentPlane(p)
        val ax = plane.east(a)
        val ay = plane.north(a)
        val bx = plane.east(b)
        val by = plane.north(b)
        return pointToSegmentDistance(0.0, 0.0, ax, ay, bx, by)
    }

    /** Distance from point (px, py) to the segment (ax, ay)–(bx, by) in the plane. */
    public fun pointToSegmentDistance(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
