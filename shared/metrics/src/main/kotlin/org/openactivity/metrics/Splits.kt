// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import org.openactivity.model.Distance
import org.openactivity.model.Split

/**
 * Streaming auto splits (per kilometre or per mile).
 *
 * Feed one sample at a time with [add]; boundaries are interpolated linearly between samples, so split
 * times do not depend on the sampling rate. The live recording screen and the saved activity use this
 * same class, so their splits are identical.
 *
 * @property splitLengthMeters length of one split.
 */
public class SplitAccumulator(public val splitLengthMeters: Double) {
    init {
        require(splitLengthMeters > 0.0) { "split length must be positive" }
    }

    private val completed = ArrayList<Split>()
    private var hasSample = false
    private var lastTime = 0.0
    private var lastDistance = 0.0
    private var lastMoving = 0.0
    private var lastAltitude = Double.NaN
    private var splitStartTime = 0.0
    private var splitStartDistance = 0.0
    private var splitStartMoving = 0.0
    private var splitStartAltitude = Double.NaN

    /** Splits completed so far. */
    public val splits: List<Split> get() = completed.toList()

    /**
     * Adds a sample.
     *
     * @param time elapsed seconds since the start.
     * @param distance cumulative metres.
     * @param movingSeconds cumulative moving seconds at this sample.
     * @param altitude metres, or `NaN` when unknown.
     * @return the splits completed by this sample (often empty).
     */
    public fun add(time: Double, distance: Double, movingSeconds: Double, altitude: Double = Double.NaN): List<Split> {
        if (!hasSample) {
            hasSample = true
            splitStartTime = time
            splitStartDistance = distance
            splitStartMoving = movingSeconds
            splitStartAltitude = altitude
            remember(time, distance, movingSeconds, altitude)
            return emptyList()
        }
        val newSplits = ArrayList<Split>(1)
        while (distance >= splitStartDistance + splitLengthMeters && distance > lastDistance) {
            val boundary = splitStartDistance + splitLengthMeters
            val f = ((boundary - lastDistance) / (distance - lastDistance)).coerceIn(0.0, 1.0)
            val bTime = lerp(lastTime, time, f)
            val bMoving = lerp(lastMoving, movingSeconds, f)
            val bAlt = interpolateAltitude(lastAltitude, altitude, f)
            val split = makeSplit(boundary - splitStartDistance, bTime, bMoving, bAlt, partial = false)
            completed += split
            newSplits += split
            splitStartTime = bTime
            splitStartDistance = boundary
            splitStartMoving = bMoving
            splitStartAltitude = bAlt
        }
        remember(time, distance, movingSeconds, altitude)
        return newSplits
    }

    /** All completed splits plus the final partial split (when it covers any distance). */
    public fun finish(): List<Split> {
        val partialDistance = lastDistance - splitStartDistance
        if (!hasSample || partialDistance <= 1e-9) return splits
        return splits + makeSplit(partialDistance, lastTime, lastMoving, lastAltitude, partial = true)
    }

    /** Captures the full state (for crash-recovery snapshots). */
    public fun snapshot(): State = State(
        completed.toList(), hasSample, lastTime, lastDistance, lastMoving, lastAltitude,
        splitStartTime, splitStartDistance, splitStartMoving, splitStartAltitude,
    )

    /** Restores a state captured by [snapshot]. */
    public fun restore(state: State) {
        completed.clear()
        completed += state.completed
        hasSample = state.hasSample
        lastTime = state.lastTime
        lastDistance = state.lastDistance
        lastMoving = state.lastMoving
        lastAltitude = state.lastAltitude
        splitStartTime = state.splitStartTime
        splitStartDistance = state.splitStartDistance
        splitStartMoving = state.splitStartMoving
        splitStartAltitude = state.splitStartAltitude
    }

    private fun remember(time: Double, distance: Double, moving: Double, altitude: Double) {
        lastTime = time
        lastDistance = distance
        lastMoving = moving
        if (altitude.isFinite()) lastAltitude = altitude
    }

    private fun makeSplit(length: Double, endTime: Double, endMoving: Double, endAlt: Double, partial: Boolean) =
        Split(
            index = completed.size,
            distance = Distance(length),
            elapsedTime = secondsToDuration(endTime - splitStartTime),
            movingTime = secondsToDuration((endMoving - splitStartMoving).coerceAtLeast(0.0)),
            elevationDelta = if (endAlt.isFinite() && splitStartAltitude.isFinite()) {
                Distance(endAlt - splitStartAltitude)
            } else {
                null
            },
            isPartial = partial,
        )

    private fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f

    private fun interpolateAltitude(a: Double, b: Double, f: Double): Double = when {
        a.isFinite() && b.isFinite() -> lerp(a, b, f)
        b.isFinite() -> b
        else -> a
    }

    /** Immutable copy of the accumulator state. */
    public data class State(
        val completed: List<Split>,
        val hasSample: Boolean,
        val lastTime: Double,
        val lastDistance: Double,
        val lastMoving: Double,
        val lastAltitude: Double,
        val splitStartTime: Double,
        val splitStartDistance: Double,
        val splitStartMoving: Double,
        val splitStartAltitude: Double,
    )
}

/** Batch splits. */
public object Splits {
    /**
     * Splits of [samples] every [splitLengthMeters], with moving time taken from [movingMask]
     * (one flag per interval, see [MovingTime.movingMask]).
     */
    public fun compute(samples: ActivitySamples, splitLengthMeters: Double, movingMask: BooleanArray): List<Split> {
        require(movingMask.size == samples.size) { "mask must have one entry per sample" }
        val acc = SplitAccumulator(splitLengthMeters)
        var moving = 0.0
        for (i in 0 until samples.size) {
            if (i > 0 && movingMask[i]) moving += samples.time[i] - samples.time[i - 1]
            acc.add(samples.time[i], samples.distance[i], moving, samples.altitude?.get(i) ?: Double.NaN)
        }
        return acc.finish()
    }
}
