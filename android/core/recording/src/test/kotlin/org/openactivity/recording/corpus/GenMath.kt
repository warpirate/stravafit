// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import kotlin.random.Random

/**
 * Geometry and random helpers for [TrackGen], built on [StrictMath] so that generated corpus files are
 * bit-for-bit reproducible on every JVM and CPU (java.lang.Math may differ in the last ulp).
 */
internal object GenMath {
    const val EARTH_RADIUS_M = 6_371_008.8
    private const val DEG = StrictMath.PI / 180.0

    fun normalizeLon(lon: Double): Double {
        var l = lon
        while (l > 180.0) l -= 360.0
        while (l < -180.0) l += 360.0
        return l
    }

    fun normalizeBearing(b: Double): Double {
        var x = b % 360.0
        if (x < 0) x += 360.0
        return x
    }

    /** Point reached from (lat, lon) after [distance] metres on initial [bearingDeg]. */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distance: Double): DoubleArray {
        val delta = distance / EARTH_RADIUS_M
        val theta = bearingDeg * DEG
        val phi1 = lat * DEG
        val sinPhi2 = StrictMath.sin(phi1) * StrictMath.cos(delta) +
            StrictMath.cos(phi1) * StrictMath.sin(delta) * StrictMath.cos(theta)
        val phi2 = StrictMath.asin(sinPhi2.coerceIn(-1.0, 1.0))
        val y = StrictMath.sin(theta) * StrictMath.sin(delta) * StrictMath.cos(phi1)
        val x = StrictMath.cos(delta) - StrictMath.sin(phi1) * sinPhi2
        val lambda2 = lon * DEG + StrictMath.atan2(y, x)
        return doubleArrayOf(phi2 / DEG, normalizeLon(lambda2 / DEG))
    }

    /** Haversine distance in metres. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dPhi = (lat2 - lat1) * DEG
        val dLambda = normalizeLon(lon2 - lon1) * DEG
        val s = StrictMath.sin(dPhi / 2)
        val t = StrictMath.sin(dLambda / 2)
        val h = s * s + StrictMath.cos(lat1 * DEG) * StrictMath.cos(lat2 * DEG) * t * t
        return 2 * EARTH_RADIUS_M * StrictMath.asin(StrictMath.min(1.0, StrictMath.sqrt(h)))
    }

    /** Initial bearing in degrees. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * DEG
        val phi2 = lat2 * DEG
        val dLambda = normalizeLon(lon2 - lon1) * DEG
        val y = StrictMath.sin(dLambda) * StrictMath.cos(phi2)
        val x = StrictMath.cos(phi1) * StrictMath.sin(phi2) - StrictMath.sin(phi1) * StrictMath.cos(phi2) * StrictMath.cos(dLambda)
        return normalizeBearing(StrictMath.atan2(y, x) / DEG)
    }

    /** Offsets (lat, lon) by [east] and [north] metres (equirectangular, fine for metre-scale noise). */
    fun offset(lat: Double, lon: Double, east: Double, north: Double): DoubleArray {
        val mPerDegLat = EARTH_RADIUS_M * DEG
        val mPerDegLon = mPerDegLat * StrictMath.cos(lat * DEG)
        return doubleArrayOf(lat + north / mPerDegLat, normalizeLon(lon + east / mPerDegLon))
    }

    /** ISA barometric formula, inverse direction (altitude to pressure). */
    fun altitudeToPressure(meters: Double): Double = 1013.25 * StrictMath.pow(1.0 - meters / 44_330.0, 5.255)

    fun exp(x: Double): Double = StrictMath.exp(x)

    fun sin(x: Double): Double = StrictMath.sin(x)

    fun sqrt(x: Double): Double = StrictMath.sqrt(x)
}

/** Standard normal samples (Box–Muller on [StrictMath]) from a seeded [Random]. */
internal class Gaussian(private val rnd: Random) {
    private var spare = Double.NaN

    fun next(): Double {
        if (!spare.isNaN()) {
            val s = spare
            spare = Double.NaN
            return s
        }
        var u: Double
        do u = rnd.nextDouble() while (u <= 1e-300)
        val v = rnd.nextDouble()
        val r = StrictMath.sqrt(-2.0 * StrictMath.log(u))
        val theta = 2.0 * StrictMath.PI * v
        spare = r * StrictMath.sin(theta)
        return r * StrictMath.cos(theta)
    }
}
