// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.model.Lap
import org.openactivity.model.SportType
import org.openactivity.model.Split
import kotlin.time.Duration

/** Lifecycle of a recording. */
public enum class RecordingStatus {
    /** Not started yet; fixes only update [GpsQuality]. */
    IDLE,

    /** Timer running. */
    RECORDING,

    /** Paused by the athlete. Only the athlete can resume (auto-pause never resumes a manual pause). */
    PAUSED,

    /** Paused automatically because the athlete stopped; resumes automatically on movement. */
    AUTO_PAUSED,

    /** Finished; [RecordingEngine.finishedActivity] holds the result. Terminal. */
    FINISHED,

    /** Discarded by the athlete. Terminal. */
    DISCARDED,
    ;

    /** True while a recording is in progress (running or paused). */
    public val isActive: Boolean get() = this == RECORDING || this == PAUSED || this == AUTO_PAUSED
}

/** Coarse GPS quality for the record screen. */
public enum class GpsQuality {
    /** No fix yet, or the last fix is too old. */
    NONE,

    /** Accuracy worse than 50 m. */
    POOR,

    /** Accuracy between 20 and 50 m. */
    FAIR,

    /** Accuracy of 20 m or better ("GPS ready"). */
    GOOD,
    ;

    public companion object {
        /** Quality for a reported [accuracyMeters] (null means unknown, treated as [FAIR]). */
        public fun fromAccuracy(accuracyMeters: Double?): GpsQuality = when {
            accuracyMeters == null -> FAIR
            accuracyMeters <= 20.0 -> GOOD
            accuracyMeters <= 50.0 -> FAIR
            else -> POOR
        }
    }
}

/** Where live altitude comes from. */
public enum class AltitudeSource {
    NONE,
    GPS,
    BAROMETER,
}

/**
 * Live elevation figures.
 *
 * @property currentMeters current altitude, or null when unknown.
 * @property gainMeters climbing so far.
 * @property lossMeters descent so far.
 */
public data class ElevationState(
    val currentMeters: Double?,
    val gainMeters: Double,
    val lossMeters: Double,
    val source: AltitudeSource,
)

/**
 * Progress of the lap in progress.
 *
 * @property index zero-based index of the current lap.
 */
public data class LapProgress(val index: Int, val elapsed: Duration, val distanceMeters: Double)

/**
 * Everything the record screen and the notification show. Produced by [RecordingEngine] after every input.
 *
 * @property elapsed time since START, including pauses (monotonic clock).
 * @property movingTime moving time so far. With auto-pause off and no manual pause this is a live estimate;
 *   the finished activity recomputes it with the shared moving-time rule.
 * @property currentSpeedMps smoothed current speed; 0 while paused.
 * @property averageSpeedMps distance divided by moving time.
 * @property splits completed auto splits.
 * @property laps completed laps.
 * @property horizontalAccuracyMeters accuracy of the latest fix, if any.
 * @property heartRateBpm latest heart rate if it is recent, else null; similarly [powerWatts] and [cadence].
 * @property autoPauseCount how many times auto-pause engaged.
 * @property isMockLocation true once any mock location has been seen.
 */
public data class RecordingState(
    val status: RecordingStatus,
    val sport: SportType,
    val elapsed: Duration,
    val movingTime: Duration,
    val distanceMeters: Double,
    val currentSpeedMps: Double,
    val averageSpeedMps: Double,
    val splits: List<Split>,
    val laps: List<Lap>,
    val currentLap: LapProgress,
    val elevation: ElevationState,
    val gpsQuality: GpsQuality,
    val horizontalAccuracyMeters: Double?,
    val heartRateBpm: Int?,
    val powerWatts: Int?,
    val cadence: Double?,
    val autoPauseCount: Int,
    val isMockLocation: Boolean,
) {
    /** Current pace in seconds per kilometre, or null when not moving. */
    val currentPaceSecondsPerKm: Double? get() = paceOf(currentSpeedMps)

    /** Average pace in seconds per kilometre, or null when not moving. */
    val averagePaceSecondsPerKm: Double? get() = paceOf(averageSpeedMps)

    private fun paceOf(speed: Double): Double? = if (speed > 0.0 && speed.isFinite()) 1000.0 / speed else null
}
