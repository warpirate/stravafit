// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.entity.ActivityPrivacyEntity
import org.openactivity.core.database.entity.PrivacyZoneEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivacyZoneDaoTest {
    private lateinit var db: OpenActivityDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun zone(uuid: String, at: Long) =
        PrivacyZoneEntity(uuid = uuid, label = "Z$uuid", centerLatitude = 18.5, centerLongitude = 73.8, radiusMeters = 200, createdAtMillis = at, updatedAtMillis = at)

    @Test
    fun `zones list oldest first and update and delete by id`() = runTest {
        val dao = db.privacyZoneDao()
        val b = dao.insert(zone("b", 20))
        val a = dao.insert(zone("a", 10))
        assertEquals(listOf("a", "b"), dao.observeAll().first().map { it.uuid })

        assertEquals(1, dao.update(dao.getById(a)!!.copy(label = "Home", radiusMeters = 800)))
        assertEquals("Home", dao.getById(a)?.label)
        assertEquals(800, dao.getById(a)?.radiusMeters)

        assertEquals(1, dao.delete(b))
        assertNull(dao.getById(b))
        assertEquals(1, dao.count())
    }

    @Test
    fun `insertWithin stops at the limit`() = runTest {
        val dao = db.privacyZoneDao()
        repeat(3) { assertEquals(true, dao.insertWithin(zone("z$it", it.toLong()), limit = 3) != null) }
        assertNull(dao.insertWithin(zone("z9", 9), limit = 3))
        assertEquals(3, dao.count())
    }

    @Test
    fun `privacy update leaves the other columns`() = runTest {
        val dao = db.activityDao()
        val id = dao.insertActivity(TestEntities.activity())
        assertEquals(1, dao.updatePrivacy(ActivityPrivacyEntity(id, 0.0, 0.0)))
        val stored = dao.getById(id)!!
        assertEquals(0.0, stored.privacyHideStartMeters, 0.0)
        assertEquals(0.0, stored.privacyHideEndMeters, 0.0)
        assertEquals(TestEntities.activity().title, stored.title)
    }
}
