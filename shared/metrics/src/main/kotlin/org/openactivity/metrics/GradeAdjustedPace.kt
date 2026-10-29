// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

/**
 * Energy cost of running on a slope, after Minetti et al. (2002), "Energy cost of walking and running at
 * extreme uphill and downhill slopes", J Appl Physiol 93:1039–1046.
 *
 * Uphill uses the published polynomial. Downhill is flattened, because real runners do not gain as much on
 * steep descents as the lab curve suggests: the benefit is capped at the cost for −9 %, and beyond −20 %
 * the cost ramps linearly back to the flat cost at −45 %. Grades are clamped to ±45 %, the fitted range.
 */
public object RunningCost {
    /** Steepest grade (as a fraction) the model accepts; steeper grades are clamped. */
    public const val MAX_GRADE: Double = 0.45

    private const val FLOOR_GRADE = -0.09
    private const val RAMP_START_GRADE = -0.20

    /** Cost on flat ground, J·kg⁻¹·m⁻¹. */
    public val FLAT: Double = minetti(0.0)

    /** Raw Minetti polynomial for grade [i] (fraction), J·kg⁻¹·m⁻¹. */
    public fun minetti(i: Double): Double =
        ((((155.4 * i - 30.4) * i - 43.3) * i + 46.3) * i + 19.5) * i + 3.6

    /** Cost used for grade-adjusted pace at grade [grade] (fraction, e.g. 0.05 for 5 %). */
    public fun cost(grade: Double): Double {
        val i = grade.coerceIn(-MAX_GRADE, MAX_GRADE)
        if (i >= 0.0) return minetti(i)
        val floor = minetti(FLOOR_GRADE)
        if (i >= RAMP_START_GRADE) return maxOf(minetti(i), floor)
        val f = (i - RAMP_START_GRADE) / (-MAX_GRADE - RAMP_START_GRADE)
        return floor + (FLAT - floor) * f
    }

    /** Ratio of the cost at [grade] to the flat cost: how much "harder" each metre is. */
    public fun factor(grade: Double): Double = cost(grade) / FLAT
}

/** Grade-adjusted pace: the flat-ground speed that would cost the same effort. */
public object GradeAdjustedPace {
    /** Distance window, metres, over which grade is smoothed. */
    public const val GRADE_WINDOW_M: Double = 30.0

    /** Flat-equivalent speed for running at [speedMps] on [grade]. */
    public fun adjustedSpeed(speedMps: Double, grade: Double): Double = speedMps * RunningCost.factor(grade)

    /**
     * Smoothed grade (fraction) at each sample: altitude change across a [windowMeters] distance window
     * centred on the sample, by linear interpolation. `NaN` where altitude is missing or the window has no
     * horizontal extent.
     */
    public fun smoothedGrade(distance: DoubleArray, altitude: DoubleArray, windowMeters: Double = GRADE_WINDOW_M): DoubleArray {
        val n = distance.size
        require(altitude.size == n) { "altitude must match distance" }
        val out = DoubleArray(n) { Double.NaN }
        if (n < 2) return out
        val half = windowMeters / 2
        for (i in 0 until n) {
            val from = (distance[i] - half).coerceAtLeast(distance[0])
            val to = (distance[i] + half).coerceAtMost(distance[n - 1])
            if (to - from <= 0.0) continue
            val a = altitudeAt(distance, altitude, from)
            val b = altitudeAt(distance, altitude, to)
            if (a.isFinite() && b.isFinite()) out[i] = (b - a) / (to - from)
        }
        return out
    }

    /**
     * Average grade-adjusted speed over the intervals flagged in [movingMask], or null when the samples have
     * no altitude or no moving time.
     */
    public fun averageSpeed(samples: ActivitySamples, movingMask: BooleanArray): Double? {
        val altitude = samples.altitude ?: return null
        val grade = smoothedGrade(samples.distance, altitude)
        var adjusted = 0.0
        var seconds = 0.0
        for (i in 1 until samples.size) {
            if (!movingMask[i]) continue
            val dd = samples.distance[i] - samples.distance[i - 1]
            val g = averageOf(grade[i - 1], grade[i])
            adjusted += if (g.isFinite()) dd * RunningCost.factor(g) else dd
            seconds += samples.time[i] - samples.time[i - 1]
        }
        return if (seconds > 0.0) adjusted / seconds else null
    }

    private fun averageOf(a: Double, b: Double): Double = when {
        a.isFinite() && b.isFinite() -> (a + b) / 2
        a.isFinite() -> a
        else -> b
    }

    /** Altitude at cumulative distance [x] by linear interpolation (binary search, O(log n)). */
    private fun altitudeAt(distance: DoubleArray, altitude: DoubleArray, x: Double): Double {
        var lo = 0
        var hi = distance.size - 1
        // Largest index whose distance is <= x.
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (distance[mid] <= x) lo = mid else hi = mid - 1
        }
        if (lo == distance.size - 1) return altitude[lo]
        val span = distance[lo + 1] - distance[lo]
        if (span <= 0.0) return altitude[lo]
        val f = ((x - distance[lo]) / span).coerceIn(0.0, 1.0)
        return altitude[lo] + (altitude[lo + 1] - altitude[lo]) * f
    }
}
