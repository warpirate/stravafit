// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.route

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.random.Random

/**
 * Test-only route shapes around a park in north London, built from bearings and distances so their
 * lengths are known exactly. None of this ships in the app.
 */
internal object RouteFixtures {
    val origin = LatLng(51.5313, -0.1570)

    /** A polyline from [start] following (bearing, metres) legs. */
    fun legs(start: LatLng, vararg legs: Pair<Double, Double>): List<LatLng> {
        val out = arrayListOf(start)
        for ((bearing, meters) in legs) out += Spherical.destination(out.last(), bearing, meters)
        return out
    }

    /** A square loop of [side] metres, starting and finishing at [origin]. */
    fun loop(side: Double = 500.0): List<LatLng> = legs(origin, 90.0 to side, 0.0 to side, 270.0 to side, 180.0 to side)

    /** Out [length] metres east and back the same way. */
    fun outAndBack(length: Double = 1000.0): List<LatLng> = legs(origin, 90.0 to length, 270.0 to length)

    /**
     * A figure-eight: a square loop north-east of [origin], then one south-west of it, crossing at
     * [origin] twice (at the start, in the middle and at the end).
     */
    fun figureEight(side: Double = 400.0): List<LatLng> =
        legs(origin, 90.0 to side, 0.0 to side, 270.0 to side, 180.0 to side, 270.0 to side, 180.0 to side, 90.0 to side, 0.0 to side)

    /**
     * Positions every [step] metres along [points], each pushed sideways by up to [noise] metres, like a
     * GPS track of someone running the route.
     */
    fun walk(points: List<LatLng>, step: Double = 5.0, noise: Double = 4.0, seed: Int = 7): List<LatLng> {
        val random = Random(seed)
        val out = ArrayList<LatLng>()
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val length = Spherical.distance(a, b)
            val bearing = Spherical.initialBearing(a, b)
            var d = 0.0
            while (d < length) {
                val on = Spherical.destination(a, bearing, d)
                val side = (random.nextDouble() * 2 - 1) * noise
                out += Spherical.destination(on, bearing + 90.0, side)
                d += step
            }
        }
        out += points.last()
        return out
    }

    /** Feeds [positions] one per second from [startSecond]; returns every update. */
    fun RouteFollower.feed(positions: List<LatLng>, startSecond: Int = 0, accuracy: Double = 5.0): List<RouteUpdate> {
        var travelled = state.travelledAtMatch
        var previous: LatLng? = null
        return positions.mapIndexed { i, p ->
            previous?.let { travelled += Spherical.distance(it, p) }
            previous = p
            onFix(p, (startSecond + i) * 1_000_000_000L, travelled, accuracy)
        }
    }
}
