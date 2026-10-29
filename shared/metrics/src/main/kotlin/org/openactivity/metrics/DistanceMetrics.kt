// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng

/** Distance along a track. */
public object DistanceMetrics {
    /** Cumulative great-circle distance in metres at each point of [points] (first value is 0). */
    public fun cumulative(points: List<LatLng>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + Spherical.distance(points[i - 1], points[i])
        return out
    }

    /** Total great-circle length of [points] in metres. */
    public fun total(points: List<LatLng>): Double = Spherical.pathLength(points)
}

/** Pace and speed conversions. Pace is undefined (null) when not moving. */
public object SpeedMath {
    /** Average speed in m/s for [meters] over [seconds]; 0 when [seconds] is not positive. */
    public fun averageSpeed(meters: Double, seconds: Double): Double = if (seconds > 0.0) meters / seconds else 0.0

    /** Seconds needed per [unitMeters] at [speedMps]; null when the speed is not positive and finite. */
    public fun paceSeconds(speedMps: Double, unitMeters: Double = 1000.0): Double? =
        if (speedMps > 0.0 && speedMps.isFinite()) unitMeters / speedMps else null

    /** Speed in m/s for a pace of [paceSeconds] per [unitMeters]. */
    public fun speedFromPace(paceSeconds: Double, unitMeters: Double = 1000.0): Double {
        require(paceSeconds > 0.0) { "pace must be positive: $paceSeconds" }
        return unitMeters / paceSeconds
    }
}
