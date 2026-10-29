// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.geo

import org.openactivity.model.LatLng

/**
 * A latitude/longitude box.
 *
 * When [crossesAntimeridian] is true the box runs east from [west] across 180° to [east], so [west] is
 * numerically greater than [east] (for example west = 179.5, east = -179.5 is a 1° wide box).
 */
public data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** True when the box spans the ±180° meridian. */
    val crossesAntimeridian: Boolean get() = west > east

    /** Width of the box in degrees of longitude, always in [0, 360]. */
    val widthDegrees: Double get() = if (crossesAntimeridian) 360.0 - west + east else east - west

    /** True when [p] lies inside the box (edges included). */
    public operator fun contains(p: LatLng): Boolean {
        if (p.latitude < south || p.latitude > north) return false
        return if (crossesAntimeridian) p.longitude >= west || p.longitude <= east else p.longitude in west..east
    }

    public companion object {
        /**
         * The smallest box containing all [points], or null for an empty list.
         *
         * The longitude range is the complement of the largest empty gap between the sorted longitudes, so
         * a track that crosses the antimeridian gets a narrow box rather than one spanning the whole world.
         */
        public fun of(points: Collection<LatLng>): BoundingBox? {
            if (points.isEmpty()) return null
            var south = Double.POSITIVE_INFINITY
            var north = Double.NEGATIVE_INFINITY
            val lons = DoubleArray(points.size)
            var i = 0
            for (p in points) {
                if (p.latitude < south) south = p.latitude
                if (p.latitude > north) north = p.latitude
                lons[i++] = p.longitude
            }
            lons.sort()
            // Largest gap between neighbours, including the wrap-around gap from the last to the first.
            var gap = lons[0] + 360.0 - lons[lons.size - 1]
            var west = lons[0]
            var east = lons[lons.size - 1]
            for (k in 1 until lons.size) {
                val g = lons[k] - lons[k - 1]
                if (g > gap) {
                    gap = g
                    west = lons[k]
                    east = lons[k - 1]
                }
            }
            return BoundingBox(south, west, north, east)
        }
    }
}
