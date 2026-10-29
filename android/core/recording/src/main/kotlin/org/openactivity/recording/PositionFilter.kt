// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.geo.LocalTangentPlane
import org.openactivity.model.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Light position smoothing: a constant-velocity Kalman filter in a local east/north plane.
 *
 * Both axes share the same measurement variance (reported accuracy squared) and process noise, so one 2×2
 * covariance serves both. Measurements whose innovation is far outside the predicted uncertainty (multipath
 * jumps) are rejected by a chi-square gate; after [MAX_GATED_IN_A_ROW] rejections in a row the filter
 * accepts that the athlete really is elsewhere and restarts from the measurement.
 *
 * The plane is re-centred whenever the estimate drifts more than [RECENTER_METERS] from its origin, which
 * keeps the flat-plane approximation accurate on long activities and across the antimeridian.
 */
internal class PositionFilter(private val processNoiseAccel: Double) {
    private var plane: LocalTangentPlane? = null
    private var e = 0.0
    private var n = 0.0
    private var ve = 0.0
    private var vn = 0.0
    private var p00 = 0.0
    private var p01 = 0.0
    private var p11 = 0.0
    private var lastNanos = 0L
    private var gatedInARow = 0

    val isInitialized: Boolean get() = plane != null

    /** Current smoothed position. Only valid when [isInitialized]. */
    val position: LatLng get() = requireNotNull(plane).toLatLng(e, n)

    /** Speed of the current velocity estimate, m/s. */
    val speed: Double get() = hypot(ve, vn)

    /** Restarts the filter at [position]. A known [speedMps] and [bearingDeg] seed the velocity. */
    fun reset(position: LatLng, nanos: Long, accuracy: Double, speedMps: Double?, bearingDeg: Double?) {
        plane = LocalTangentPlane(position)
        e = 0.0
        n = 0.0
        if (speedMps != null && bearingDeg != null && speedMps.isFinite() && bearingDeg.isFinite()) {
            val b = bearingDeg * PI / 180.0
            ve = speedMps * sin(b)
            vn = speedMps * cos(b)
            p11 = VELOCITY_VAR_KNOWN
        } else {
            ve = 0.0
            vn = 0.0
            p11 = VELOCITY_VAR_UNKNOWN
        }
        p00 = accuracy * accuracy
        p01 = 0.0
        lastNanos = nanos
        gatedInARow = 0
    }

    /**
     * Predicts to [nanos] and fuses a position measurement with standard deviation [accuracy]. When the
     * receiver also reports [speedMps] and [bearingDeg] (Doppler velocity, far less noisy than position
     * differences), the velocity is fused too; this is what keeps position noise from inflating distance.
     *
     * @return false when the measurement was gated out as an outlier (state only predicted).
     */
    fun update(position: LatLng, nanos: Long, accuracy: Double, speedMps: Double?, bearingDeg: Double?): Boolean {
        val pl = plane
        if (pl == null) {
            reset(position, nanos, accuracy, speedMps, bearingDeg)
            return true
        }
        val dt = (nanos - lastNanos) / 1e9
        if (dt > 0.0) predict(dt)
        lastNanos = nanos
        val ie = pl.east(position) - e
        val iN = pl.north(position) - n
        val rp = accuracy * accuracy
        if ((ie * ie + iN * iN) / (p00 + rp) > GATE_CHI2) {
            gatedInARow++
            if (gatedInARow >= MAX_GATED_IN_A_ROW) {
                reset(position, nanos, accuracy, speedMps, bearingDeg)
                return true
            }
            return false
        }
        gatedInARow = 0
        if (speedMps != null && bearingDeg != null && speedMps.isFinite() && bearingDeg.isFinite()) {
            val b = bearingDeg * PI / 180.0
            val rv = VELOCITY_NOISE_BASE * VELOCITY_NOISE_BASE +
                (VELOCITY_NOISE_PER_MPS * speedMps) * (VELOCITY_NOISE_PER_MPS * speedMps)
            fuseWithVelocity(ie, iN, speedMps * sin(b) - ve, speedMps * cos(b) - vn, rp, rv)
        } else {
            fusePositionOnly(ie, iN, rp)
        }
        if (abs(e) > RECENTER_METERS || abs(n) > RECENTER_METERS) recenter()
        return true
    }

