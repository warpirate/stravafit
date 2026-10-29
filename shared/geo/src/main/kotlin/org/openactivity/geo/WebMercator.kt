// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.geo

import org.openactivity.model.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * A point in Web Mercator (EPSG:3857) metres.
 *
 * @property x easting in metres, in [-[WebMercator.HALF_WORLD_M], [WebMercator.HALF_WORLD_M]].
 * @property y northing in metres.
 */
public data class MercatorPoint(val x: Double, val y: Double)

/**
 * Spherical Web Mercator projection, as used by OpenStreetMap and MapLibre tiles.
 *
 * Latitudes are clamped to ±[MAX_LATITUDE] so polar points (for example a ski tour on Svalbard) still
 * project to finite coordinates.
 */
public object WebMercator {
    /** The latitude at which the Web Mercator world becomes square. */
    public const val MAX_LATITUDE: Double = 85.05112877980659

    /** Sphere radius defined by EPSG:3857. */
    public const val RADIUS_M: Double = 6_378_137.0

    /** Half the width of the projected world, in metres. */
    public const val HALF_WORLD_M: Double = PI * RADIUS_M

    private const val DEG = PI / 180.0

    /** Projects a position to Web Mercator metres. */
    public fun project(p: LatLng): MercatorPoint {
        val lat = p.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val x = RADIUS_M * p.longitude * DEG
        val y = RADIUS_M * ln(tan(PI / 4 + lat * DEG / 2))
        return MercatorPoint(x, y)
    }

    /** Inverse of [project]. */
    public fun unproject(m: MercatorPoint): LatLng {
        val lon = Spherical.normalizeLongitude(m.x / RADIUS_M / DEG)
        val lat = (2 * atan(exp(m.y / RADIUS_M)) - PI / 2) / DEG
        return LatLng(lat.coerceIn(-90.0, 90.0), lon)
    }

    /**
     * Projects to normalised world coordinates in [0, 1] with the origin at the top-left (north-west),
     * the convention used by slippy-map tiles. Multiply by `256 * 2^zoom` for pixel coordinates.
     */
    public fun toWorld(p: LatLng): Pair<Double, Double> {
        val m = project(p)
        return Pair((m.x + HALF_WORLD_M) / (2 * HALF_WORLD_M), (HALF_WORLD_M - m.y) / (2 * HALF_WORLD_M))
    }
}
