// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.entity.RouteEntity
import org.openactivity.core.database.entity.RoutePointEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JournalAndRouteDaoTest {
    private lateinit var db: OpenActivityDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `flushes append points and replace the snapshot`() = runTest {
        val dao = db.recordingJournalDao()
        dao.begin(TestEntities.journal(pointCount = 0))
        dao.flush(TestEntities.journal(3, byteArrayOf(9)), (0 until 3).map(TestEntities::journalPoint))
        dao.flush(TestEntities.journal(5, byteArrayOf(8, 8)), (3 until 5).map(TestEntities::journalPoint))

        val journal = dao.get()!!
        assertEquals(5, journal.pointCount)
        assertArrayEquals(byteArrayOf(8, 8), journal.engineSnapshot)
        assertEquals((0 until 5).toList(), dao.getPoints(journal.pointCount).map { it.seq })
    }

    @Test
    fun `begin replaces an older journal and clear removes everything`() = runTest {
        val dao = db.recordingJournalDao()
        dao.begin(TestEntities.journal(0))
        dao.flush(TestEntities.journal(2), (0 until 2).map(TestEntities::journalPoint))
        dao.begin(TestEntities.journal(0).copy(recordingUuid = "rec-2"))
        assertEquals("rec-2", dao.get()?.recordingUuid)
        assertTrue(dao.getPoints(100).isEmpty())

        dao.flush(TestEntities.journal(1).copy(recordingUuid = "rec-2"), listOf(TestEntities.journalPoint(0)))
        dao.clear()
        assertNull(dao.get())
        assertNull(dao.observe().first())
        assertTrue(dao.getPoints(100).isEmpty())
    }

    @Test
    fun `routes keep their points in order and delete cascades`() = runTest {
        val dao = db.routeDao()
        val route = RouteEntity(
            uuid = "r1", name = "Lake loop", description = "", sport = "RUN", distanceMeters = 5000.0,
            elevationGainMeters = 30.0, elevationLossMeters = 30.0, polyline = "abc", bboxSouth = 1.0,
            bboxWest = 2.0, bboxNorth = 3.0, bboxEast = 4.0, createdAtMillis = 10L,
        )
        val id = dao.insertRouteWithPoints(
            route,
            (0 until 20).reversed().map { RoutePointEntity(0, it, 47.0, 8.0 + it * 1e-3, null, it * 76.0) },
        )
        assertEquals((0 until 20).toList(), dao.getPoints(id).map { it.seq })
        assertEquals(listOf("Lake loop"), dao.observeAll().first().map { it.name })
        assertEquals(1, dao.delete(id))
        assertTrue(dao.getPoints(id).isEmpty())
        assertNull(dao.getById(id))
    }
}
