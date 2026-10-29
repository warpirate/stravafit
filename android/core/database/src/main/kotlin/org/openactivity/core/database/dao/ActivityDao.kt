// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityMetadataEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.ActivityStatRow
import org.openactivity.core.database.entity.BestEffortEntity
import org.openactivity.core.database.entity.BestEffortWithActivity
import org.openactivity.core.database.entity.LapEntity
import org.openactivity.core.database.entity.SplitEntity

/** Saved activities and their child rows (points, laps, splits, best efforts). */
@Dao
abstract class ActivityDao {

    /**
     * Inserts an activity with all its child rows in one transaction and returns the new id.
     * Child rows are given the new id, whatever `activityId` they carried.
     */
    @Transaction
    open suspend fun insertActivityWithChildren(
        activity: ActivityEntity,
        points: List<ActivityPointEntity>,
        laps: List<LapEntity>,
        splits: List<SplitEntity>,
        bestEfforts: List<BestEffortEntity>,
    ): Long {
        val id = insertActivity(activity)
        insertPoints(points.map { it.copy(activityId = id) })
        insertLaps(laps.map { it.copy(activityId = id) })
        insertSplits(splits.map { it.copy(activityId = id) })
        insertBestEfforts(bestEfforts.map { it.copy(activityId = id) })
        return id
    }

    /** Inserts [activity]; fails if its uuid already exists. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertActivity(activity: ActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPoints(points: List<ActivityPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLaps(laps: List<LapEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSplits(splits: List<SplitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBestEfforts(efforts: List<BestEffortEntity>)

    /** All activities, newest first. */
    @Query("SELECT * FROM activities ORDER BY start_epoch_millis DESC, id DESC")
    abstract fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    abstract fun observeById(id: Long): Flow<ActivityEntity?>

    @Query("SELECT * FROM activities WHERE id = :id")
    abstract suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE uuid = :uuid")
    abstract suspend fun getByUuid(uuid: String): ActivityEntity?

    @Query("SELECT COUNT(*) FROM activities")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT * FROM activity_points WHERE activity_id = :activityId ORDER BY seq")
    abstract fun observePoints(activityId: Long): Flow<List<ActivityPointEntity>>

    @Query("SELECT * FROM activity_points WHERE activity_id = :activityId ORDER BY seq")
    abstract suspend fun getPoints(activityId: Long): List<ActivityPointEntity>

    @Query("SELECT * FROM laps WHERE activity_id = :activityId ORDER BY lap_index")
    abstract fun observeLaps(activityId: Long): Flow<List<LapEntity>>

    @Query("SELECT * FROM splits WHERE activity_id = :activityId ORDER BY unit_system, split_index")
    abstract fun observeSplits(activityId: Long): Flow<List<SplitEntity>>

    @Query("SELECT * FROM best_efforts WHERE activity_id = :activityId ORDER BY kind, target")
    abstract fun observeBestEfforts(activityId: Long): Flow<List<BestEffortEntity>>

    /** Updates only the editable metadata columns. Returns the number of rows changed (0 or 1). */
    @Update(entity = ActivityEntity::class)
    abstract suspend fun updateMetadata(metadata: ActivityMetadataEntity): Int

    /** Deletes an activity; its child rows go with it (foreign keys cascade). */
    @Query("DELETE FROM activities WHERE id = :id")
    abstract suspend fun delete(id: Long): Int

    /** Lightweight rows for stats, from [fromEpochMillis] (inclusive) on. */
    @Query(
        "SELECT id, sport, start_epoch_millis, time_zone_id, elapsed_millis, moving_millis, distance_meters, " +
            "elevation_gain_meters FROM activities WHERE start_epoch_millis >= :fromEpochMillis " +
            "ORDER BY start_epoch_millis",
    )
    abstract fun observeStatRows(fromEpochMillis: Long): Flow<List<ActivityStatRow>>

    /**
     * Every best effort of [kind] for activities of the given [sports], best first within each target
     * (shortest time for distance efforts, longest distance for duration efforts).
     */
    @Query(
        "SELECT be.activity_id, be.kind, be.target, be.value, be.start_seconds, a.sport, a.title, " +
            "a.start_epoch_millis FROM best_efforts be JOIN activities a ON a.id = be.activity_id " +
            "WHERE be.kind = :kind AND a.sport IN (:sports) " +
            "ORDER BY be.target, CASE WHEN be.kind = 'DISTANCE' THEN be.value ELSE -be.value END, " +
            "a.start_epoch_millis",
    )
    abstract fun observeBestEfforts(kind: String, sports: List<String>): Flow<List<BestEffortWithActivity>>
}
