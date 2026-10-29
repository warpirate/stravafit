// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.JournalPointEntity
import org.openactivity.core.database.entity.RecordingJournalEntity

/** The crash-safe journal of the in-progress recording. */
@Dao
abstract class RecordingJournalDao {

    /** Replaces any previous journal with a fresh one for a new recording. */
    @Transaction
    open suspend fun begin(journal: RecordingJournalEntity) {
        clear()
        upsertJournal(journal)
    }

    /**
     * Atomically appends [newPoints] and replaces the journal row (engine snapshot and point count), so a
     * crash at any moment leaves a consistent snapshot and point list.
     */
    @Transaction
    open suspend fun flush(journal: RecordingJournalEntity, newPoints: List<JournalPointEntity>) {
        upsertJournal(journal)
        if (newPoints.isNotEmpty()) insertPoints(newPoints)
        deletePointsFrom(journal.pointCount)
    }

    @Upsert
    abstract suspend fun upsertJournal(journal: RecordingJournalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPoints(points: List<JournalPointEntity>)

    /** Drops points beyond the snapshot (left by an earlier flush that the snapshot no longer covers). */
    @Query("DELETE FROM recording_journal_points WHERE seq >= :count")
    protected abstract suspend fun deletePointsFrom(count: Int)

    @Query("SELECT * FROM recordings_journal WHERE id = ${RecordingJournalEntity.SINGLETON_ID}")
    abstract suspend fun get(): RecordingJournalEntity?

    @Query("SELECT * FROM recordings_journal WHERE id = ${RecordingJournalEntity.SINGLETON_ID}")
    abstract fun observe(): Flow<RecordingJournalEntity?>

    /** The first [count] journal points in order. */
    @Query("SELECT * FROM recording_journal_points WHERE seq < :count ORDER BY seq")
    abstract suspend fun getPoints(count: Int): List<JournalPointEntity>

    /** Removes the journal and all its points. */
    @Transaction
    open suspend fun clear() {
        deleteAllPoints()
        deleteJournal()
    }

    @Query("DELETE FROM recording_journal_points")
    protected abstract suspend fun deleteAllPoints()

    @Query("DELETE FROM recordings_journal")
    protected abstract suspend fun deleteJournal()
}
