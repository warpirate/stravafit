// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.geo

import org.openactivity.model.LatLng
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle helpers on a spherical Earth.
 *
 * A sphere with the IUGG mean radius is within about 0.5 % of the ellipsoid everywhere, and the error
 * between consecutive GPS samples is far below GPS noise. Every distance in the app uses these
 * functions, so the phone and the server compute identical numbers.
 */
public object Spherical {
    /** IUGG mean Earth radius in metres. */
    public const val EARTH_RADIUS_M: Double = 6_371_008.8

    private const val DEG = PI / 180.0

    /** Great-circle distance in metres between two points (haversine formula). */
    public fun distance(a: LatLng, b: LatLng): Double = distance(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Great-circle distance in metres between two points given in degrees (haversine formula). */
    public fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * DEG
        val phi2 = lat2 * DEG
        val dPhi = (lat2 - lat1) * DEG
        val dLambda = normalizeLongitude(lon2 - lon1) * DEG
        val s = sin(dPhi / 2)
        val t = sin(dLambda / 2)
        val h = s * s + cos(phi1) * cos(phi2) * t * t
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Initial bearing (forward azimuth) from [from] to [to] in degrees clockwise from north, in [0, 360). */
    public fun initialBearing(from: LatLng, to: LatLng): Double {
        val phi1 = from.latitude * DEG
        val phi2 = to.latitude * DEG
        val dLambda = normalizeLongitude(to.longitude - from.longitude) * DEG
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalizeBearing(atan2(y, x) / DEG)
    }

    /** The point reached by travelling [distanceMeters] from [from] along the great circle with [bearingDeg]. */
    public fun destination(from: LatLng, bearingDeg: Double, distanceMeters: Double): LatLng {
        val delta = distanceMeters / EARTH_RADIUS_M
        val theta = bearingDeg * DEG
        val phi1 = from.latitude * DEG
        val lambda1 = from.longitude * DEG
        val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        val phi2 = asin(sinPhi2.coerceIn(-1.0, 1.0))
        val y = sin(theta) * sin(delta) * cos(phi1)
        val x = cos(delta) - sin(phi1) * sinPhi2
        val lambda2 = lambda1 + atan2(y, x)
        return LatLng((phi2 / DEG).coerceIn(-90.0, 90.0), normalizeLongitude(lambda2 / DEG))
    }

    /** Sum of great-circle distances along [points], in metres. */
    public fun pathLength(points: List<LatLng>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        return total
    }

    /** Wraps a longitude (or longitude difference) into [-180, 180]. */
    public fun normalizeLongitude(lon: Double): Double {
        if (lon in -180.0..180.0) return lon
        val wrapped = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return if (wrapped == -180.0 && lon > 0) 180.0 else wrapped
    }

    /** Wraps a bearing into [0, 360). */
    public fun normalizeBearing(deg: Double): Double {
        val b = deg % 360.0
        return if (b < 0) b + 360.0 else b
    }
}
