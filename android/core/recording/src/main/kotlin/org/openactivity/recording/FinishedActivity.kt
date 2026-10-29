// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.metrics.DistanceEffort
import org.openactivity.metrics.DurationEffort
import org.openactivity.model.Lap
import org.openactivity.model.SportType
import org.openactivity.model.Split
import kotlin.time.Duration

/**
 * One accepted location sample of the finished track.
 *
 * @property offsetNanos monotonic time since START.
 * @property epochMillis wall-clock time rebuilt from the start anchor plus [offsetNanos], so it is always
 *   monotonic even when the device clock jumped during the activity.
 * @property latitude smoothed latitude (used for maps, distance and exports).
 * @property longitude smoothed longitude.
 * @property rawLatitude latitude as reported by the receiver.
 * @property rawLongitude longitude as reported by the receiver.
 * @property accuracyMeters reported horizontal accuracy, if known.
 * @property altitudeMeters best altitude at this moment (barometric when available), if known.
 * @property speedMps smoothed speed.
 * @property distanceMeters cumulative distance at this sample.
 * @property paused true when the sample was taken while paused (kept for cropping, excluded from totals).
 * @property segment track segment; a new segment starts after a manual resume or an interruption.
 */
public data class TrackPoint(
    val offsetNanos: Long,
    val epochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val rawLatitude: Double,
    val rawLongitude: Double,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val speedMps: Double,
    val distanceMeters: Double,
    val paused: Boolean,
    val segment: Int,
    val heartRateBpm: Int?,
    val cadence: Double?,
    val powerWatts: Int?,
)

/** Why the timer was stopped. */
public enum class PauseKind {
    /** The athlete pressed pause. */
    MANUAL,

    /** Auto-pause detected a stop. */
    AUTO,

    /** The recording process was interrupted (killed, crashed or the phone rebooted). */
    INTERRUPTION,
}

/**
 * A span during which the timer was stopped.
 *
 * @property startOffsetNanos monotonic offset from START at which the pause began.
 * @property endOffsetNanos offset at which it ended.
 */
public data class PauseInterval(val kind: PauseKind, val startOffsetNanos: Long, val endOffsetNanos: Long) {
    /** Length of the pause. */
    val durationNanos: Long get() = endOffsetNanos - startOffsetNanos
}

/**
 * A stretch without accepted GPS fixes while recording (tunnel, dense forest, receiver dropout).
 *
 * @property bridgedMeters straight-line distance added across the gap.
 */
public data class GpsGap(val startOffsetNanos: Long, val durationNanos: Long, val bridgedMeters: Double)

/** Quality flags attached to a finished activity. */
public enum class ActivityFlag {
    /** At least one mock location was recorded; the activity must be excluded from leaderboards. */
    MOCK_LOCATION,

    /** A GPS gap bridged more than [RecordingConfig.significantGapMeters]; shown on the summary. */
    SIGNIFICANT_GPS_GAP,

    /** The recording was interrupted and recovered. */
    RECOVERED_AFTER_INTERRUPTION,
}

/**
 * The result of a finished recording, ready to be saved locally and queued for upload.
 *
 * @property startEpochMillis wall-clock start time, or null if no wall-clock time was ever supplied.
 * @property elapsedTime START to FINISH on the monotonic clock.
 * @property movingTime timer time when auto-pause was on or the athlete paused manually; otherwise moving
 *   time from the shared moving-time rule (the same computation the server runs).
 * @property maxSpeedMps highest smoothed speed while moving, capped at the sport's plausible maximum.
 * @property bestEfforts fastest times for the sport's standard distances.
 * @property durationEfforts furthest distances for the sport's standard durations (rides).
 * @property averageGradeAdjustedSpeedMps grade-adjusted speed for runs with altitude, else null.
 */
public data class FinishedActivity(
    val sport: SportType,
    val startEpochMillis: Long?,
    val elapsedTime: Duration,
    val movingTime: Duration,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val altitudeSource: AltitudeSource,
    val averageSpeedMps: Double,
    val maxSpeedMps: Double,
    val averageGradeAdjustedSpeedMps: Double?,
    val averageHeartRate: Double?,
    val maxHeartRate: Int?,
    val averagePower: Double?,
    val maxPower: Int?,
    val averageCadence: Double?,
    val splits: List<Split>,
    val laps: List<Lap>,
    val bestEfforts: List<DistanceEffort>,
    val durationEfforts: List<DurationEffort>,
    val pauses: List<PauseInterval>,
    val autoPauseCount: Int,
    val gpsGaps: List<GpsGap>,
    val flags: Set<ActivityFlag>,
    val points: List<TrackPoint>,
)
