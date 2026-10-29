// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.mapper.FinishedActivityMapper
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.NewActivity
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.polyline.PolylineCodec
import java.time.LocalTime
import java.time.ZoneId

class FinishedActivityMapperTest {
    private val zurich = ZoneId.of("Europe/Zurich")

    @Test
    fun `rows carry the engine totals and the save options`() {
        val finished = TestRecordings.steady(seconds = 1200)
        val rows = FinishedActivityMapper.toRows(
            finished,
            NewActivity(uuid = "u1", visibility = Visibility.ONLY_ME, hideStartMeters = 200.0, hideEndMeters = 150.0, timeZone = zurich),
            UnitSystem.METRIC,
            nowMillis = 1L,
        )
        val a = rows.activity
        assertEquals("u1", a.uuid)
        assertEquals("RUN", a.sport)
        assertEquals("ONLY_ME", a.visibility)
        assertEquals(finished.distanceMeters, a.distanceMeters, 0.0)
        assertEquals(finished.movingTime.inWholeMilliseconds, a.movingMillis)
        assertEquals(TestRecordings.START_WALL_MILLIS, a.startEpochMillis)
        assertEquals("Europe/Zurich", a.timeZoneId)
        assertEquals(200.0, a.privacyHideStartMeters, 0.0)
        assertEquals(150.0, a.privacyHideEndMeters, 0.0)
        assertEquals(finished.points.size, rows.points.size)
        assertEquals((rows.points.indices).toList(), rows.points.map { it.seq })
        assertEquals(finished.laps.size, rows.laps.size)
    }

    @Test
    fun `polyline and bounds cover the track`() {
        val finished = TestRecordings.steady(seconds = 300)
        val rows = FinishedActivityMapper.toRows(finished, NewActivity("u"), UnitSystem.METRIC, 1L)
        val decoded = PolylineCodec.decode(rows.activity.polyline!!)
        assertTrue("straight line simplifies to a few points", decoded.size in 2..5)
        assertEquals(finished.points.first().latitude, decoded.first().latitude, 1e-5)
        assertNotNull(rows.activity.bboxSouth)
        assertTrue(rows.activity.bboxEast!! > rows.activity.bboxWest!!)
    }

    @Test
    fun `splits are stored for both unit systems`() {
        val finished = TestRecordings.steady(seconds = 1200, speedMps = 3.0) // about 3.6 km
        val rows = FinishedActivityMapper.toRows(finished, NewActivity("u"), UnitSystem.METRIC, 1L)
        val metric = rows.splits.filter { it.unitSystem == "METRIC" }
        val imperial = rows.splits.filter { it.unitSystem == "IMPERIAL" }
        assertEquals(finished.splits.size, metric.size)
        assertEquals(4, metric.size) // 3 full km + partial
        assertEquals(3, imperial.size) // 2 full miles + partial
        assertTrue(imperial.last().isPartial)
        assertEquals(1609.344, imperial.first().distanceMeters, 1.0)
    }

    @Test
    fun `best efforts are stored by kind`() {
        val finished = TestRecordings.steady(seconds = 1200)
        val rows = FinishedActivityMapper.toRows(finished, NewActivity("u"), UnitSystem.METRIC, 1L)
        val distance = rows.bestEfforts.filter { it.kind == EffortKind.DISTANCE.name }
        assertEquals(finished.bestEfforts.size, distance.size)
        val km = distance.single { it.target == 1000.0 }
        assertEquals(1000.0 / 3.0, km.value, 5.0)
    }

    @Test
    fun `default title uses local part of day and an explicit title wins`() {
        val finished = TestRecordings.steady(sport = SportType.TRAIL_RUN, seconds = 60)
        // 22:13 UTC is 23:13 in Zurich: night.
        val rows = FinishedActivityMapper.toRows(finished, NewActivity("u", timeZone = zurich), UnitSystem.METRIC, 1L)
        assertEquals("Night Trail Run", rows.activity.title)
        val named = FinishedActivityMapper.toRows(finished, NewActivity("u", title = "Track day"), UnitSystem.METRIC, 1L)
        assertEquals("Track day", named.activity.title)
    }

    @Test
    fun `titles and sport names`() {
        assertEquals("Morning Ride", DefaultTitles.forActivity(SportType.RIDE, LocalTime.of(7, 0)))
        assertEquals("Afternoon Walk", DefaultTitles.forActivity(SportType.WALK, LocalTime.of(12, 0)))
        assertEquals("Evening Mountain Bike Ride", DefaultTitles.forActivity(SportType.MOUNTAIN_BIKE_RIDE, LocalTime.of(20, 59)))
        assertEquals("E-Bike Ride", DefaultTitles.sportName(SportType.E_BIKE_RIDE))
    }

    @Test
    fun `an activity without fixes has no polyline`() {
        val finished = TestRecordings.steady(seconds = 0)
        val rows = FinishedActivityMapper.toRows(finished, NewActivity("u"), UnitSystem.METRIC, 5_000L)
        assertNull(rows.activity.polyline)
        assertNull(rows.activity.bboxSouth)
        assertTrue(rows.splits.isEmpty())
    }
}
