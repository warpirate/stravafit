// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.geo.BoundingBox
import org.openactivity.metrics.DistanceEffort
import org.openactivity.metrics.DurationEffort
import org.openactivity.model.Lap
import org.openactivity.model.SportType
import org.openactivity.model.Split
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.recording.ActivityFlag
import org.openactivity.recording.AltitudeSource
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration

/**
 * A saved activity as the UI sees it: summary metrics and editable metadata, without streams.
 *
 * @property uuid stable client id (upload idempotency key).
 * @property perceivedExertion RPE on a 1..10 scale, or null.
 * @property polyline encoded polyline (precision 5) of the simplified track, null without GPS.
 * @property bounds bounding box of the track, null without GPS.
 * @property hideStartMeters radius hidden around the start on public views.
 * @property hideEndMeters radius hidden around the end on public views.
 */
data class SavedActivity(
    val id: Long,
    val uuid: String,
    val sport: SportType,
    val title: String,
    val description: String,
    val visibility: Visibility,
    val perceivedExertion: Int?,
    val startTime: Instant,
    val timeZone: ZoneId,
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
    val autoPauseCount: Int,
    val polyline: String?,
    val bounds: BoundingBox?,
    val hideStartMeters: Double,
    val hideEndMeters: Double,
    val flags: Set<ActivityFlag>,
    val routeId: Long?,
)

/**
 * One sample of a saved activity's streams.
 *
 * @property offsetMillis time since the start on the monotonic clock.
 * @property paused taken while paused (excluded from totals, kept for cropping).
 * @property segment track segment; a new one starts after a resume or a recovered interruption.
 */
data class ActivityPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val epochMillis: Long,
    val offsetMillis: Long,
    val heartRate: Int?,
    val cadence: Double?,
    val powerWatts: Int?,
    val speedMps: Double,
    val accuracyMeters: Double?,
    val distanceMeters: Double,
    val paused: Boolean,
    val segment: Int,
)

/** An activity with its streams, laps, splits for both unit systems, and best efforts. */
data class ActivityDetail(
    val activity: SavedActivity,
    val points: List<ActivityPoint>,
    val laps: List<Lap>,
    val metricSplits: List<Split>,
    val imperialSplits: List<Split>,
    val bestEfforts: List<DistanceEffort>,
    val durationEfforts: List<DurationEffort>,
) {
    /** Splits in [units] (per km or per mile). */
    fun splits(units: UnitSystem): List<Split> = if (units == UnitSystem.METRIC) metricSplits else imperialSplits
}

/**
 * The fields the save and edit screens change.
 *
 * @property perceivedExertion RPE 1..10 or null to clear it.
 */
data class ActivityMetadataUpdate(
    val sport: SportType,
    val title: String,
    val description: String,
    val visibility: Visibility,
    val perceivedExertion: Int?,
) {
    init {
        require(perceivedExertion == null || perceivedExertion in 1..10) { "RPE must be 1..10" }
    }
}

/**
 * Choices applied when a finished recording is saved.
 *
 * @property uuid the recording's id, reused as the activity uuid.
 * @property title explicit title, or null for the default ("Morning Run").
 */
data class NewActivity(
    val uuid: String,
    val title: String? = null,
    val description: String = "",
    val visibility: Visibility = Visibility.DEFAULT,
    val perceivedExertion: Int? = null,
    val routeId: Long? = null,
    val hideStartMeters: Double = 0.0,
    val hideEndMeters: Double = 0.0,
    val timeZone: ZoneId = ZoneId.systemDefault(),
)
