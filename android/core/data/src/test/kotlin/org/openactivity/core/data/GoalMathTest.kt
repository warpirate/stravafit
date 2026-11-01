// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.core.data.repository.GoalMath
import org.openactivity.core.database.entity.ActivityStatRow
import org.openactivity.model.SportType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class GoalMathTest {
    private val pune = ZoneId.of("Asia/Kolkata")

    private fun row(
        date: String,
        time: String = "08:00",
        sport: String = "RUN",
        km: Double = 5.0,
        zone: String = "Asia/Kolkata",
        movingMinutes: Long = 30,
        climb: Double = 20.0,
    ) = ActivityStatRow(
        id = 0,
        sport = sport,
        startEpochMillis = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.of(zone)).toInstant().toEpochMilli(),
        timeZoneId = zone,
        elapsedMillis = movingMinutes * 60_000 + 60_000,
        movingMillis = movingMinutes * 60_000,
        distanceMeters = km * 1000,
        elevationGainMeters = climb,
    )

    private fun now(date: String, time: String = "12:00", zone: ZoneId = pune) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `week periods start on the chosen first day`() {
        // Thursday 29 Oct 2026.
        assertEquals(d("2026-10-26") to d("2026-11-02"), GoalMath.periodOf(GoalPeriod.WEEK, d("2026-10-29"), DayOfWeek.MONDAY))
        assertEquals(d("2026-10-25") to d("2026-11-01"), GoalMath.periodOf(GoalPeriod.WEEK, d("2026-10-29"), DayOfWeek.SUNDAY))
        assertEquals(d("2026-10-24") to d("2026-10-31"), GoalMath.periodOf(GoalPeriod.WEEK, d("2026-10-29"), DayOfWeek.SATURDAY))
        // The first day itself opens a new week.
        assertEquals(d("2026-10-25") to d("2026-11-01"), GoalMath.periodOf(GoalPeriod.WEEK, d("2026-10-25"), DayOfWeek.SUNDAY))
    }

    @Test
    fun `month and year periods follow the calendar, leap years included`() {
        assertEquals(d("2028-02-01") to d("2028-03-01"), GoalMath.periodOf(GoalPeriod.MONTH, d("2028-02-29"), DayOfWeek.MONDAY))
        assertEquals(d("2026-12-01") to d("2027-01-01"), GoalMath.periodOf(GoalPeriod.MONTH, d("2026-12-31"), DayOfWeek.MONDAY))
        assertEquals(d("2027-01-01") to d("2028-01-01"), GoalMath.periodOf(GoalPeriod.YEAR, d("2027-01-01"), DayOfWeek.MONDAY))
    }

    @Test
    fun `a week across new year counts both years, a yearly goal only its own`() {
        // Friday 1 Jan 2027: the Monday week began on 28 Dec 2026.
        val rows = listOf(row("2026-12-28", km = 10.0), row("2026-12-31", km = 5.0), row("2027-01-01", km = 3.0))
        val week = GoalMath.progress(Goal(sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.DISTANCE, target = 30_000.0), rows, now("2027-01-01"), DayOfWeek.MONDAY)
        assertEquals(d("2026-12-28"), week.periodStart)
        assertEquals(18_000.0, week.current, 0.0)
        val year = GoalMath.progress(Goal(sport = null, period = GoalPeriod.YEAR, metric = GoalMetric.DISTANCE, target = 1_000_000.0), rows, now("2027-01-01"), DayOfWeek.MONDAY)
        assertEquals(3_000.0, year.current, 0.0)
        assertEquals(365, year.daysLeft)
    }

    @Test
    fun `an activity counts on the day it started in its own time zone`() {
        // Sunday 25 Oct, 23:30 in New York is already Monday 26 Oct in Pune: it stays in the week before.
        val late = row("2026-10-25", time = "23:30", zone = "America/New_York", km = 12.0)
        val goal = Goal(sport = SportType.RUN, period = GoalPeriod.WEEK, metric = GoalMetric.DISTANCE, target = 20_000.0)
        assertEquals(0.0, GoalMath.progress(goal, listOf(late), now("2026-10-27"), DayOfWeek.MONDAY).current, 0.0)
        assertEquals(12_000.0, GoalMath.progress(goal, listOf(late), now("2026-10-25"), DayOfWeek.MONDAY).current, 0.0)
        // With Sunday-first weeks it belongs to the week of 25 Oct.
        assertEquals(12_000.0, GoalMath.progress(goal, listOf(late), now("2026-10-27"), DayOfWeek.SUNDAY).current, 0.0)
    }

    @Test
    fun `each metric sums the right figure and only matching sports count`() {
        val rows = listOf(
            row("2026-10-26", sport = "RUN", km = 5.0, movingMinutes = 30, climb = 40.0),
            row("2026-10-27", sport = "TRAIL_RUN", km = 8.0, movingMinutes = 60, climb = 300.0),
            row("2026-10-28", sport = "RIDE", km = 40.0, movingMinutes = 90, climb = 500.0),
        )
        val now = now("2026-10-29")
        fun current(sport: SportType?, metric: GoalMetric) =
            GoalMath.progress(Goal(sport = sport, period = GoalPeriod.WEEK, metric = metric, target = 1.0), rows, now, DayOfWeek.MONDAY).current
        assertEquals(13_000.0, current(SportType.RUN, GoalMetric.DISTANCE), 0.0)
        assertEquals(5_400.0, current(SportType.RUN, GoalMetric.TIME), 0.0)
        assertEquals(840.0, current(null, GoalMetric.ELEVATION), 0.0)
        assertEquals(3.0, current(null, GoalMetric.COUNT), 0.0)
        assertEquals(1.0, current(SportType.RIDE, GoalMetric.COUNT), 0.0)
        assertEquals(0.0, current(SportType.SWIM, GoalMetric.COUNT), 0.0)
        assertTrue(GoalMath.counts(SportType.RIDE, SportType.GRAVEL_RIDE))
        assertFalse(GoalMath.counts(SportType.WALK, SportType.HIKE))
    }

    @Test
    fun `pace, remaining and days left come from the elapsed part of the period`() {
        // Thursday noon of a Monday week: 3.5 of 7 days gone.
        val goal = Goal(sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.DISTANCE, target = 40_000.0)
        val p = GoalMath.progress(goal, listOf(row("2026-10-26", km = 18.0)), now("2026-10-29"), DayOfWeek.MONDAY)
        assertEquals(0.5, p.elapsedFraction, 1e-9)
        assertEquals(20_000.0, p.expected, 1e-6)
        assertFalse(p.onPace)
        assertEquals(22_000.0, p.remaining, 0.0)
        assertEquals(4, p.daysLeft)
        assertEquals(0.45, p.fraction, 1e-9)
        val done = GoalMath.progress(goal, listOf(row("2026-10-26", km = 41.0)), now("2026-10-29"), DayOfWeek.MONDAY)
        assertTrue(done.reached)
        assertEquals(0.0, done.remaining, 0.0)
    }

    @Test
    fun `elapsed fraction uses real hours across a daylight saving change`() {
        // Europe/Berlin leaves summer time on Sunday 25 Oct 2026, so that week has 169 hours.
        val berlin = ZoneId.of("Europe/Berlin")
        val goal = Goal(sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.COUNT, target = 3.0)
        val p = GoalMath.progress(goal, emptyList(), now("2026-10-25", "23:00", berlin), DayOfWeek.MONDAY)
        assertEquals(168.0 / 169.0, p.elapsedFraction, 1e-9)
    }

    @Test
    fun `count goals are weekly only`() {
        assertTrue(GoalMetric.COUNT.allowedFor(GoalPeriod.WEEK))
        assertFalse(GoalMetric.COUNT.allowedFor(GoalPeriod.MONTH))
        assertTrue(runCatching { Goal(sport = null, period = GoalPeriod.YEAR, metric = GoalMetric.COUNT, target = 5.0) }.isFailure)
        assertTrue(runCatching { Goal(sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.TIME, target = 0.0) }.isFailure)
    }

    @Test
    fun `the streak counts consecutive active weeks and an empty current week does not break it`() {
        val today = d("2026-10-29") // Thursday
        val days = listOf("2026-10-14", "2026-10-20", "2026-10-21", "2026-09-30").map(::d)
        // Weeks of 12 and 19 Oct are active, 5 Oct is not; this week is still empty.
        assertEquals(WeeklyStreak(2, activeThisWeek = false), GoalMath.weeklyStreak(days, today, DayOfWeek.MONDAY))
        assertEquals(WeeklyStreak(3, activeThisWeek = true), GoalMath.weeklyStreak(days + d("2026-10-26"), today, DayOfWeek.MONDAY))
        // Two empty weeks in a row end it.
        assertEquals(WeeklyStreak.NONE, GoalMath.weeklyStreak(listOf(d("2026-10-14")), today, DayOfWeek.MONDAY))
        assertEquals(WeeklyStreak.NONE, GoalMath.weeklyStreak(emptyList(), today, DayOfWeek.MONDAY))
    }

    @Test
    fun `the streak follows the first day of the week and spans the new year`() {
        // Sunday 1 Nov 2026 with Sunday-first weeks: 25 Oct (Sun) and 31 Oct (Sat) are the same week.
        val days = listOf(d("2026-10-25"), d("2026-10-31"))
        assertEquals(WeeklyStreak(1, false), GoalMath.weeklyStreak(days, d("2026-11-01"), DayOfWeek.SUNDAY))
        // Monday-first, those are two different weeks.
        assertEquals(WeeklyStreak(2, false), GoalMath.weeklyStreak(days, d("2026-11-02"), DayOfWeek.MONDAY))
        // Over the new year: weeks of 21 Dec, 28 Dec and 4 Jan.
        val winter = listOf("2026-12-22", "2026-12-29", "2027-01-05").map(::d)
        assertEquals(WeeklyStreak(3, true), GoalMath.weeklyStreak(winter, d("2027-01-06"), DayOfWeek.MONDAY))
    }
}
