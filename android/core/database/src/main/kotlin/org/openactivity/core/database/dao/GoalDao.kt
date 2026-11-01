// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.GoalEntity

/** Training goals. */
@Dao
interface GoalDao {
    /** All goals, weekly first, then monthly and yearly; oldest first within a period. */
    @Query(
        "SELECT * FROM goals ORDER BY CASE period WHEN 'WEEK' THEN 0 WHEN 'MONTH' THEN 1 ELSE 2 END, " +
            "created_at_millis, id",
    )
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    /** Returns the number of rows changed (0 or 1). */
    @Update
    suspend fun update(goal: GoalEntity): Int

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long): Int
}
