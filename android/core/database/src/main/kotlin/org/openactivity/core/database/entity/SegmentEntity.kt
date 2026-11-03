// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A personal segment: a stretch of road or trail cut from one of your activities, matched against every
 * activity you save afterwards. It lives on this phone only; there is no public leaderboard.
 *
 * @property sport `SportType` name of the segment; activities of the same family (all runs, all rides) match.
 * @property polyline encoded polyline (precision 5) of the segment, start to finish.
 * @property elevations elevation in metres at each polyline point, comma-separated; empty when unknown.
 * @property averageGradePercent (finish elevation - start elevation) / distance, in percent.
 * @property sourceActivityId the activity it was cut from; kept as a plain number, so deleting that activity
 *   keeps the segment.
 */
@Entity(
    tableName = "segments",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["starred"])],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val sport: String,
    val polyline: String,
    val elevations: String,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elevation_gain_meters") val elevationGainMeters: Double,
    @ColumnInfo(name = "average_grade_percent") val averageGradePercent: Double,
    @ColumnInfo(name = "start_latitude") val startLatitude: Double,
    @ColumnInfo(name = "start_longitude") val startLongitude: Double,
    @ColumnInfo(name = "end_latitude") val endLatitude: Double,
    @ColumnInfo(name = "end_longitude") val endLongitude: Double,
    @ColumnInfo(name = "bbox_south") val bboxSouth: Double,
    @ColumnInfo(name = "bbox_west") val bboxWest: Double,
    @ColumnInfo(name = "bbox_north") val bboxNorth: Double,
    @ColumnInfo(name = "bbox_east") val bboxEast: Double,
    @ColumnInfo(name = "source_activity_id") val sourceActivityId: Long?,
    val starred: Boolean,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

/**
 * One effort on a segment: the stretch of an activity between the segment's start and finish gates.
 *
 * Times are interpolated at the gates, so they are not tied to the sampling interval. Ranks are frozen
 * when the effort is saved (as Strava does): a later, faster effort never takes a medal away.
 *
 * @property startIndex index of the first activity point inside the effort (`activity_points.seq`).
 * @property endIndex index of the last activity point inside the effort.
 * @property startOffsetMillis activity time at the start gate.
 * @property startEpochMillis wall-clock time at the start gate (orders efforts and picks their year).
 * @property year calendar year of the effort in the activity's time zone.
 * @property rankAllTime place among all your efforts on the segment when it was saved; null for the very
 *   first effort (nothing to rank against).
 * @property rankYear place among your efforts in [year] when it was saved; null for the first one that year.
 * @property isPersonalRecord the fastest effort ever when it was saved (beating at least one earlier one).
 */
@Entity(
    tableName = "segment_efforts",
    foreignKeys = [
        ForeignKey(entity = SegmentEntity::class, parentColumns = ["id"], childColumns = ["segment_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["segment_id", "activity_id", "start_index"], unique = true),
        Index(value = ["activity_id"]),
        Index(value = ["segment_id", "elapsed_millis"]),
    ],
)
data class SegmentEffortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "segment_id") val segmentId: Long,
    @ColumnInfo(name = "activity_id") val activityId: Long,
    @ColumnInfo(name = "start_index") val startIndex: Int,
    @ColumnInfo(name = "end_index") val endIndex: Int,
    @ColumnInfo(name = "start_offset_millis") val startOffsetMillis: Long,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    val year: Int,
    @ColumnInfo(name = "elapsed_millis") val elapsedMillis: Long,
    @ColumnInfo(name = "moving_millis") val movingMillis: Long,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "average_speed_mps") val averageSpeedMps: Double,
    @ColumnInfo(name = "average_heart_rate") val averageHeartRate: Double?,
    @ColumnInfo(name = "max_heart_rate") val maxHeartRate: Int?,
    @ColumnInfo(name = "average_power") val averagePower: Double?,
    @ColumnInfo(name = "average_cadence") val averageCadence: Double?,
    @ColumnInfo(name = "rank_all_time") val rankAllTime: Int?,
    @ColumnInfo(name = "rank_year") val rankYear: Int?,
    @ColumnInfo(name = "is_personal_record") val isPersonalRecord: Boolean,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

/** An effort with its activity and its segment, for effort lists. */
data class SegmentEffortRow(
    @Embedded val effort: SegmentEffortEntity,
    @ColumnInfo(name = "activity_title") val activityTitle: String,
    @ColumnInfo(name = "activity_sport") val activitySport: String,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
    @ColumnInfo(name = "segment_name") val segmentName: String,
    @ColumnInfo(name = "segment_sport") val segmentSport: String,
    @ColumnInfo(name = "segment_distance_meters") val segmentDistanceMeters: Double,
    @ColumnInfo(name = "segment_grade_percent") val segmentGradePercent: Double,
)

/** A segment with its effort count and best time, for lists. */
data class SegmentSummaryRow(
    @Embedded val segment: SegmentEntity,
    @ColumnInfo(name = "effort_count") val effortCount: Int,
    @ColumnInfo(name = "best_elapsed_millis") val bestElapsedMillis: Long?,
    @ColumnInfo(name = "best_activity_id") val bestActivityId: Long?,
    @ColumnInfo(name = "best_start_epoch_millis") val bestStartEpochMillis: Long?,
)

/** The columns matching needs to pick candidate activities, without loading whole rows. */
data class ActivityMatchRow(
    val id: Long,
    val sport: String,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
    @ColumnInfo(name = "bbox_south") val bboxSouth: Double?,
    @ColumnInfo(name = "bbox_west") val bboxWest: Double?,
    @ColumnInfo(name = "bbox_north") val bboxNorth: Double?,
    @ColumnInfo(name = "bbox_east") val bboxEast: Double?,
)
