// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.mapper.FinishedActivityMapper
import org.openactivity.core.data.mapper.toDomain
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.PartOfDay
import org.openactivity.core.data.model.ResourceActivityTitles
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ActivityTitlesTest {
    private val titles = ResourceActivityTitles(ApplicationProvider.getApplicationContext())

    @Test
    fun `resource titles name every sport like the English fallback`() {
        val times = listOf(LocalTime.of(5, 0), LocalTime.of(12, 0), LocalTime.of(17, 0), LocalTime.of(21, 0), LocalTime.of(4, 59))
        for (sport in SportType.entries) {
            for (time in times) assertEquals(DefaultTitles.forActivity(sport, time), titles.forActivity(sport, time))
        }
    }

    @Test
    fun `part of day boundaries`() {
        assertEquals(PartOfDay.NIGHT, PartOfDay.of(LocalTime.of(4, 59)))
        assertEquals(PartOfDay.MORNING, PartOfDay.of(LocalTime.of(5, 0)))
        assertEquals(PartOfDay.MORNING, PartOfDay.of(LocalTime.of(11, 59)))
        assertEquals(PartOfDay.AFTERNOON, PartOfDay.of(LocalTime.of(12, 0)))
        assertEquals(PartOfDay.EVENING, PartOfDay.of(LocalTime.of(17, 0)))
        assertEquals(PartOfDay.EVENING, PartOfDay.of(LocalTime.of(20, 59)))
        assertEquals(PartOfDay.NIGHT, PartOfDay.of(LocalTime.of(21, 0)))
    }

    @Test
    fun `titles use the activity's own time zone`() {
        val start = Instant.parse("2025-06-03T06:00:00Z")
        assertEquals("Morning Run", titles.forActivity(SportType.RUN, start, ZoneId.of("Asia/Kolkata")))
        assertEquals("Night Run", titles.forActivity(SportType.RUN, start, ZoneId.of("America/Los_Angeles")))
        assertEquals("Afternoon Ride", titles.forActivity(SportType.RIDE, start, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `the saved title comes from the injected titles`() {
        val rows = FinishedActivityMapper.toRows(
            TestRecordings.steady(seconds = 600),
            NewActivity("u", timeZone = ZoneId.of("UTC")),
            UnitSystem.METRIC,
            nowMillis = 1L,
            titles = { sport, _ -> "Titled ${sport.apiName}" },
        )
        assertEquals("Titled Run", rows.activity.title)
    }

    @Test
    fun `an unreadable stored visibility reads back as only me`() {
        val rows = FinishedActivityMapper.toRows(
            TestRecordings.steady(seconds = 600),
            NewActivity("u", visibility = Visibility.EVERYONE),
            UnitSystem.METRIC,
            nowMillis = 1L,
        )
        assertEquals(Visibility.EVERYONE, rows.activity.toDomain().visibility)
        assertEquals(Visibility.ONLY_ME, rows.activity.copy(visibility = "PUBLIC_TO_ALIENS").toDomain().visibility)
        assertEquals(Visibility.ONLY_ME, rows.activity.copy(visibility = "").toDomain().visibility)
    }
}
