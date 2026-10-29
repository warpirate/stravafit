// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import org.openactivity.model.UnitConstants.METERS_PER_MILE

/**
 * The fastest window of an activity for a target distance.
 *
 * @property targetMeters the target distance.
 * @property elapsedSeconds time taken (elapsed time inside the window, pauses included).
 * @property startSeconds activity time at which the window starts.
 * @property startDistance cumulative distance at which the window starts.
 */
public data class DistanceEffort(
    val targetMeters: Double,
    val elapsedSeconds: Double,
    val startSeconds: Double,
    val startDistance: Double,
)

/**
 * The longest distance covered within a target duration.
 *
 * @property durationSeconds the target duration.
 * @property distanceMeters distance covered.
 * @property startSeconds activity time at which the window starts.
 */
public data class DurationEffort(
    val durationSeconds: Double,
    val distanceMeters: Double,
    val startSeconds: Double,
)

/** Standard best-effort targets. */
public object StandardEfforts {
    /** Run distances: 400 m, 1/2 mile, 1 km, 1 mile, 2 mile, 5 km, 10 km, 15 km, 10 mile, 20 km, half, 30 km, marathon, 50 km. */
    public val RUN_DISTANCES_M: List<Double> = listOf(
        400.0, METERS_PER_MILE / 2, 1000.0, METERS_PER_MILE, 2 * METERS_PER_MILE, 5000.0, 10_000.0, 15_000.0,
        10 * METERS_PER_MILE, 20_000.0, 21_097.5, 30_000.0, 42_195.0, 50_000.0,
    )

    /** Ride distances: 5, 10, 20, 30, 40, 50, 80, 100 km and 100 miles. */
    public val RIDE_DISTANCES_M: List<Double> = listOf(
        5_000.0, 10_000.0, 20_000.0, 30_000.0, 40_000.0, 50_000.0, 80_000.0, 100_000.0, 100 * METERS_PER_MILE,
    )

    /** Ride durations for "furthest in": 1, 5, 10, 20, 30, 60, 120 minutes. */
    public val RIDE_DURATIONS_S: List<Double> = listOf(60.0, 300.0, 600.0, 1200.0, 1800.0, 3600.0, 7200.0)
}

/**
 * Best efforts by exact sliding window.
 *
 * Distance against time is treated as piecewise linear between samples, so the optimal window always
 * starts or ends on a sample. Two two-pointer passes (windows starting on a sample, windows ending on a
 * sample) therefore find the exact optimum in O(n) per target.
 *
 * Optionally, intervals faster than [maxSpeedMps] (GPS jumps, a stretch in a vehicle) split the activity:
 * no effort may span them, which prevents fake records.
 */
public object BestEfforts {
    /** Fastest time for each target in [targetsMeters] that the activity covers; others are omitted. */
    public fun forDistances(
        samples: ActivitySamples,
        targetsMeters: List<Double>,
        maxSpeedMps: Double = Double.POSITIVE_INFINITY,
    ): List<DistanceEffort> = targetsMeters.mapNotNull { forDistance(samples, it, maxSpeedMps) }

    /** Fastest time to cover [targetMeters], or null when no valid window is that long. */
    public fun forDistance(
        samples: ActivitySamples,
        targetMeters: Double,
        maxSpeedMps: Double = Double.POSITIVE_INFINITY,
    ): DistanceEffort? {
        require(targetMeters > 0.0) { "target must be positive" }
        var best: DistanceEffort? = null
        for (run in validRuns(samples, maxSpeedMps)) {
            val e = distanceInRange(samples.time, samples.distance, run.first, run.last, targetMeters)
            if (e != null && (best == null || e.elapsedSeconds < best.elapsedSeconds)) best = e
        }
        return best
    }

    /** Furthest distance covered within [durationSeconds], or null when the activity is shorter. */
    public fun forDuration(
        samples: ActivitySamples,
        durationSeconds: Double,
        maxSpeedMps: Double = Double.POSITIVE_INFINITY,
    ): DurationEffort? {
        require(durationSeconds > 0.0) { "duration must be positive" }
        var best: DurationEffort? = null
        for (run in validRuns(samples, maxSpeedMps)) {
            val e = durationInRange(samples.time, samples.distance, run.first, run.last, durationSeconds)
            if (e != null && (best == null || e.distanceMeters > best.distanceMeters)) best = e
        }
        return best
    }

