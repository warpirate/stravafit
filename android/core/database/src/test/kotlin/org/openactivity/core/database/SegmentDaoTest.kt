// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.entity.SegmentEffortEntity
import org.openactivity.core.database.entity.SegmentEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SegmentDaoTest {
    private lateinit var db: OpenActivityDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun segment(uuid: String, starred: Boolean = false, at: Long = 1) = SegmentEntity(
        uuid = uuid, name = "Seg $uuid", sport = "RUN", polyline = "_p~iF~ps|U_ulLnnqC", elevations = "",
        distanceMeters = 500.0, elevationGainMeters = 10.0, averageGradePercent = 2.0,
        startLatitude = 38.5, startLongitude = -120.2, endLatitude = 40.7, endLongitude = -120.95,
        bboxSouth = 38.5, bboxWest = -120.95, bboxNorth = 40.7, bboxEast = -120.2,
        sourceActivityId = null, starred = starred, createdAtMillis = at,
    )

    private fun effort(segmentId: Long, activityId: Long, elapsed: Long, start: Int = 0) = SegmentEffortEntity(
        segmentId = segmentId, activityId = activityId, startIndex = start, endIndex = start + 10, startOffsetMillis = 0,
        startEpochMillis = 1_700_000_000_000L + activityId, year = 2023, elapsedMillis = elapsed, movingMillis = elapsed,
        distanceMeters = 500.0, averageSpeedMps = 3.0, averageHeartRate = null, maxHeartRate = null, averagePower = null,
        averageCadence = null, rankAllTime = null, rankYear = null, isPersonalRecord = false, createdAtMillis = 1,
    )

    @Test
    fun `summaries carry the best time and list starred first`() = runTest {
        val dao = db.segmentDao()
        val a = db.activityDao().insertActivity(TestEntities.activity(uuid = "a"))
        val b = db.activityDao().insertActivity(TestEntities.activity(uuid = "b"))
        val plain = dao.insert(segment("p", at = 5))
        val starred = dao.insert(segment("s", starred = true, at = 1))
        dao.insertEfforts(listOf(effort(plain, a, 90_000), effort(plain, b, 80_000)))

        val rows = dao.observeSummaries().first()
        assertEquals(listOf(starred, plain), rows.map { it.segment.id })
        assertEquals(2, rows[1].effortCount)
        assertEquals(80_000L, rows[1].bestElapsedMillis)
        assertEquals(b, rows[1].bestActivityId)
        assertNull(rows[0].bestElapsedMillis)
        assertEquals(listOf(starred), dao.getStarred(listOf("RUN")).map { it.id })
    }

    @Test
    fun `an effort stored twice is kept once and deleting an activity removes its efforts`() = runTest {
        val dao = db.segmentDao()
        val a = db.activityDao().insertActivity(TestEntities.activity(uuid = "a"))
        val s = dao.insert(segment("x"))
        assertTrue(dao.insertEfforts(listOf(effort(s, a, 100_000))).first() > 0)
        assertEquals(-1L, dao.insertEfforts(listOf(effort(s, a, 50_000))).first())
        assertEquals(1, dao.observeEffortsOfActivity(a).first().size)
        assertEquals("Seg x", dao.observeEffortsOfActivity(a).first().first().segmentName)

        db.activityDao().delete(a)
        assertEquals(0, dao.getEffortsOfSegment(s).size)
        assertEquals(1, dao.count())
    }

    @Test
    fun `best efforts pick each segment's fastest`() = runTest {
        val dao = db.segmentDao()
        val a = db.activityDao().insertActivity(TestEntities.activity(uuid = "a"))
        val s = dao.insert(segment("x"))
        dao.insertEfforts(listOf(effort(s, a, 100_000, start = 0), effort(s, a, 70_000, start = 50)))
        assertEquals(listOf(70_000L), dao.observeBestEfforts().first().map { it.effort.elapsedMillis })
        dao.replaceEffortsOfSegment(s, listOf(effort(s, a, 60_000)))
        assertEquals(listOf(60_000L), dao.getEffortsOfSegment(s).map { it.elapsedMillis })
    }

    @Test
    fun `candidates are activities of the sport whose box overlaps`() = runTest {
        db.activityDao().insertActivity(TestEntities.activity(uuid = "run"))
        db.activityDao().insertActivity(TestEntities.activity(uuid = "ride", sport = "RIDE"))
        val dao = db.segmentDao()
        // TestEntities boxes span 38.5..40.7 N; west/east are stored as given, so query the same order.
        val hits = dao.candidateActivities(listOf("RUN"), south = 39.0, west = -121.0, north = 39.5, east = 0.0)
        assertEquals(1, hits.size)
        assertEquals("RUN", hits.single().sport)
        assertEquals(0, dao.candidateActivities(listOf("RUN"), south = 50.0, west = -121.0, north = 51.0, east = 0.0).size)
    }
}
