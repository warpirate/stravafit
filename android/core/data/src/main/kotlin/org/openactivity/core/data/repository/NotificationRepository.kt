// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.di.DataClock
import org.openactivity.core.data.mapper.enumOrNull
import org.openactivity.core.data.model.InboxNotification
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.database.dao.NotificationDao
import org.openactivity.core.database.entity.NotificationEntity
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The on-device notifications inbox (notifications-search section 2). */
interface NotificationRepository {
    /** The newest [limit] entries, newest first. */
    fun observeNotifications(limit: Int = DEFAULT_LIMIT): Flow<List<InboxNotification>>

    /** Entries not read yet: the bell's badge. */
    fun observeUnreadCount(): Flow<Int>

    /** Records [entry] and returns its id, or null when its event was recorded before. */
    suspend fun add(entry: NewNotification): Long?

    /** One entry, or null once it is gone. */
    suspend fun get(id: Long): InboxNotification?

    /** Whether the event [dedupeKey] has been recorded. */
    suspend fun contains(dedupeKey: String): Boolean

    suspend fun markRead(id: Long)

    suspend fun markAllRead()

    companion object {
        const val DEFAULT_LIMIT: Int = 100
    }
}

/** [NotificationRepository] over Room. Entries older than [RETENTION_DAYS] are pruned as new ones arrive. */
@Singleton
class LocalNotificationRepository @Inject constructor(
    private val dao: NotificationDao,
    @DataClock private val clock: Clock,
) : NotificationRepository {

    override fun observeNotifications(limit: Int): Flow<List<InboxNotification>> =
        dao.observeRecent(limit).map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun add(entry: NewNotification): Long? {
        val now = clock.instant()
        dao.deleteOlderThan(now.minus(RETENTION_DAYS, ChronoUnit.DAYS).toEpochMilli())
        val id = dao.insert(
            NotificationEntity(
                type = entry.type.name,
                title = entry.title,
                body = entry.body,
                target = entry.target.name,
                activityId = entry.activityId,
                createdAtMillis = now.toEpochMilli(),
                isRead = false,
                dedupeKey = entry.dedupeKey,
            ),
        )
        return id.takeIf { it > 0 }
    }

    override suspend fun get(id: Long): InboxNotification? = dao.getById(id)?.toDomain()

    override suspend fun contains(dedupeKey: String): Boolean = dao.exists(dedupeKey)

    override suspend fun markRead(id: Long) {
        dao.markRead(id)
    }

    override suspend fun markAllRead() {
        dao.markAllRead()
    }

    private fun NotificationEntity.toDomain(): InboxNotification? {
        val type = enumOrNull<NotificationType>(type) ?: return null
        return InboxNotification(
            id = id,
            type = type,
            title = title,
            body = body,
            target = enumOrNull<NotificationTarget>(target) ?: NotificationTarget.PROGRESS,
            activityId = activityId,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            isRead = isRead,
        )
    }

    private companion object {
        const val RETENTION_DAYS = 365L
    }
}