    private fun fusePositionOnly(ie: Double, iN: Double, rp: Double) {
        val s = p00 + rp
        val k0 = p00 / s
        val k1 = p01 / s
        e += k0 * ie
        n += k0 * iN
        ve += k1 * ie
        vn += k1 * iN
        val newP00 = (1 - k0) * p00
        val newP01 = (1 - k0) * p01
        val newP11 = p11 - k1 * p01
        p00 = newP00
        p01 = newP01
        p11 = newP11
    }

    /** Joint update with position innovations (ie, iN) and velocity innovations (je, jN). */
    private fun fuseWithVelocity(ie: Double, iN: Double, je: Double, jN: Double, rp: Double, rv: Double) {
        val s00 = p00 + rp
        val s11 = p11 + rv
        val det = s00 * s11 - p01 * p01
        val k00 = (p00 * s11 - p01 * p01) / det
        val k01 = p01 * rp / det
        val k10 = p01 * rv / det
        val k11 = (p11 * s00 - p01 * p01) / det
        e += k00 * ie + k01 * je
        n += k00 * iN + k01 * jN
        ve += k10 * ie + k11 * je
        vn += k10 * iN + k11 * jN
        val newP00 = (1 - k00) * p00 - k01 * p01
        val newP01 = (1 - k00) * p01 - k01 * p11
        val newP11 = -k10 * p01 + (1 - k11) * p11
        p00 = newP00
        p01 = newP01
        p11 = newP11
    }

    private fun predict(dt: Double) {
        e += ve * dt
        n += vn * dt
        val q = processNoiseAccel * processNoiseAccel
        val dt2 = dt * dt
        p00 += 2 * dt * p01 + dt2 * p11 + q * dt2 * dt2 / 4
        p01 += dt * p11 + q * dt2 * dt / 2
        p11 += q * dt2
    }

    private fun recenter() {
        val here = position
        plane = LocalTangentPlane(here)
        e = 0.0
        n = 0.0
    }

    fun snapshot(): PositionFilterState? = plane?.let {
        PositionFilterState(it.origin, e, n, ve, vn, p00, p01, p11, lastNanos, gatedInARow)
    }

    fun restore(state: PositionFilterState?) {
        if (state == null) {
            plane = null
            return
        }
        plane = LocalTangentPlane(state.origin)
        e = state.east
        n = state.north
        ve = state.velocityEast
        vn = state.velocityNorth
        p00 = state.p00
        p01 = state.p01
        p11 = state.p11
        lastNanos = state.lastNanos
        gatedInARow = state.gatedInARow
    }

    fun clear() {
        plane = null
    }

    private companion object {
        /** Chi-square threshold for 2 degrees of freedom at roughly 1 in 3000. */
        const val GATE_CHI2 = 16.0
        const val MAX_GATED_IN_A_ROW = 4
        const val RECENTER_METERS = 5_000.0
        const val VELOCITY_VAR_KNOWN = 1.0
        const val VELOCITY_VAR_UNKNOWN = 25.0

        /** Doppler speed noise floor, m/s. */
        const val VELOCITY_NOISE_BASE = 0.3

        /** Velocity noise growing with speed (course error), per m/s. */
        const val VELOCITY_NOISE_PER_MPS = 0.05
    }
}

/** Serializable state of the position filter (part of [EngineSnapshot]). */
public data class PositionFilterState(
    val origin: LatLng,
    val east: Double,
    val north: Double,
    val velocityEast: Double,
    val velocityNorth: Double,
    val p00: Double,
    val p01: Double,
    val p11: Double,
    val lastNanos: Long,
    val gatedInARow: Int,
)
