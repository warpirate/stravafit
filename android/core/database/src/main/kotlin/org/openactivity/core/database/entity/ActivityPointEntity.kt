// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One sample of an activity's track and sensor streams.
 *
 * @property seq zero-based position in the stream.
 * @property offsetMillis time since the activity start on the monotonic clock.
 * @property epochMillis wall-clock time of the sample.
 * @property elevationMeters best altitude (barometric when available), or null.
 * @property distanceMeters cumulative distance at this sample.
 * @property paused true when taken while paused (kept for cropping, excluded from totals).
 * @property segment track segment; a new one starts after a resume or an interruption.
 */
@Entity(
    tableName = "activity_points",
    primaryKeys = ["activity_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActivityPointEntity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "elevation_meters") val elevationMeters: Double?,
    @ColumnInfo(name = "epoch_millis") val epochMillis: Long,
    @ColumnInfo(name = "offset_millis") val offsetMillis: Long,
    @ColumnInfo(name = "heart_rate") val heartRate: Int?,
    val cadence: Double?,
    @ColumnInfo(name = "power_watts") val powerWatts: Int?,
    @ColumnInfo(name = "speed_mps") val speedMps: Double,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Double?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    val paused: Boolean,
    val segment: Int,
)
