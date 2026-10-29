// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The per-sample data of one activity, as parallel arrays that share an index.
 *
 * Interval *i* is the span from sample *i − 1* to sample *i* (for *i* ≥ 1). Sampling may be irregular
 * (1 Hz phones, "smart recording" watches with multi-second gaps); every algorithm here handles that.
 *
 * @property time seconds since the activity start; must be non-decreasing.
 * @property distance cumulative metres; must be non-decreasing.
 * @property altitude metres above sea level, `NaN` where unknown; null when the activity has none.
 * @property heartRate beats per minute, `NaN` where unknown; null when absent.
 * @property power watts, `NaN` where unknown; null when absent.
 * @property cadence rpm (or steps per minute of one foot), `NaN` where unknown; null when absent.
 * @property paused `paused[i]` is true when interval *i* lies inside a pause (manual, auto or recording
 *   gap) and must not count as moving; null when there were no pauses.
 */
public class ActivitySamples(
    public val time: DoubleArray,
    public val distance: DoubleArray,
    public val altitude: DoubleArray? = null,
    public val heartRate: DoubleArray? = null,
    public val power: DoubleArray? = null,
    public val cadence: DoubleArray? = null,
    public val paused: BooleanArray? = null,
) {
    /** Number of samples. */
    public val size: Int get() = time.size

    init {
        val n = time.size
        require(distance.size == n) { "distance has ${distance.size} samples, time has $n" }
        listOf(altitude, heartRate, power, cadence).forEach { s ->
            require(s == null || s.size == n) { "every stream must have $n samples" }
        }
        require(paused == null || paused.size == n) { "paused must have $n entries" }
        for (i in 1 until n) {
            require(time[i] >= time[i - 1]) { "time decreases at sample $i" }
            require(distance[i] >= distance[i - 1]) { "distance decreases at sample $i" }
        }
    }

    /** True when interval [i] is inside a pause. */
    public fun isPaused(i: Int): Boolean = paused?.get(i) == true

    /** Total elapsed seconds from the first to the last sample. */
    public val elapsedSeconds: Double get() = if (size == 0) 0.0 else time[size - 1] - time[0]

    /** Total distance in metres. */
    public val totalDistance: Double get() = if (size == 0) 0.0 else distance[size - 1] - distance[0]
}

/** Converts fractional seconds to a [Duration] rounded to the nearest nanosecond. */
public fun secondsToDuration(seconds: Double): Duration = (seconds * 1e9).roundToLong().nanoseconds

/** Converts a [Duration] to fractional seconds. */
public fun Duration.toSecondsDouble(): Double = inWholeNanoseconds / 1e9