    /** Highest average speed over any window of [windowSeconds] (a robust "max speed"); 0 if too short. */
    public fun maxAverageSpeed(
        samples: ActivitySamples,
        windowSeconds: Double,
        maxSpeedMps: Double = Double.POSITIVE_INFINITY,
    ): Double = forDuration(samples, windowSeconds, maxSpeedMps)?.let { it.distanceMeters / windowSeconds } ?: 0.0

    /** Maximal index ranges whose intervals are all at or below [maxSpeedMps]. */
    private fun validRuns(samples: ActivitySamples, maxSpeedMps: Double): List<IntRange> {
        val n = samples.size
        if (n < 2) return emptyList()
        if (maxSpeedMps == Double.POSITIVE_INFINITY) return listOf(0 until n)
        val runs = ArrayList<IntRange>()
        var start = 0
        for (i in 1 until n) {
            val dt = samples.time[i] - samples.time[i - 1]
            val dd = samples.distance[i] - samples.distance[i - 1]
            val tooFast = if (dt > 0.0) dd / dt > maxSpeedMps else dd > 0.0
            if (tooFast) {
                if (i - 1 > start) runs += start..(i - 1)
                start = i
            }
        }
        if (n - 1 > start) runs += start until n
        return runs
    }

    private fun distanceInRange(t: DoubleArray, d: DoubleArray, lo: Int, hi: Int, target: Double): DistanceEffort? {
        if (d[hi] - d[lo] < target) return null
        var bestTime = Double.POSITIVE_INFINITY
        var bestStart = 0.0
        var bestStartDist = 0.0
        // Windows starting on sample i.
        var j = lo
        for (i in lo..hi) {
            val goal = d[i] + target
            if (d[hi] < goal) break
            while (d[j] < goal) j++
            val end = interpolate(t, d, j, goal)
            if (end - t[i] < bestTime) {
                bestTime = end - t[i]
                bestStart = t[i]
                bestStartDist = d[i]
            }
        }
        // Windows ending on sample k; the start is the latest moment at distance d[k] - target.
        var i = lo
        for (k in lo..hi) {
            val startDist = d[k] - target
            if (startDist < d[lo]) continue
            while (i + 1 <= hi && d[i + 1] <= startDist) i++
            val start = if (d[i] == startDist || i == hi) t[i] else {
                t[i] + (startDist - d[i]) / (d[i + 1] - d[i]) * (t[i + 1] - t[i])
            }
            if (t[k] - start < bestTime) {
                bestTime = t[k] - start
                bestStart = start
                bestStartDist = startDist
            }
        }
        return DistanceEffort(target, bestTime, bestStart, bestStartDist)
    }

    /** Time at which distance first reaches [goal], where d[j] >= goal and (j == 0 or d[j-1] < goal). */
    private fun interpolate(t: DoubleArray, d: DoubleArray, j: Int, goal: Double): Double {
        if (j == 0 || d[j] == goal) return t[j]
        val f = (goal - d[j - 1]) / (d[j] - d[j - 1])
        return t[j - 1] + f * (t[j] - t[j - 1])
    }

    private fun durationInRange(t: DoubleArray, d: DoubleArray, lo: Int, hi: Int, duration: Double): DurationEffort? {
        if (t[hi] - t[lo] < duration) return null
        var bestDist = Double.NEGATIVE_INFINITY
        var bestStart = 0.0
        var j = lo
        for (i in lo..hi) {
            val endTime = t[i] + duration
            if (t[hi] < endTime) break
            while (t[j] < endTime) j++
            val dist = distanceAt(t, d, j, endTime) - d[i]
            if (dist > bestDist) {
                bestDist = dist
                bestStart = t[i]
            }
        }
        var i = lo
        for (k in lo..hi) {
            val startTime = t[k] - duration
            if (startTime < t[lo]) continue
            while (i + 1 <= hi && t[i + 1] <= startTime) i++
            val startDist = if (t[i] == startTime || i == hi) d[i] else {
                d[i] + (startTime - t[i]) / (t[i + 1] - t[i]) * (d[i + 1] - d[i])
            }
            val dist = d[k] - startDist
            if (dist > bestDist) {
                bestDist = dist
                bestStart = startTime
            }
        }
        return DurationEffort(duration, bestDist, bestStart)
    }

    private fun distanceAt(t: DoubleArray, d: DoubleArray, j: Int, time: Double): Double {
        if (j == 0 || t[j] == time) return d[j]
        val f = (time - t[j - 1]) / (t[j] - t[j - 1])
        return d[j - 1] + f * (d[j] - d[j - 1])
    }
}
