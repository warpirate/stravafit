// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/** A lap of an activity (manual, auto-lap by distance, or the closing session lap). */
@Entity(
    tableName = "laps",
    primaryKeys = ["activity_id", "lap_index"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LapEntity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "lap_index") val index: Int,
    @ColumnInfo(name = "start_offset_millis") val startOffsetMillis: Long,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elevation_gain_meters") val elevationGainMeters: Double,
    val trigger: String,
)

/**
 * An auto split of an activity. Splits are stored for both unit systems, so switching units never needs a
 * recomputation.
 *
 * @property unitSystem `METRIC` (1 km splits) or `IMPERIAL` (1 mile splits).
 */
@Entity(
    tableName = "splits",
    primaryKeys = ["activity_id", "unit_system", "split_index"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SplitEntity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "unit_system") val unitSystem: String,
    @ColumnInfo(name = "split_index") val index: Int,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "elevation_delta_meters") val elevationDeltaMeters: Double?,
    @ColumnInfo(name = "is_partial") val isPartial: Boolean,
)

/**
 * A best effort found in an activity.
 *
 * @property kind `DISTANCE` (fastest time over [target] metres; [value] is seconds) or `DURATION` (furthest
 *   distance within [target] seconds; [value] is metres).
 * @property startSeconds activity time at which the effort starts.
 */
@Entity(
    tableName = "best_efforts",
    primaryKeys = ["activity_id", "kind", "target"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BestEffortEntity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    val kind: String,
    val target: Double,
    val value: Double,
    @ColumnInfo(name = "start_seconds") val startSeconds: Double,
)

/** A best effort joined with the activity it belongs to, for the personal-bests list. */
data class BestEffortWithActivity(
    @ColumnInfo(name = "activity_id") val activityId: Long,
    val kind: String,
    val target: Double,
    val value: Double,
    @ColumnInfo(name = "start_seconds") val startSeconds: Double,
    val sport: String,
    val title: String,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
)
