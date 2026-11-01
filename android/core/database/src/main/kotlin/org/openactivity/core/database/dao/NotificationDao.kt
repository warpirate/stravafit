// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.NotificationEntity

/** The notifications inbox. */
@Dao
interface NotificationDao {
    /** The newest [limit] entries, newest first. */
    @Query("SELECT * FROM notifications ORDER BY created_at_millis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    /** Inserts [entry] and returns its id, or -1 when an entry with the same dedupe key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Long): NotificationEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM notifications WHERE dedupe_key = :key)")
    suspend fun exists(key: String): Boolean

    @Query("UPDATE notifications SET is_read = 1 WHERE id = :id AND is_read = 0")
    suspend fun markRead(id: Long): Int

    @Query("UPDATE notifications SET is_read = 1 WHERE is_read = 0")
    suspend fun markAllRead(): Int

    /** Deletes entries created before [beforeMillis]; returns how many went. */
    @Query("DELETE FROM notifications WHERE created_at_millis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int
}
