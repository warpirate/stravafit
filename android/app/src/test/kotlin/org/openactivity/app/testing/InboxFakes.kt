// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.openactivity.core.data.model.InboxNotification
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.repository.NotificationRepository
import java.time.Instant

/** In-memory inbox: entries are numbered from 1, newest first, unique by dedupe key. */
class FakeNotificationRepository : NotificationRepository {
    private val keys = mutableSetOf<String>()
    val entries = MutableStateFlow<List<InboxNotification>>(emptyList())

    override fun observeNotifications(limit: Int): Flow<List<InboxNotification>> = entries.map { it.take(limit) }

    override fun observeUnreadCount(): Flow<Int> = entries.map { list -> list.count { !it.isRead } }

    override suspend fun add(entry: NewNotification): Long? {
        if (!keys.add(entry.dedupeKey)) return null
        val id = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1
        val row = InboxNotification(id, entry.type, entry.title, entry.body, entry.target, entry.activityId, Instant.EPOCH, isRead = false, dedupeKey = entry.dedupeKey)
        entries.update { listOf(row) + it }
        return id
    }

    override suspend fun get(id: Long): InboxNotification? = entries.value.firstOrNull { it.id == id }

    override suspend fun contains(dedupeKey: String): Boolean = dedupeKey in keys

    override suspend fun markRead(id: Long) = entries.update { list -> list.map { if (it.id == id) it.copy(isRead = true) else it } }

    override suspend fun markAllRead() = entries.update { list -> list.map { it.copy(isRead = true) } }
}
