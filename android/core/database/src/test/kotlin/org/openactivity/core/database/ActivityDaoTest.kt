// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.entity.ActivityMetadataEntity
import org.openactivity.core.database.entity.BestEffortEntity
import org.openactivity.core.database.entity.LapEntity
import org.openactivity.core.database.entity.SplitEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityDaoTest {
    private lateinit var db: OpenActivityDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private val dao get() = db.activityDao()

    private fun lap(i: Int) = LapEntity(0, i, i * 600_000L, 600_000L, 590_000L, 2000.0, 10.0, "MANUAL")

    private fun split(unit: String, i: Int) = SplitEntity(0, unit, i, 1000.0, 300_000L, 295_000L, 1.5, false)

    @Test
    fun `insert with children assigns the new id to every child row`() = runTest {
        val id = dao.insertActivityWithChildren(
            TestEntities.activity(),
            points = (0 until 50).map { TestEntities.point(it, activityId = 999) },
            laps = listOf(lap(0), lap(1)),
            splits = listOf(split("METRIC", 0), split("IMPERIAL", 0)),
            bestEfforts = listOf(BestEffortEntity(0, "DISTANCE", 1000.0, 290.0, 12.0)),
        )
        assertEquals(50, dao.getPoints(id).size)
        assertTrue(dao.getPoints(id).all { it.activityId == id })
        assertEquals(listOf(0, 1), dao.observeLaps(id).first().map { it.index })
        assertEquals(2, dao.observeSplits(id).first().size)
        assertEquals(1, dao.observeBestEfforts(id).first().size)
        assertEquals("uuid-1", dao.getById(id)?.uuid)
    }

    @Test
    fun `activities are listed newest first`() = runTest {
        dao.insertActivity(TestEntities.activity(uuid = "a", start = 1000L))
        dao.insertActivity(TestEntities.activity(uuid = "b", start = 3000L))
        dao.insertActivity(TestEntities.activity(uuid = "c", start = 2000L))
        assertEquals(listOf("b", "c", "a"), dao.observeAll().first().map { it.uuid })
        assertEquals(3, dao.observeCount().first())
    }

    @Test
    fun `duplicate uuid is rejected`() = runTest {
        dao.insertActivity(TestEntities.activity(uuid = "same"))
        try {
            dao.insertActivity(TestEntities.activity(uuid = "same"))
            fail("expected a constraint violation")
        } catch (expected: SQLiteConstraintException) {
            // The uuid is the idempotency key: the same recording can never be saved twice.
        }
    }

    @Test
    fun `metadata update leaves metrics untouched`() = runTest {
        val id = dao.insertActivity(TestEntities.activity())
        val changed = dao.updateMetadata(
            ActivityMetadataEntity(id, "TRAIL_RUN", "Hill reps", "Felt strong", "ONLY_ME", 7, 42L),
        )
        assertEquals(1, changed)
        val row = dao.getById(id)!!
        assertEquals("Hill reps", row.title)
        assertEquals("TRAIL_RUN", row.sport)
        assertEquals(7, row.perceivedExertion)
        assertEquals("ONLY_ME", row.visibility)
        assertEquals(5_000.0, row.distanceMeters, 0.0)
        assertEquals(42L, row.updatedAtMillis)
    }

    @Test
    fun `delete cascades to child rows`() = runTest {
        val id = dao.insertActivityWithChildren(
            TestEntities.activity(),
            points = (0 until 10).map { TestEntities.point(it) },
            laps = listOf(lap(0)),
            splits = listOf(split("METRIC", 0)),
            bestEfforts = listOf(BestEffortEntity(0, "DISTANCE", 400.0, 90.0, 0.0)),
        )
        assertEquals(1, dao.delete(id))
        assertNull(dao.getById(id))
        assertTrue(dao.getPoints(id).isEmpty())
        assertTrue(dao.observeLaps(id).first().isEmpty())
        assertTrue(dao.observeSplits(id).first().isEmpty())
        assertTrue(dao.observeBestEfforts(id).first().isEmpty())
    }

    @Test
    fun `stat rows start at the given time`() = runTest {
        dao.insertActivity(TestEntities.activity(uuid = "old", start = 1000L))
        dao.insertActivity(TestEntities.activity(uuid = "new", start = 5000L))
        val rows = dao.observeStatRows(fromEpochMillis = 2000L).first()
        assertEquals(1, rows.size)
        assertEquals(5000L, rows.single().startEpochMillis)
    }

    @Test
    fun `best efforts come best first per target and filter by sport`() = runTest {
        val slow = dao.insertActivity(TestEntities.activity(uuid = "slow"))
        val fast = dao.insertActivity(TestEntities.activity(uuid = "fast"))
        val ride = dao.insertActivity(TestEntities.activity(uuid = "ride", sport = "RIDE"))
        dao.insertBestEfforts(
            listOf(
                BestEffortEntity(slow, "DISTANCE", 1000.0, 300.0, 0.0),
                BestEffortEntity(fast, "DISTANCE", 1000.0, 280.0, 0.0),
                BestEffortEntity(fast, "DISTANCE", 5000.0, 1500.0, 0.0),
                BestEffortEntity(ride, "DISTANCE", 1000.0, 100.0, 0.0),
                BestEffortEntity(ride, "DURATION", 60.0, 600.0, 0.0),
                BestEffortEntity(slow, "DURATION", 60.0, 300.0, 0.0),
            ),
        )
        val runs = dao.observeBestEfforts("DISTANCE", listOf("RUN", "TRAIL_RUN")).first()
        assertEquals(listOf(1000.0 to 280.0, 1000.0 to 300.0, 5000.0 to 1500.0), runs.map { it.target to it.value })
        val durations = dao.observeBestEfforts("DURATION", listOf("RUN", "RIDE")).first()
        assertEquals(listOf(600.0, 300.0), durations.map { it.value })
    }
}
