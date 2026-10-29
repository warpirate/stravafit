// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import kotlin.math.roundToInt

/**
 * Heart-rate zones defined by their upper bounds.
 *
 * Zone *k* (zero-based) covers heart rates above `upperBounds[k - 1]` up to and including
 * `upperBounds[k]`; the last zone has no upper bound. With 4 bounds there are 5 zones.
 *
 * @property upperBounds strictly increasing upper bounds in bpm.
 */
public data class HeartRateZones(val upperBounds: List<Int>) {
    init {
        require(upperBounds.isNotEmpty()) { "at least one bound is required" }
        require(upperBounds.zipWithNext().all { (a, b) -> a < b }) { "bounds must increase: $upperBounds" }
    }

    /** Number of zones. */
    val count: Int get() = upperBounds.size + 1

    /** Zero-based zone for [bpm]. */
    public fun zoneOf(bpm: Double): Int {
        for ((k, bound) in upperBounds.withIndex()) if (bpm <= bound) return k
        return upperBounds.size
    }

    public companion object {
        /** Default fractions of maximum heart rate at the top of zones 1–4. */
        public val DEFAULT_FRACTIONS: List<Double> = listOf(0.65, 0.81, 0.89, 0.97)

        /** Five zones as percentages of [maxHr] (Z1 < 65 %, Z2 65–81 %, Z3 81–89 %, Z4 89–97 %, Z5 > 97 %). */
        public fun fromMaxHeartRate(maxHr: Int): HeartRateZones {
            require(maxHr in 100..250) { "implausible max heart rate: $maxHr" }
            return HeartRateZones(DEFAULT_FRACTIONS.map { (it * maxHr).roundToInt() })
        }
    }
}

/** Time spent in each heart-rate zone. */
public object TimeInZones {
    /** Heart rates outside this range are treated as sensor errors and skipped. */
    public val PLAUSIBLE_BPM: ClosedFloatingPointRange<Double> = 30.0..230.0

    /**
     * Seconds per zone. Each interval is attributed to the heart rate at its start (sample and hold).
     * Intervals that are excluded by [includeMask], longer than [maxGapSeconds] (a sensor dropout) or start
     * with an implausible value are skipped.
     */
    public fun compute(
        samples: ActivitySamples,
        zones: HeartRateZones,
        includeMask: BooleanArray? = null,
        maxGapSeconds: Double = 30.0,
    ): DoubleArray {
        val out = DoubleArray(zones.count)
        val hr = samples.heartRate ?: return out
        for (i in 1 until samples.size) {
            if (includeMask != null && !includeMask[i]) continue
            val dt = samples.time[i] - samples.time[i - 1]
            if (dt <= 0.0 || dt > maxGapSeconds) continue
            val bpm = hr[i - 1]
            if (!bpm.isFinite() || bpm !in PLAUSIBLE_BPM) continue
            out[zones.zoneOf(bpm)] += dt
        }
        return out
    }
}

/** Averages and maxima of sensor series. */
public object SeriesStats {
    /**
     * Time-weighted average of [values] (sample and hold), over intervals included by [includeMask] and no
     * longer than [maxGapSeconds]; null when nothing qualifies. `NaN` values are skipped.
     */
    public fun timeWeightedAverage(
        time: DoubleArray,
        values: DoubleArray,
        includeMask: BooleanArray? = null,
        maxGapSeconds: Double = 30.0,
    ): Double? {
        require(values.size == time.size) { "values must match time" }
        var sum = 0.0
        var weight = 0.0
        for (i in 1 until time.size) {
            if (includeMask != null && !includeMask[i]) continue
            val dt = time[i] - time[i - 1]
            val v = values[i - 1]
            if (dt <= 0.0 || dt > maxGapSeconds || !v.isFinite()) continue
            sum += v * dt
            weight += dt
        }
        return if (weight > 0.0) sum / weight else null
    }

    /** Largest finite value, or null when there is none. */
    public fun max(values: DoubleArray): Double? {
        var m = Double.NEGATIVE_INFINITY
        for (v in values) if (v.isFinite() && v > m) m = v
        return if (m == Double.NEGATIVE_INFINITY) null else m
    }
}
