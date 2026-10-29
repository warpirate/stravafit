// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.sample

import org.openactivity.app.model.LatLng
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Deterministic synthetic GPS tracks for sample data and previews. The same seed always yields
 * the same track, so screenshots are stable.
 */
object SampleRoutes {

    private const val KM_PER_DEG_LAT = 111.32
    private const val SMOOTHING_PASSES = 10

    /**
     * A closed loop around [center]: a circle of [radiusKm] deformed by [lobes] bulges and
     * smoothed random wobble.
     */
    fun loop(center: LatLng, radiusKm: Double, seed: Long, lobes: Int = 3, points: Int = 160): List<LatLng> {
        val noise = smoothNoise(points, seed)
        val phase = Random(seed).nextDouble() * 2 * PI
        return List(points + 1) { index ->
            val i = index % points
            val t = 2 * PI * i / points
            val r = radiusKm * (1 + 0.28 * sin(lobes * t + phase) + 0.12 * noise[i])
            offset(center, r * cos(t), r * sin(t) * 0.8)
        }
    }

    /** An out-and-back line heading [bearingDeg] for [lengthKm], with a slightly different return leg. */
    fun outAndBack(start: LatLng, lengthKm: Double, bearingDeg: Double, seed: Long, points: Int = 80): List<LatLng> {
        val noise = smoothNoise(points, seed)
        val bearing = Math.toRadians(bearingDeg)
        val out = List(points) { i ->
            val d = lengthKm * i / (points - 1)
            val lateral = 0.18 * lengthKm * sin(PI * i / (points - 1) * 2) * 0.4 + 0.05 * lengthKm * noise[i]
            offset(start, d * sin(bearing) + lateral * cos(bearing), d * cos(bearing) - lateral * sin(bearing))
        }
        val back = out.reversed().mapIndexed { i, p ->
            val shift = 0.02 * lengthKm * sin(PI * i / (points - 1))
            offset(p, shift * cos(bearing), -shift * sin(bearing))
        }
        return out + back
    }

    /** A meandering point-to-point line, e.g. a commute or a trail. */
    fun pointToPoint(start: LatLng, lengthKm: Double, bearingDeg: Double, seed: Long, points: Int = 120): List<LatLng> {
        val noise = smoothNoise(points, seed)
        val random = Random(seed)
        val phaseA = random.nextDouble() * 2 * PI
        val phaseB = random.nextDouble() * 2 * PI
        val bearing = Math.toRadians(bearingDeg)
        return List(points) { i ->
            val t = i.toDouble() / (points - 1)
            val d = lengthKm * t
            // Two gentle bends plus a little wobble: reads like a road, not GPS noise.
            val lateral = lengthKm * (0.09 * sin(2 * PI * 1.2 * t + phaseA) + 0.03 * sin(2 * PI * 3.5 * t + phaseB) + 0.012 * noise[i])
            offset(start, d * sin(bearing) + lateral * cos(bearing), d * cos(bearing) - lateral * sin(bearing))
        }
    }

    private fun offset(origin: LatLng, eastKm: Double, northKm: Double): LatLng {
        val lat = origin.latitude + northKm / KM_PER_DEG_LAT
        val lng = origin.longitude + eastKm / (KM_PER_DEG_LAT * cos(Math.toRadians(origin.latitude)))
        return LatLng(lat, lng)
    }

    /** Random values in roughly [-1, 1], smoothed so tracks look like streets rather than static. */
    private fun smoothNoise(count: Int, seed: Long): DoubleArray {
        val random = Random(seed)
        var values = DoubleArray(count) { random.nextDouble() * 2 - 1 }
        repeat(SMOOTHING_PASSES) {
            values = DoubleArray(count) { i ->
                (values[(i - 1 + count) % count] + 2 * values[i] + values[(i + 1) % count]) / 4
            }
        }
        val max = values.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: 1.0
        return DoubleArray(count) { values[it] / max }
    }
}
