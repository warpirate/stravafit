// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

/**
 * Speed smoothing, stationary detection and auto-pause decisions, driven by accepted fixes.
 *
 * - The smoothed speed is an exponential moving average (α = [EMA_ALPHA]) of the per-fix speed.
 * - "Stationary" needs [STATIONARY_ENTER_FIXES] consecutive fixes under the enter threshold and
 *   [STATIONARY_EXIT_FIXES] over the exit threshold to end. While stationary, position jitter adds no
 *   distance.
 * - Auto-pause compares the smoothed speed to [AutoPauseRule] thresholds for a sustained time. Because
 *   the smoothed speed lags, each transition is back-dated to the moment the raw speed crossed the
 *   threshold, so stops are not partly counted as moving time (and vice versa).
 */
internal class MotionDetector(private val profile: SportProfile) {
    var smoothedSpeed: Double = 0.0
        private set
    var isStationary: Boolean = false
        private set
    private var hasSpeed = false
    private var slowFixes = 0
    private var fastFixes = 0
    private var belowSince: Long? = null
    private var aboveSince: Long? = null
    private var rawBelowSince: Long? = null
    private var rawAboveSince: Long? = null

    /** Feeds the per-fix [speed] (m/s) measured at [nanos]. */
    fun onSpeed(speed: Double, nanos: Long) {
        val s = if (speed.isFinite() && speed >= 0.0) speed else 0.0
        profile.autoPause?.let { rule ->
            rawBelowSince = if (s < rule.pauseBelowMps) rawBelowSince ?: nanos else null
            rawAboveSince = if (s > rule.resumeAboveMps) rawAboveSince ?: nanos else null
        }
        smoothedSpeed = if (hasSpeed) smoothedSpeed + EMA_ALPHA * (s - smoothedSpeed) else s
        hasSpeed = true
        if (isStationary) {
            fastFixes = if (s > profile.stationaryExitMps) fastFixes + 1 else 0
            if (fastFixes >= STATIONARY_EXIT_FIXES) {
                isStationary = false
                fastFixes = 0
            }
        } else {
            slowFixes = if (s < profile.stationaryEnterMps) slowFixes + 1 else 0
            if (slowFixes >= STATIONARY_ENTER_FIXES) {
                isStationary = true
                slowFixes = 0
            }
        }
    }

    /**
     * Auto-pause decision at time [nanos] given whether the activity is currently auto-paused.
     * [resumeAllowed] is false while GPS accuracy is too poor to trust movement.
     */
    fun autoPauseDecision(nanos: Long, autoPaused: Boolean, resumeAllowed: Boolean): AutoPauseEvent {
        val rule = profile.autoPause ?: return AutoPauseEvent.NONE
        if (!autoPaused) {
            aboveSince = null
            if (smoothedSpeed < rule.pauseBelowMps) {
                val since = belowSince ?: nanos.also { belowSince = it }
                if ((nanos - since) / 1e9 >= rule.pauseAfterSeconds) {
                    belowSince = null
                    return AutoPauseEvent(AutoPauseDecision.PAUSE, minOf(since, rawBelowSince ?: since))
                }
            } else {
                belowSince = null
            }
        } else {
            belowSince = null
            if (resumeAllowed && smoothedSpeed > rule.resumeAboveMps) {
                val since = aboveSince ?: nanos.also { aboveSince = it }
                if ((nanos - since) / 1e9 >= rule.resumeAfterSeconds) {
                    aboveSince = null
                    return AutoPauseEvent(AutoPauseDecision.RESUME, minOf(since, rawAboveSince ?: since))
                }
            } else {
                aboveSince = null
            }
        }
        return AutoPauseEvent.NONE
    }

    /** Forgets speed history (after a manual resume or an interruption). */
    fun reset() {
        smoothedSpeed = 0.0
        hasSpeed = false
        isStationary = false
        slowFixes = 0
        fastFixes = 0
        belowSince = null
        aboveSince = null
        rawBelowSince = null
        rawAboveSince = null
    }

    fun snapshot(): MotionDetectorState =
        MotionDetectorState(
            smoothedSpeed, isStationary, hasSpeed, slowFixes, fastFixes, belowSince, aboveSince, rawBelowSince, rawAboveSince,
        )

    fun restore(s: MotionDetectorState) {
        smoothedSpeed = s.smoothedSpeed
        isStationary = s.isStationary
        hasSpeed = s.hasSpeed
        slowFixes = s.slowFixes
        fastFixes = s.fastFixes
        belowSince = s.belowSince
        aboveSince = s.aboveSince
        rawBelowSince = s.rawBelowSince
        rawAboveSince = s.rawAboveSince
    }

    private companion object {
        const val EMA_ALPHA = 0.3
        const val STATIONARY_ENTER_FIXES = 3
        const val STATIONARY_EXIT_FIXES = 2
    }
}

internal enum class AutoPauseDecision { NONE, PAUSE, RESUME }

/** An auto-pause decision and the (back-dated) moment it takes effect. */
internal data class AutoPauseEvent(val decision: AutoPauseDecision, val effectiveNanos: Long) {
    companion object {
        val NONE = AutoPauseEvent(AutoPauseDecision.NONE, 0L)
    }
}

/** Serializable state of the motion detector (part of [EngineSnapshot]). */
public data class MotionDetectorState(
    val smoothedSpeed: Double,
    val isStationary: Boolean,
    val hasSpeed: Boolean,
    val slowFixes: Int,
    val fastFixes: Int,
    val belowSince: Long?,
    val aboveSince: Long?,
    val rawBelowSince: Long?,
    val rawAboveSince: Long?,
)
