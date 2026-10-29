// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openactivity.core.data.repository.StatsAggregator
import org.openactivity.core.database.entity.ActivityStatRow
import org.openactivity.model.SportType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes

class StatsAggregatorTest {
    private fun row(date: String, time: String = "08:00", sport: String = "RUN", km: Double = 5.0, zone: String = "UTC") =
        ActivityStatRow(
            id = 0,
            sport = sport,
            startEpochMillis = LocalDate.parse(date).atTime(java.time.LocalTime.parse(time))
                .atZone(ZoneId.of(zone)).toInstant().toEpochMilli(),
            timeZoneId = zone,
            elapsedMillis = 30.minutes.inWholeMilliseconds,
            movingMillis = 25.minutes.inWholeMilliseconds,
            distanceMeters = km * 1000,
            elevationGainMeters = 10.0,
        )

    @Test
    fun `weeks are dense, oldest first, and start on the given day`() {
        val rows = listOf(row("2026-10-26"), row("2026-10-28", km = 10.0), row("2026-10-12"))
        val weeks = StatsAggregator.weekly(rows, 4, LocalDate.parse("2026-10-29"), DayOfWeek.MONDAY)
        assertEquals(
            listOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26").map(LocalDate::parse),
            weeks.map { it.weekStart },
        )
        assertEquals(listOf(0, 1, 0, 2), weeks.map { it.totals.count })
        assertEquals(15_000.0, weeks.last().totals.distanceMeters, 0.0)
        assertEquals(50.minutes, weeks.last().totals.movingTime)
    }

    @Test
    fun `sunday-first weeks bucket differently`() {
        val weeks = StatsAggregator.weekly(listOf(row("2026-10-25")), 1, LocalDate.parse("2026-10-29"), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-10-25"), weeks.single().weekStart)
        assertEquals(1, weeks.single().totals.count)
    }

    @Test
    fun `an activity counts on its own local day`() {
        // 23:30 in Tokyo on Sunday is 14:30 UTC Sunday; 00:30 Monday in Tokyo is still Sunday in UTC.
        val mondayInTokyo = row("2026-10-26", time = "00:30", zone = "Asia/Tokyo")
        val weeks = StatsAggregator.weekly(listOf(mondayInTokyo), 1, LocalDate.parse("2026-10-29"), DayOfWeek.MONDAY)
        assertEquals(1, weeks.single().totals.count)
    }

    @Test
    fun `year totals split by sport`() {
        val rows = listOf(row("2026-01-01"), row("2026-06-01", sport = "RIDE", km = 40.0), row("2025-12-31"), row("2026-02-02", sport = "BOGUS"))
        val year = StatsAggregator.year(rows, 2026)
        assertEquals(3, year.totals.count)
        assertEquals(setOf(SportType.RUN, SportType.RIDE, SportType.OTHER), year.bySport.keys)
        assertEquals(40_000.0, year.bySport.getValue(SportType.RIDE).distanceMeters, 0.0)
    }
}
