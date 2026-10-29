// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved activity: summary metrics plus the metadata the athlete can edit.
 *
 * Enum-like values are stored by their stable `name` (sport, visibility, altitude source), so reordering an
 * enum never corrupts old rows. Durations are milliseconds, distances metres, speeds metres per second.
 *
 * @property uuid stable client id. It doubles as the idempotency key for a later upload and lets crash
 *   recovery detect a recording that was already saved.
 * @property perceivedExertion RPE on a 1..10 scale, or null when not rated.
 * @property timeZoneId IANA zone the activity was recorded in, for local-time labels and week buckets.
 * @property polyline encoded polyline (precision 5) of the simplified track, or null without GPS.
 * @property bboxSouth bounding box of the full track; all four edges are null without GPS.
 * @property privacyHideStartMeters radius hidden around the start on public views (copied from settings).
 * @property privacyHideEndMeters radius hidden around the end on public views.
 * @property flags comma-separated quality flags (mock location, GPS gap, recovered after interruption).
 * @property routeId the saved route followed while recording, if any.
 */
@Entity(
    tableName = "activities",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["start_epoch_millis"]),
        Index(value = ["sport"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val sport: String,
    val title: String,
    val description: String,
    val visibility: String,
    @ColumnInfo(name = "perceived_exertion") val perceivedExertion: Int?,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elevation_gain_meters") val elevationGainMeters: Double,
    @ColumnInfo(name = "elevation_loss_meters") val elevationLossMeters: Double,
    @ColumnInfo(name = "altitude_source") val altitudeSource: String,
    @ColumnInfo(name = "average_speed_mps") val averageSpeedMps: Double,
    @ColumnInfo(name = "max_speed_mps") val maxSpeedMps: Double,
    @ColumnInfo(name = "average_gap_speed_mps") val averageGradeAdjustedSpeedMps: Double?,
    @ColumnInfo(name = "average_heart_rate") val averageHeartRate: Double?,
    @ColumnInfo(name = "max_heart_rate") val maxHeartRate: Int?,
    @ColumnInfo(name = "average_power") val averagePower: Double?,
    @ColumnInfo(name = "max_power") val maxPower: Int?,
    @ColumnInfo(name = "average_cadence") val averageCadence: Double?,
    @ColumnInfo(name = "auto_pause_count") val autoPauseCount: Int,
    val polyline: String?,
    @ColumnInfo(name = "bbox_south") val bboxSouth: Double?,
    @ColumnInfo(name = "bbox_west") val bboxWest: Double?,
    @ColumnInfo(name = "bbox_north") val bboxNorth: Double?,
    @ColumnInfo(name = "bbox_east") val bboxEast: Double?,
    @ColumnInfo(name = "privacy_hide_start_meters") val privacyHideStartMeters: Double,
    @ColumnInfo(name = "privacy_hide_end_meters") val privacyHideEndMeters: Double,
    val flags: String,
    @ColumnInfo(name = "route_id") val routeId: Long?,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)

/** The athlete-editable fields of an activity, for partial updates through `@Update(entity = ...)`. */
data class ActivityMetadataEntity(
    val id: Long,
    val sport: String,
    val title: String,
    val description: String,
    val visibility: String,
    @ColumnInfo(name = "perceived_exertion") val perceivedExertion: Int?,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)

/** The columns stats aggregation needs, without loading whole rows. */
data class ActivityStatRow(
    val id: Long,
    val sport: String,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elevation_gain_meters") val elevationGainMeters: Double,
)
