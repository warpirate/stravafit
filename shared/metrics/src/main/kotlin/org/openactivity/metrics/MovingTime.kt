// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import org.openactivity.model.SportCategory
import org.openactivity.model.SportType

/**
 * Rule that decides whether an interval counts as moving.
 *
 * @property thresholdMps an interval is moving when its smoothed speed is at least this.
 * @property gapCapSeconds intervals longer than this (sparse "smart" recording, GPS dropouts) are judged on
 *   their own implied speed instead of the smoothed speed, so a long gap is neither assumed moving nor
 *   assumed stopped.
 * @property smoothingWindowSeconds width of the centred window used to smooth speed.
 */
public data class MovingTimeRule(
    val thresholdMps: Double,
    val gapCapSeconds: Double,
    val smoothingWindowSeconds: Double = 5.0,
) {
    init {
        require(thresholdMps >= 0.0) { "threshold must not be negative" }
        require(gapCapSeconds > 0.0) { "gap cap must be positive" }
        require(smoothingWindowSeconds >= 0.0) { "smoothing window must not be negative" }
    }

    public companion object {
        /**
         * Default rule for [sport]: runs 0.89 m/s (about 30 min/mile), walks and hikes 0.3 m/s,
         * rides 1.0 m/s, snow sports 0.8 m/s, others 0.5 m/s.
         */
        public fun forSport(sport: SportType): MovingTimeRule = when {
            sport.isRunLike -> MovingTimeRule(0.89, 30.0)
            sport.category == SportCategory.FOOT -> MovingTimeRule(0.3, 60.0)
            sport.category == SportCategory.BIKE -> MovingTimeRule(1.0, 30.0)
            sport.category == SportCategory.SNOW -> MovingTimeRule(0.8, 30.0)
            else -> MovingTimeRule(0.5, 30.0)
        }
    }
}

/** Moving-time classification from a distance stream. The same code runs on the phone and the server. */
public object MovingTime {
    /** Per-interval moving flags: element *i* describes interval *i* (element 0 is always false). */
    public fun movingMask(samples: ActivitySamples, rule: MovingTimeRule): BooleanArray {
        val n = samples.size
        val t = samples.time
        val d = samples.distance
        val mask = BooleanArray(n)
        val half = rule.smoothingWindowSeconds / 2
        var lo = 0
        var hi = 0
        for (i in 1 until n) {
            val dt = t[i] - t[i - 1]
            if (dt <= 0.0 || samples.isPaused(i)) continue
            if (dt > rule.gapCapSeconds) {
                mask[i] = (d[i] - d[i - 1]) / dt >= rule.thresholdMps
                continue
            }
            val mid = (t[i - 1] + t[i]) / 2
            while (lo < i - 1 && t[lo] < mid - half) lo++
            if (hi < i) hi = i
            while (hi + 1 < n && t[hi + 1] <= mid + half && t[hi + 1] - t[hi] <= rule.gapCapSeconds) hi++
            var start = lo
            // Do not smooth across a long gap before this interval.
            while (start < i - 1 && t[start + 1] - t[start] > rule.gapCapSeconds) start++
            val span = t[hi] - t[start]
            val speed = if (span > 0.0) (d[hi] - d[start]) / span else 0.0
            mask[i] = speed >= rule.thresholdMps
        }
        return mask
    }

    /** Total moving seconds of [samples] under [rule]. */
    public fun movingSeconds(samples: ActivitySamples, rule: MovingTimeRule): Double =
        sumIntervals(samples, movingMask(samples, rule))

    /** Total seconds of the intervals whose [mask] entry is true. */
    public fun sumIntervals(samples: ActivitySamples, mask: BooleanArray): Double {
        var total = 0.0
        for (i in 1 until samples.size) if (mask[i]) total += samples.time[i] - samples.time[i - 1]
        return total
    }

    /** Timer time: every interval that is not paused. */
    public fun timerMask(samples: ActivitySamples): BooleanArray =
        BooleanArray(samples.size) { i -> i > 0 && !samples.isPaused(i) }
}
