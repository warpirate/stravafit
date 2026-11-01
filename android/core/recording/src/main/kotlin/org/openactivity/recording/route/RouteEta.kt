// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.route

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Time still needed to finish a route at the athlete's average moving pace so far. */
public object RouteEta {
    /** Below this much moving time or distance the average pace is not meaningful yet. */
    public const val MIN_MOVING_SECONDS: Double = 60.0
    public const val MIN_DISTANCE_METERS: Double = 200.0

    /**
     * Time to cover [remainingMeters] at [distanceMeters] over [movingTime], or null while there is too
     * little data (the first minute, or the first 200 m) or nothing left to go.
     */
    public fun remaining(remainingMeters: Double, distanceMeters: Double, movingTime: Duration): Duration? {
        val moving = movingTime.inWholeMilliseconds / 1000.0
        if (moving < MIN_MOVING_SECONDS || distanceMeters < MIN_DISTANCE_METERS) return null
        if (!(remainingMeters > 0.0) || !remainingMeters.isFinite()) return null
        val speed = distanceMeters / moving
        if (!(speed > 0.0) || !speed.isFinite()) return null
        return (remainingMeters / speed).seconds
    }
}
