// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

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
import org.openactivity.core.data.repository.LocalSegmentRepository
import org.openactivity.core.data.repository.SegmentRanking
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Segment ranks (pure) and the Room-backed repository: create, match, freeze places, re-match. */
@RunWith(RobolectricTestRunner::class)
class SegmentRepositoryTest {
    private lateinit var db: OpenActivityDatabase
    private lateinit var segments: LocalSegmentRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-03T06:00:00Z"), ZoneOffset.UTC)
    private val origin = LatLng(47.37, 8.54)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        segments = LocalSegmentRepository(db.segmentDao(), db.activityDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    // ---------------------------------------------------------------------------------------- ranking

    @Test
    fun `places are frozen against earlier efforts, all-time and yearly`() {
        assertEquals(SegmentRanking.Places(null, null, false), SegmentRanking.place(emptyList(), 100, 2026))
        val earlier = listOf(SegmentRanking.Entry(100, 2025), SegmentRanking.Entry(90, 2025), SegmentRanking.Entry(120, 2026))
        assertEquals(SegmentRanking.Places(1, 1, true), SegmentRanking.place(earlier, 80, 2026))
        // Slower than 2025's best two but the fastest this year: a yearly PR.
        assertEquals(SegmentRanking.Places(3, 1, false), SegmentRanking.place(earlier, 110, 2026))
        // A tie keeps the earlier effort ahead.
        assertEquals(SegmentRanking.Places(2, 2, false), SegmentRanking.place(earlier, 90, 2025))
    }

    @Test
    fun `efforts saved in order rank against the ones before them`() {
        val places = SegmentRanking.placeInOrder(
            emptyList(),
            listOf(SegmentRanking.Entry(100, 2026), SegmentRanking.Entry(90, 2026), SegmentRanking.Entry(95, 2026)),
        )
        assertEquals(listOf(null, 1, 2), places.map { it.rankAllTime })
        assertEquals(listOf(false, true, false), places.map { it.isPersonalRecord })
    }

    // ------------------------------------------------------------------------------------- repository

    @Test
    fun `a segment created from an activity matches it and a faster later run is a PR`() = runTest {
        val first = insertRun("a", start = Instant.parse("2026-10-01T07:00:00Z"), metersPerSecond = 4.0)
        val id = segments.create(first, startIndex = 50, endIndex = 175, name = "  River   sprint ", sport = SportType.RUN)
        val segment = segments.observeSegment(id).first()!!
        assertEquals("River sprint", segment.name)
        assertTrue(segment.starred)
        assertEquals(500.0, segment.distanceMeters, 3.0)
        assertTrue(segment.elevations != null)

        assertEquals(1, segments.rematchSegment(id))
        val firstEffort = segments.observeEffortsOfActivity(first).first().single()
        assertNull("the first effort has nothing to beat", firstEffort.rankAllTime)
        assertNull(firstEffort.medalRank)

        val faster = insertRun("b", start = Instant.parse("2026-10-08T07:00:00Z"), metersPerSecond = 5.0)
        val added = segments.matchActivity(faster)
        assertEquals(1, added.size)
        assertTrue(added.single().isPersonalRecord)
        assertEquals(1, added.single().medalRank)
        assertEquals(100.0, added.single().elapsed.inWholeMilliseconds / 1000.0, 1.0)
        assertTrue("matching twice adds nothing", segments.matchActivity(faster).isEmpty())

        val slower = insertRun("c", start = Instant.parse("2026-10-09T07:00:00Z"), metersPerSecond = 3.0)
        val third = segments.matchActivity(slower).single()
        assertEquals(3, third.rankAllTime)
        assertFalse(third.isPersonalRecord)

        val summary = segments.observeSegments().first().single()
        assertEquals(3, summary.effortCount)
        assertEquals(faster, summary.bestActivityId)
        assertEquals(listOf(faster), segments.observePersonalRecords().first().map { it.activityId })
        assertEquals(100.0, segments.liveSegments(SportType.TRAIL_RUN).single().prElapsedMillis!! / 1000.0, 1.0)
        assertTrue("rides do not see run segments", segments.liveSegments(SportType.RIDE).isEmpty())
    }

    @Test
    fun `a re-match replays activities in date order`() = runTest {
        val late = insertRun("late", start = Instant.parse("2026-06-01T07:00:00Z"), metersPerSecond = 5.0)
        val early = insertRun("early", start = Instant.parse("2025-06-01T07:00:00Z"), metersPerSecond = 4.0)
        val id = segments.create(late, 40, 140, "Sprint", SportType.RUN)
        assertEquals(2, segments.rematchSegment(id))
        val efforts = segments.observeEffortsOfSegment(id).first()
        assertEquals(listOf(late, early), efforts.map { it.activityId })
        // The 2026 run beat the 2025 one when it happened.
        assertTrue(efforts.first().isPersonalRecord)
        assertNull(efforts.first().rankYear)
        assertEquals(2025, efforts.last().year)
    }

    @Test
    fun `too short a range is refused and star, rename and delete work`() = runTest {
        val a = insertRun("a", start = Instant.parse("2026-10-01T07:00:00Z"), metersPerSecond = 5.0)
        val refused = runCatching { segments.create(a, 40, 70, "Short", SportType.RUN) }
        assertTrue(refused.exceptionOrNull() is IllegalArgumentException)

        val id = segments.create(a, 40, 140, "Sprint", SportType.RUN)
        segments.setStarred(id, false)
        assertFalse(segments.observeSegment(id).first()!!.starred)
        assertFalse(segments.rename(id, "   "))
        assertTrue(segments.rename(id, "Sprint 2"))
        assertEquals("Sprint 2", segments.observeSegment(id).first()!!.name)
        assertTrue(segments.delete(id))
        assertNull(segments.observeSegment(id).first())
    }

    /**
     * A run due north from 200 m before [origin] to 700 m after it, a point every second, so point `i` is
     * `i * metersPerSecond - 200` m from the origin (the segment created in these tests spans 0 to 500 m).
     */
    private suspend fun insertRun(uuid: String, start: Instant, metersPerSecond: Double): Long {
        val count = (900 / metersPerSecond).toInt() + 1
        val step = 900.0 / (count - 1)
        val begin = Spherical.destination(origin, 180.0, 200.0)
        val points = (0 until count).map { i ->
            val p = Spherical.destination(begin, 0.0, i * step)
            ActivityPointEntity(
                activityId = 0, seq = i, latitude = p.latitude, longitude = p.longitude, elevationMeters = 400.0 + i * step * 0.02,
                epochMillis = start.toEpochMilli() + i * 1000L, offsetMillis = i * 1000L, heartRate = 150, cadence = 88.0,
                powerWatts = null, speedMps = metersPerSecond, accuracyMeters = 4.0, distanceMeters = i * step, paused = false, segment = 0,
            )
        }
        val activity = ActivityEntity(
            uuid = uuid, sport = "RUN", title = "Run $uuid", description = "", visibility = "EVERYONE", perceivedExertion = null,
            startEpochMillis = start.toEpochMilli(), timeZoneId = "UTC", elapsedMillis = count * 1000L, movingMillis = count * 1000L,
            distanceMeters = 900.0, elevationGainMeters = 18.0, elevationLossMeters = 0.0, altitudeSource = "GPS",
            averageSpeedMps = metersPerSecond, maxSpeedMps = metersPerSecond, averageGradeAdjustedSpeedMps = null,
            averageHeartRate = 150.0, maxHeartRate = 150, averagePower = null, maxPower = null, averageCadence = 88.0,
            autoPauseCount = 0, polyline = null, bboxSouth = points.minOf { it.latitude }, bboxWest = points.minOf { it.longitude },
            bboxNorth = points.maxOf { it.latitude }, bboxEast = points.maxOf { it.longitude }, privacyHideStartMeters = 0.0,
            privacyHideEndMeters = 0.0, flags = "", routeId = null, createdAtMillis = 0, updatedAtMillis = 0,
        )
        return db.activityDao().insertActivityWithChildren(activity, points, emptyList(), emptyList(), emptyList())
    }
}
