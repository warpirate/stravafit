// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.PrivacyZoneEntity

/** Privacy zones, oldest first (the order the owner added them). */
@Dao
abstract class PrivacyZoneDao {
    @Query("SELECT * FROM privacy_zones ORDER BY created_at_millis, id")
    abstract fun observeAll(): Flow<List<PrivacyZoneEntity>>

    @Query("SELECT * FROM privacy_zones ORDER BY created_at_millis, id")
    abstract suspend fun getAll(): List<PrivacyZoneEntity>

    @Query("SELECT * FROM privacy_zones WHERE id = :id")
    abstract suspend fun getById(id: Long): PrivacyZoneEntity?

    @Query("SELECT COUNT(*) FROM privacy_zones")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(zone: PrivacyZoneEntity): Long

    /**
     * Inserts [zone] unless [limit] zones exist already, in one transaction so two quick adds cannot both
     * pass the check. Returns the new id, or null when the limit was reached.
     */
    @Transaction
    open suspend fun insertWithin(zone: PrivacyZoneEntity, limit: Int): Long? =
        if (count() >= limit) null else insert(zone)

    /** Returns the number of rows changed (0 or 1). */
    @Update
    abstract suspend fun update(zone: PrivacyZoneEntity): Int

    @Query("DELETE FROM privacy_zones WHERE id = :id")
    abstract suspend fun delete(id: Long): Int
}
