// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.ActivityMatchRow
import org.openactivity.core.database.entity.SegmentEffortEntity
import org.openactivity.core.database.entity.SegmentEffortRow
import org.openactivity.core.database.entity.SegmentEntity
import org.openactivity.core.database.entity.SegmentSummaryRow

/** Personal segments and the efforts matched on them. */
@Dao
abstract class SegmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(segment: SegmentEntity): Long

    @Query("SELECT * FROM segments WHERE id = :id")
    abstract suspend fun getById(id: Long): SegmentEntity?

    @Query("SELECT * FROM segments WHERE id = :id")
    abstract fun observeById(id: Long): Flow<SegmentEntity?>

    /** Every segment, for matching a new activity. */
    @Query("SELECT * FROM segments ORDER BY id")
    abstract suspend fun getAll(): List<SegmentEntity>

    /** Starred segments of the given [sports], for live segments while recording. */
    @Query("SELECT * FROM segments WHERE starred = 1 AND sport IN (:sports) ORDER BY id")
    abstract suspend fun getStarred(sports: List<String>): List<SegmentEntity>

    /** Every segment with its effort count and best time; starred first, then newest. */
    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM segment_efforts e WHERE e.segment_id = s.id) AS effort_count, " +
            "(SELECT e.elapsed_millis FROM segment_efforts e WHERE e.segment_id = s.id " +
            "ORDER BY e.elapsed_millis, e.start_epoch_millis LIMIT 1) AS best_elapsed_millis, " +
            "(SELECT e.activity_id FROM segment_efforts e WHERE e.segment_id = s.id " +
            "ORDER BY e.elapsed_millis, e.start_epoch_millis LIMIT 1) AS best_activity_id, " +
            "(SELECT e.start_epoch_millis FROM segment_efforts e WHERE e.segment_id = s.id " +
            "ORDER BY e.elapsed_millis, e.start_epoch_millis LIMIT 1) AS best_start_epoch_millis " +
            "FROM segments s ORDER BY s.starred DESC, s.created_at_millis DESC, s.id DESC",
    )
    abstract fun observeSummaries(): Flow<List<SegmentSummaryRow>>

    @Query("UPDATE segments SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String): Int

    @Query("UPDATE segments SET starred = :starred WHERE id = :id")
    abstract suspend fun setStarred(id: Long, starred: Boolean): Int

    /** Deletes a segment; its efforts go with it. */
    @Query("DELETE FROM segments WHERE id = :id")
    abstract suspend fun delete(id: Long): Int

    /** Adds efforts; one already stored for the same segment, activity and start point is kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertEfforts(efforts: List<SegmentEffortEntity>): List<Long>

    @Query("DELETE FROM segment_efforts WHERE segment_id = :segmentId")
    abstract suspend fun deleteEffortsOfSegment(segmentId: Long): Int

    @Query("DELETE FROM segment_efforts WHERE activity_id = :activityId")
    abstract suspend fun deleteEffortsOfActivity(activityId: Long): Int

    /** Replaces every effort of [segmentId] in one transaction (a re-match). */
    @Transaction
    open suspend fun replaceEffortsOfSegment(segmentId: Long, efforts: List<SegmentEffortEntity>) {
        deleteEffortsOfSegment(segmentId)
        insertEfforts(efforts)
    }

    /** Elapsed times and years of every effort on [segmentId], for ranking a new one. */
    @Query("SELECT * FROM segment_efforts WHERE segment_id = :segmentId ORDER BY start_epoch_millis, id")
    abstract suspend fun getEffortsOfSegment(segmentId: Long): List<SegmentEffortEntity>

    /** A segment's efforts with their activities, newest first. */
    @Query("$EFFORT_ROW WHERE e.segment_id = :segmentId ORDER BY e.start_epoch_millis DESC, e.id DESC")
    abstract fun observeEffortsOfSegment(segmentId: Long): Flow<List<SegmentEffortRow>>

    /** An activity's efforts with their segments, in the order they were ridden or run. */
    @Query("$EFFORT_ROW WHERE e.activity_id = :activityId ORDER BY e.start_offset_millis, e.id")
    abstract fun observeEffortsOfActivity(activityId: Long): Flow<List<SegmentEffortRow>>

    /** Every personal record still standing: the best effort of each segment, segments by name. */
    @Query(
        "$EFFORT_ROW WHERE e.id = (SELECT b.id FROM segment_efforts b WHERE b.segment_id = e.segment_id " +
            "ORDER BY b.elapsed_millis, b.start_epoch_millis, b.id LIMIT 1) ORDER BY s.name COLLATE NOCASE",
    )
    abstract fun observeBestEfforts(): Flow<List<SegmentEffortRow>>

    /** Efforts with these ids, for announcing new ones. */
    @Query("$EFFORT_ROW WHERE e.id IN (:ids) ORDER BY e.start_offset_millis")
    abstract suspend fun getEffortRows(ids: List<Long>): List<SegmentEffortRow>

    /**
     * Activities of [sports] whose bounding box overlaps the given one (a segment's), oldest first: the
     * candidates a new segment is matched against. Activities without GPS have no box and never match.
     */
    @Query(
        "SELECT id, sport, start_epoch_millis, time_zone_id, bbox_south, bbox_west, bbox_north, bbox_east " +
            "FROM activities WHERE sport IN (:sports) AND bbox_south IS NOT NULL " +
            "AND bbox_south <= :north AND bbox_north >= :south AND bbox_west <= :east AND bbox_east >= :west " +
            "ORDER BY start_epoch_millis, id",
    )
    abstract suspend fun candidateActivities(sports: List<String>, south: Double, west: Double, north: Double, east: Double): List<ActivityMatchRow>

    @Query(
        "SELECT id, sport, start_epoch_millis, time_zone_id, bbox_south, bbox_west, bbox_north, bbox_east " +
            "FROM activities WHERE id = :id",
    )
    abstract suspend fun matchRow(id: Long): ActivityMatchRow?

    @Query("SELECT COUNT(*) FROM segments")
    abstract suspend fun count(): Int
}

private const val EFFORT_ROW =
    "SELECT e.*, a.title AS activity_title, a.sport AS activity_sport, a.time_zone_id AS time_zone_id, " +
        "s.name AS segment_name, s.sport AS segment_sport, s.distance_meters AS segment_distance_meters, " +
        "s.average_grade_percent AS segment_grade_percent " +
        "FROM segment_efforts e JOIN activities a ON a.id = e.activity_id JOIN segments s ON s.id = e.segment_id"
