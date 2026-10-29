// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

/**
 * Where altitude comes from; decides the default hysteresis for gain and loss.
 *
 * Barometric altitude is precise over short spans, so 2 m of sustained change counts. GPS (or DEM)
 * altitude is noisy, so climbing must be sustained for 10 m before it counts.
 *
 * @property hysteresisMeters default hysteresis for this source.
 */
public enum class ElevationSource(public val hysteresisMeters: Double) {
    BAROMETER(2.0),
    GPS(10.0),
}

/**
 * Streaming elevation gain/loss: a trailing moving median followed by a hysteresis accumulator.
 *
 * A climb or descent only counts once the smoothed altitude has moved [hysteresisMeters] away from the
 * last reference level, which suppresses sensor noise and slow drift. Feeding samples one at a time
 * (live recording) gives exactly the same result as [ElevationMetrics.compute] on the whole series.
 *
 * @property hysteresisMeters minimum sustained change before it counts.
 * @property medianWindow number of samples in the trailing median (odd values recommended).
 */
public class ElevationAccumulator(
    public val hysteresisMeters: Double,
    public val medianWindow: Int = 5,
) {
    init {
        require(hysteresisMeters >= 0.0) { "hysteresis must not be negative" }
        require(medianWindow >= 1) { "median window must be at least 1" }
    }

    private val ring = DoubleArray(medianWindow)
    private var count = 0
    private var next = 0
    private var reference = Double.NaN

    /** Total counted climbing, metres. */
    public var gain: Double = 0.0
        private set

    /** Total counted descent, metres. */
    public var loss: Double = 0.0
        private set

    /** Latest smoothed altitude, or `NaN` before the first valid sample. */
    public var smoothed: Double = Double.NaN
        private set

    /** Adds one altitude sample; `NaN` and infinite values are ignored. */
    public fun add(altitude: Double) {
        add(altitude, hysteresisMeters)
    }

    /**
     * Adds one altitude sample using [hysteresis] for this step instead of [hysteresisMeters]; lets callers
     * widen the band when the source reports poor vertical accuracy.
     */
    public fun add(altitude: Double, hysteresis: Double) {
        if (!altitude.isFinite()) return
        ring[next] = altitude
        next = (next + 1) % medianWindow
        if (count < medianWindow) count++
        val m = median()
        smoothed = m
        if (reference.isNaN()) {
            reference = m
            return
        }
        val delta = m - reference
        if (delta >= hysteresis && delta > 0.0) {
            gain += delta
            reference = m
        } else if (-delta >= hysteresis && delta < 0.0) {
            loss += -delta
            reference = m
        }
    }

    /** Captures the accumulator's full state (for crash-recovery snapshots). */
    public fun snapshot(): State = State(ring.toList(), count, next, reference, gain, loss, smoothed)

    /** Restores a state captured by [snapshot] from an accumulator with the same configuration. */
    public fun restore(state: State) {
        require(state.ring.size == medianWindow) { "snapshot window ${state.ring.size} != $medianWindow" }
        state.ring.forEachIndexed { i, v -> ring[i] = v }
        count = state.count
        next = state.next
        reference = state.reference
        gain = state.gain
        loss = state.loss
        smoothed = state.smoothed
    }

    private fun median(): Double {
        val window = ring.copyOf(count)
        window.sort()
        return if (count % 2 == 1) window[count / 2] else (window[count / 2 - 1] + window[count / 2]) / 2
    }

    /** Immutable copy of the accumulator state. */
    public data class State(
        val ring: List<Double>,
        val count: Int,
        val next: Int,
        val reference: Double,
        val gain: Double,
        val loss: Double,
        val smoothed: Double,
    )
}

/** Gain and loss in metres. */
public data class ElevationTotals(val gain: Double, val loss: Double)

/** Batch elevation metrics. */
public object ElevationMetrics {
    /** Gain and loss of [altitude] (NaN entries skipped) with [hysteresisMeters] and a [medianWindow] median. */
    public fun compute(altitude: DoubleArray, hysteresisMeters: Double, medianWindow: Int = 5): ElevationTotals {
        val acc = ElevationAccumulator(hysteresisMeters, medianWindow)
        for (a in altitude) acc.add(a)
        return ElevationTotals(acc.gain, acc.loss)
    }

    /**
     * Altitude in metres from barometric pressure, using the international standard atmosphere.
     * Only differences are meaningful unless the result is calibrated against a known altitude.
     */
    public fun pressureToAltitude(hPa: Double, seaLevelHPa: Double = STANDARD_SEA_LEVEL_HPA): Double =
        44_330.0 * (1.0 - Math.pow(hPa / seaLevelHPa, 1.0 / 5.255))

    /** Inverse of [pressureToAltitude]. */
    public fun altitudeToPressure(meters: Double, seaLevelHPa: Double = STANDARD_SEA_LEVEL_HPA): Double =
        seaLevelHPa * Math.pow(1.0 - meters / 44_330.0, 5.255)

    /** Standard sea-level pressure in hPa. */
    public const val STANDARD_SEA_LEVEL_HPA: Double = 1013.25
}
