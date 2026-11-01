// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.entity.GoalEntity
import org.openactivity.core.database.entity.NotificationEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InboxAndGoalDaoTest {
    private lateinit var db: OpenActivityDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(key: String, at: Long, activityId: Long? = null, read: Boolean = false) = NotificationEntity(
        type = "WEEKLY_SUMMARY",
        title = "t$key",
        body = "",
        target = "PROGRESS",
        activityId = activityId,
        createdAtMillis = at,
        isRead = read,
        dedupeKey = key,
    )

    @Test
    fun `entries come newest first and a repeated dedupe key is ignored`() = runTest {
        val dao = db.notificationDao()
        assertTrue(dao.insert(entry("a", 1)) > 0)
        assertTrue(dao.insert(entry("b", 3)) > 0)
        assertEquals(-1L, dao.insert(entry("a", 5)))
        assertTrue(dao.exists("a"))
        assertFalse(dao.exists("zz"))
        assertEquals(listOf("tb", "ta"), dao.observeRecent(10).first().map { it.title })
        assertEquals(1, dao.observeRecent(1).first().size)
    }

    @Test
    fun `unread count follows mark read and mark all read`() = runTest {
        val dao = db.notificationDao()
        val first = dao.insert(entry("a", 1))
        dao.insert(entry("b", 2))
        dao.insert(entry("c", 3, read = true))
        assertEquals(2, dao.observeUnreadCount().first())
        assertEquals(1, dao.markRead(first))
        assertEquals(0, dao.markRead(first))
        assertEquals(1, dao.observeUnreadCount().first())
        assertEquals(1, dao.markAllRead())
        assertEquals(0, dao.observeUnreadCount().first())
    }

    @Test
    fun `deleting an activity removes its entries and old entries can be pruned`() = runTest {
        val id = db.activityDao().insertActivity(TestEntities.activity())
        val dao = db.notificationDao()
        dao.insert(entry("pr", 10, activityId = id))
        dao.insert(entry("old", 1))
        db.activityDao().delete(id)
        assertEquals(listOf("told"), dao.observeRecent(10).first().map { it.title })
        assertEquals(1, dao.deleteOlderThan(5))
        assertTrue(dao.observeRecent(10).first().isEmpty())
    }

    @Test
    fun `goals are listed weekly first and can be updated and deleted`() = runTest {
        val dao = db.goalDao()
        val year = dao.insert(GoalEntity(sport = null, period = "YEAR", metric = "DISTANCE", target = 1e6, createdAtMillis = 1, updatedAtMillis = 1))
        val week = dao.insert(GoalEntity(sport = "RUN", period = "WEEK", metric = "TIME", target = 3600.0, createdAtMillis = 2, updatedAtMillis = 2))
        assertEquals(listOf(week, year), dao.observeAll().first().map { it.id })
        assertEquals(1, dao.update(dao.getById(week)!!.copy(target = 7200.0, updatedAtMillis = 3)))
        assertEquals(7200.0, dao.getById(week)!!.target, 0.0)
        assertEquals(1, dao.delete(year))
        assertNull(dao.getById(year))
    }
}
