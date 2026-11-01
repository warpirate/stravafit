// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.math.PI

/**
 * Waypoints for a round trip: the start and 2-4 waypoints lie on a circle whose circumference is the
 * target distance divided by the route factor (roads are longer than the circle they wind around).
 */
object LoopGeometry {
    /** Typical ratio of a routed loop's length to its circle in towns; refined as routes come back. */
    const val DEFAULT_ROUTE_FACTOR = 1.3
    const val MIN_ROUTE_FACTOR = 1.0
    const val MAX_ROUTE_FACTOR = 2.5
    const val MAX_WAYPOINTS = 6

    /** Radius in metres of the circle for a loop of [targetMeters] with [routeFactor]. */
    fun radiusFor(targetMeters: Double, routeFactor: Double = DEFAULT_ROUTE_FACTOR): Double {
        require(targetMeters > 0) { "target distance must be positive" }
        return targetMeters / (2 * PI * routeFactor.coerceIn(MIN_ROUTE_FACTOR, MAX_ROUTE_FACTOR))
    }

    /** Route factor implied by a routed length of [routedMeters] around a circle of [radiusMeters]. */
    fun observedFactor(routedMeters: Double, radiusMeters: Double): Double =
        (routedMeters / (2 * PI * radiusMeters)).coerceIn(MIN_ROUTE_FACTOR, MAX_ROUTE_FACTOR)

    /** The centre of the circle: [radiusMeters] from [start] towards [bearingDeg], so the start lies on it. */
    fun center(start: LatLng, radiusMeters: Double, bearingDeg: Double): LatLng =
        Spherical.destination(start, bearingDeg, radiusMeters)

    /**
     * [count] waypoints evenly spaced around the circle, clockwise from the start (seen from above),
     * excluding the start itself.
     */
    fun waypoints(start: LatLng, radiusMeters: Double, bearingDeg: Double, count: Int): List<LatLng> {
        require(count in 1..MAX_WAYPOINTS) { "waypoint count must be 1..$MAX_WAYPOINTS, was $count" }
        val center = center(start, radiusMeters, bearingDeg)
        val startAngle = bearingDeg + 180.0
        return (1..count).map { k ->
            Spherical.destination(center, Spherical.normalizeBearing(startAngle + k * 360.0 / (count + 1)), radiusMeters)
        }
    }

    /** The full loop to route: start, the waypoints, and back to the start. */
    fun loop(start: LatLng, radiusMeters: Double, bearingDeg: Double, count: Int): List<LatLng> =
        listOf(start) + waypoints(start, radiusMeters, bearingDeg, count) + start

    /** "N", "NE", ... for a bearing. */
    fun compass(bearingDeg: Double): String {
        val index = ((Spherical.normalizeBearing(bearingDeg) + 22.5) / 45.0).toInt() % COMPASS.size
        return COMPASS[index]
    }

    private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
}
