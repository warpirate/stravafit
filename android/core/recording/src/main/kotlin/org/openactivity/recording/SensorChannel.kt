// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

/**
 * Latest value, time-weighted average and maximum of one sensor stream (heart rate, power or cadence).
 *
 * A reading is "fresh" for [FRESH_NANOS]; stale readings are neither shown nor averaged, so a strap that
 * drops out does not keep contributing its last value.
 */
internal class SensorChannel {
    private var latest: Double = Double.NaN
    private var latestNanos: Long = Long.MIN_VALUE
    private var weightedSum = 0.0
    private var weightNanos = 0L
    var max: Double = Double.NaN
        private set

    fun onReading(value: Double, nanos: Long, recording: Boolean) {
        if (!value.isFinite()) return
        latest = value
        latestNanos = nanos
        if (recording && (max.isNaN() || value > max)) max = value
    }

    /** Latest value if it is fresh at [nanos]. */
    fun current(nanos: Long): Double? = if (isFresh(nanos)) latest else null

    /** Integrates the current value over a recording interval ending at [nanos] of length [dtNanos]. */
    fun accumulate(nanos: Long, dtNanos: Long) {
        if (dtNanos > 0 && isFresh(nanos)) {
            weightedSum += latest * dtNanos
            weightNanos += dtNanos
        }
    }

    /** Time-weighted average while recording, or null when never fresh. */
    val average: Double? get() = if (weightNanos > 0) weightedSum / weightNanos else null

    private fun isFresh(nanos: Long) = latestNanos != Long.MIN_VALUE && nanos - latestNanos <= FRESH_NANOS

    fun snapshot(): SensorChannelState = SensorChannelState(latest, latestNanos, weightedSum, weightNanos, max)

    fun restore(s: SensorChannelState) {
        latest = s.latest
        latestNanos = s.latestNanos
        weightedSum = s.weightedSum
        weightNanos = s.weightNanos
        max = s.max
    }

    private companion object {
        const val FRESH_NANOS = 5_000_000_000L
    }
}

/** Serializable state of a sensor channel (part of [EngineSnapshot]). */
public data class SensorChannelState(
    val latest: Double,
    val latestNanos: Long,
    val weightedSum: Double,
    val weightNanos: Long,
    val max: Double,
)
