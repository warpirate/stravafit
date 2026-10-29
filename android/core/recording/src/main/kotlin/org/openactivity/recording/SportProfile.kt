// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.metrics.MovingTimeRule
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem

/**
 * Auto-pause thresholds with hysteresis: pausing needs a sustained slow speed, resuming a sustained faster
 * one, so the state does not flicker around a single threshold.
 *
 * @property pauseBelowMps smoothed speed under which the athlete counts as stopped.
 * @property pauseAfterSeconds how long the speed must stay under [pauseBelowMps] before pausing.
 * @property resumeAboveMps smoothed speed over which the athlete counts as moving again.
 * @property resumeAfterSeconds how long the speed must stay over [resumeAboveMps] before resuming.
 */
public data class AutoPauseRule(
    val pauseBelowMps: Double,
    val pauseAfterSeconds: Double,
    val resumeAboveMps: Double,
    val resumeAfterSeconds: Double,
) {
    init {
        require(resumeAboveMps >= pauseBelowMps) { "resume threshold must not be below the pause threshold" }
        require(pauseAfterSeconds >= 0.0 && resumeAfterSeconds >= 0.0) { "delays must not be negative" }
    }
}

/**
 * Per-sport tuning of the recording pipeline.
 *
 * @property maxSpeedMps fixes implying a faster speed than this (plus accuracy slack) are rejected as jumps.
 * @property movingRule rule for moving time when auto-pause is off.
 * @property autoPause auto-pause thresholds, or null when the sport never auto-pauses (for example swims).
 * @property processNoiseAccel expected acceleration (m/s²) of the smoothing filter; higher follows turns faster.
 * @property stationaryEnterMps speed under which consecutive fixes mark the athlete as standing still.
 * @property stationaryExitMps speed over which consecutive fixes end the stationary state.
 */
public data class SportProfile(
    val maxSpeedMps: Double,
    val movingRule: MovingTimeRule,
    val autoPause: AutoPauseRule?,
    val processNoiseAccel: Double,
    val stationaryEnterMps: Double = 0.25,
    val stationaryExitMps: Double = 0.45,
) {
    public companion object {
        /** Default profile for [sport], following the recording design doc (§4.3, §6.2, §7.2). */
        public fun forSport(sport: SportType): SportProfile {
            val rule = MovingTimeRule.forSport(sport)
            return when {
                sport.isRunLike -> SportProfile(12.0, rule, AutoPauseRule(0.5, 4.0, 1.2, 2.0), 1.5)
                sport.category == SportCategory.FOOT -> SportProfile(8.0, rule, AutoPauseRule(0.2, 10.0, 0.6, 3.0), 1.0)
                sport.category == SportCategory.BIKE -> SportProfile(35.0, rule, AutoPauseRule(1.0, 3.0, 1.5, 2.0), 3.0)
                sport.category == SportCategory.SNOW -> SportProfile(45.0, rule, AutoPauseRule(0.8, 10.0, 1.5, 2.0), 3.0)
                sport == SportType.SWIM -> SportProfile(5.0, rule, null, 0.5)
                else -> SportProfile(70.0, rule, AutoPauseRule(0.5, 5.0, 1.0, 2.0), 3.0)
            }
        }
    }
}

/**
 * Configuration of one recording.
 *
 * @property sport the sport being recorded.
 * @property autoPauseEnabled whether auto-pause is on; defaults to on for rides only (product decision).
 * @property unitSystem decides the auto-split length (1 km or 1 mile).
 * @property profile per-sport tuning.
 * @property maxAccuracyMeters fixes with a worse reported accuracy are ignored while recording.
 * @property autoResumeMaxAccuracyMeters auto-resume is suppressed while accuracy is worse than this, so
 *   indoor drift cannot resume a paused activity.
 * @property minStepMeters smallest position change added to the distance; jitter below it is ignored.
 * @property gapResetSeconds after this long without an accepted fix the smoother restarts from the next fix.
 * @property gpsLostSeconds GPS quality reports [GpsQuality.NONE] when the last fix is older than this.
 * @property significantGapMeters GPS gaps that bridge more than this are flagged on the finished activity.
 */
public data class RecordingConfig(
    val sport: SportType,
    val autoPauseEnabled: Boolean = sport.isRideLike,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val profile: SportProfile = SportProfile.forSport(sport),
    val maxAccuracyMeters: Double = 50.0,
    val autoResumeMaxAccuracyMeters: Double = 30.0,
    val minStepMeters: Double = 3.0,
    val gapResetSeconds: Double = 10.0,
    val gpsLostSeconds: Double = 10.0,
    val significantGapMeters: Double = 100.0,
) {
    init {
        require(maxAccuracyMeters > 0.0) { "max accuracy must be positive" }
        require(minStepMeters >= 0.0) { "min step must not be negative" }
        require(gapResetSeconds > 0.0 && gpsLostSeconds > 0.0) { "timeouts must be positive" }
    }
}
