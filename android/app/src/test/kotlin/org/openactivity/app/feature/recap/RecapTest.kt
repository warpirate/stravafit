// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.app.feature.notifications.InboxDestination
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.notifications.InboxText
import org.openactivity.app.testing.savedActivity
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.InboxNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class RecapMathTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val monday = LocalDate.of(2026, 10, 26) // ISO week 44

    private fun at(local: String, zone: ZoneId = kolkata) = ZonedDateTime.parse("${local}[${zone.id}]").toInstant()

    @Test
    fun `last completed week is the ISO week before this one`() {
        // Monday 08:00: the week that just ended.
        assertEquals(monday, RecapMath.lastCompletedWeek(ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, kolkata), DayOfWeek.MONDAY))
        // Sunday night still belongs to the running week: the recap is of the week before it.
        assertEquals(monday.minusWeeks(1), RecapMath.lastCompletedWeek(ZonedDateTime.of(2026, 11, 1, 23, 59, 0, 0, kolkata), DayOfWeek.MONDAY))
        // Weeks across a new year (ISO week 1 of 2027 starts on Monday 4 January).
        assertEquals(LocalDate.of(2026, 12, 28), RecapMath.lastCompletedWeek(ZonedDateTime.of(2027, 1, 4, 7, 0, 0, 0, kolkata), DayOfWeek.MONDAY))
    }

    @Test
    fun `a Sunday week start is honoured`() {
        assertEquals(LocalDate.of(2026, 10, 25), RecapMath.lastCompletedWeek(ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, kolkata), DayOfWeek.SUNDAY))
    }

    @Test
    fun `an activity counts on the day it started in its own zone`() {
        // Sunday 23:30 in Berlin is Monday 04:00 in India: it stays in the Berlin Sunday's week.
        val lateSundayAbroad = savedActivity(1, start = at("2026-11-01T23:30:00+01:00", berlin)).copy(timeZone = berlin)
        assertEquals(LocalDate.of(2026, 11, 1), RecapMath.localDate(lateSundayAbroad))
        assertEquals(listOf(lateSundayAbroad), RecapMath.inWeek(listOf(lateSundayAbroad), monday))
        assertTrue(RecapMath.inWeek(listOf(lateSundayAbroad), monday.plusWeeks(1)).isEmpty())
    }

    @Test
    fun `week edges are Monday 00 00 inclusive to the next Monday exclusive`() {
        val first = savedActivity(1, start = at("2026-10-26T00:00:00+05:30"))
        val last = savedActivity(2, start = at("2026-11-01T23:59:00+05:30"))
        val next = savedActivity(3, start = at("2026-11-02T00:00:00+05:30"))
        val before = savedActivity(4, start = at("2026-10-25T23:59:00+05:30"))
        assertEquals(listOf(1L, 2L), RecapMath.inWeek(listOf(next, last, before, first), monday).map { it.id })
    }

    @Test
    fun `a daylight saving change does not move the week`() {
        // Europe falls back on Sunday 25 October 2026; both ends of that week stay in it.
        val zone = berlin
        val weekStart = LocalDate.of(2026, 10, 19)
        val sundayAfterChange = savedActivity(1, start = at("2026-10-25T22:00:00+01:00", zone)).copy(timeZone = zone)
        val mondayBefore = savedActivity(2, start = at("2026-10-19T00:30:00+02:00", zone)).copy(timeZone = zone)
        assertEquals(2, RecapMath.inWeek(listOf(sundayAfterChange, mondayBefore), weekStart).size)
    }

    @Test
    fun `recap sums the week against the week before`() {
        val all = listOf(
            savedActivity(1, start = at("2026-10-27T07:00:00+05:30"), distanceMeters = 10_000.0, movingSeconds = 3_000),
            savedActivity(2, start = at("2026-10-30T07:00:00+05:30"), distanceMeters = 14_000.0, movingSeconds = 4_200, title = "Long Run"),
            savedActivity(3, start = at("2026-10-20T07:00:00+05:30"), distanceMeters = 20_000.0, movingSeconds = 6_000),
        )
        val recap = RecapMath.recap(all, emptyList(), monday)
        assertEquals(2, recap.totals.count)
        assertEquals(24_000.0, recap.totals.distanceMeters, 0.0)
        assertEquals(1, recap.previous.count)
        assertEquals(20, recap.distanceChange)
        assertEquals(20, recap.timeChange)
        assertEquals("Long Run", recap.longest?.title)
        assertEquals(LocalDate.of(2026, 11, 1), recap.weekEnd)
    }

    @Test
    fun `no week before means no percentage`() {
        val recap = RecapMath.recap(listOf(savedActivity(1, start = at("2026-10-27T07:00:00+05:30"))), emptyList(), monday)
        assertNull(recap.distanceChange)
        assertEquals("Nothing the week before. Good to be back.", PeriodCards.comparison(recap))
    }

    @Test
    fun `longest falls back to moving time without distances`() {
        val yoga = savedActivity(1, sport = SportType.YOGA, distanceMeters = 0.0, movingSeconds = 3_600, track = null)
        val gym = savedActivity(2, sport = SportType.WEIGHT_TRAINING, distanceMeters = 0.0, movingSeconds = 1_800, track = null)
        assertEquals(1L, RecapMath.longest(listOf(gym, yoga))?.id)
    }

    @Test
    fun `weekly goals are judged on the recap week only`() {
        val run = savedActivity(1, start = at("2026-10-27T07:00:00+05:30"), distanceMeters = 31_000.0)
        val ride = savedActivity(2, sport = SportType.RIDE, start = at("2026-10-28T07:00:00+05:30"), distanceMeters = 40_000.0)
        val goals = listOf(
            Goal(1, SportType.RUN, GoalPeriod.WEEK, GoalMetric.DISTANCE, 30_000.0),
            Goal(2, null, GoalPeriod.WEEK, GoalMetric.COUNT, 3.0),
            Goal(3, null, GoalPeriod.MONTH, GoalMetric.DISTANCE, 100_000.0),
        )
        val results = RecapMath.recap(listOf(run, ride), goals, monday).goals
        assertEquals(listOf(1L, 2L), results.map { it.goal.id })
        assertTrue(results[0].reached)
        assertEquals(31_000.0, results[0].achieved, 0.0)
        assertFalse(results[1].reached)
        assertEquals(2.0, results[1].achieved, 0.0)
    }

    @Test
    fun `month boundaries use the activity's own zone`() {
        val lastOfOctoberAbroad = savedActivity(1, start = at("2026-10-31T23:00:00+01:00", berlin)).copy(timeZone = berlin)
        assertEquals(1, RecapMath.inMonth(listOf(lastOfOctoberAbroad), YearMonth.of(2026, 10)).size)
        assertTrue(RecapMath.inMonth(listOf(lastOfOctoberAbroad), YearMonth.of(2026, 11)).isEmpty())
    }
}

class PeriodCardsTest {
    private val formatter = UnitFormatter(UnitSystem.METRIC, Locale.UK)
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `month card has the month's totals and one shape per activity with a route`() {
        val all = listOf(
            savedActivity(1, start = Instant.parse("2026-10-03T01:30:00Z"), distanceMeters = 5_000.0, movingSeconds = 1_500),
            savedActivity(2, start = Instant.parse("2026-10-10T01:30:00Z"), distanceMeters = 7_500.0, movingSeconds = 2_400),
            savedActivity(3, sport = SportType.YOGA, start = Instant.parse("2026-10-12T01:30:00Z"), distanceMeters = 0.0, movingSeconds = 3_600, track = null, elevationGain = 0.0),
            savedActivity(4, start = Instant.parse("2026-11-01T01:30:00Z")), // November
        )
        val card = PeriodCards.month(october, all, formatter, Locale.UK)
        assertEquals("October 2026", card.title)
        assertEquals("Month in sport", card.kicker)
        assertEquals(listOf("Activities", "Distance", "Time", "Elevation"), card.stats.map { it.label })
        assertEquals("3", card.stats[0].value)
        assertEquals("12.50", card.stats[1].value)
        assertEquals("km", card.stats[1].unit)
        assertEquals("2h 5m", card.stats[2].value)
        assertEquals(2, card.routes.size)
        assertEquals(1, card.withoutRoute)
    }

    @Test
    fun `a month without activities is an honest empty card`() {
        val card = PeriodCards.month(october, emptyList(), formatter, Locale.UK)
        assertEquals(listOf("Activities", "Time"), card.stats.map { it.label })
        assertEquals("0", card.stats[0].value)
        assertTrue(card.routes.isEmpty())
        assertEquals(0, card.withoutRoute)
    }

    @Test
    fun `routes lose the hidden start and finish`() {
        val activity = savedActivity(1)
        val full = routeexport.Polyline.decode(activity.polyline!!)
        val drawn = PeriodCards.route(activity)
        assertTrue(drawn.size < full.size)
        assertTrue(drawn.none { it == full.first() || it == full.last() })
        assertTrue(PeriodCards.route(savedActivity(2, track = null)).isEmpty())
    }

    @Test
    fun `week range shows the year only across a new year`() {
        assertEquals("26 Oct – 1 Nov", PeriodCards.weekRange(LocalDate.of(2026, 10, 26), Locale.UK))
        assertEquals("28 Dec 2026 – 3 Jan 2027", PeriodCards.weekRange(LocalDate.of(2026, 12, 28), Locale.UK))
    }

    @Test
    fun `comparison wording`() {
        fun recap(now: Double, before: Double) = RecapMath.recap(
            listOf(
                savedActivity(1, start = Instant.parse("2026-10-27T01:30:00Z"), distanceMeters = now),
                savedActivity(2, start = Instant.parse("2026-10-20T01:30:00Z"), distanceMeters = before),
            ),
            emptyList(),
            LocalDate.of(2026, 10, 26),
        )
        assertEquals("12% more distance than the week before", PeriodCards.comparison(recap(11_200.0, 10_000.0)))
        assertEquals("25% less distance than the week before", PeriodCards.comparison(recap(7_500.0, 10_000.0)))
        assertEquals("Same distance as the week before", PeriodCards.comparison(recap(10_000.0, 10_000.0)))
    }
}

class RecapNotificationTest {
    private val formatter = UnitFormatter(UnitSystem.METRIC, Locale.UK)
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun `the recap entry sums the week and opens the recap screen`() {
        val recap = RecapMath.recap(
            listOf(
                savedActivity(1, start = Instant.parse("2026-10-27T01:30:00Z"), distanceMeters = 10_000.0, movingSeconds = 3_000, elevationGain = 40.0),
                savedActivity(2, start = Instant.parse("2026-10-31T01:30:00Z"), distanceMeters = 14_200.0, movingSeconds = 4_500, elevationGain = 60.0, title = "Sunday Long Run"),
                savedActivity(3, start = Instant.parse("2026-10-21T01:30:00Z"), distanceMeters = 20_000.0, movingSeconds = 6_000),
            ),
            listOf(Goal(1, SportType.RUN, GoalPeriod.WEEK, GoalMetric.DISTANCE, 20_000.0)),
            monday,
        )
        val entry = InboxText.weeklyRecap(recap, WeeklyStreak(4, false), formatter)!!
        assertEquals("Your week: 2 activities · 24.20 km", entry.title)
        assertEquals(
            "2h 5m · 100 m climbed · 21% more distance than the week before · Longest: Sunday Long Run, 14.20 km · Weekly run goal reached · 4-week streak",
            entry.body,
        )
        assertEquals(NotificationType.WEEKLY_SUMMARY, entry.type)
        assertEquals(NotificationTarget.RECAP, entry.target)
        assertEquals("weekly:2026-10-26", entry.dedupeKey)
    }

    @Test
    fun `an empty week gets no recap`() {
        assertNull(InboxText.weeklyRecap(RecapMath.recap(emptyList(), emptyList(), monday), WeeklyStreak.NONE, formatter))
    }

    @Test
    fun `the inbox entry leads to its week's recap`() {
        fun entry(key: String) = InboxNotification(1, NotificationType.WEEKLY_SUMMARY, "t", "b", NotificationTarget.RECAP, null, Instant.EPOCH, false, key)
        assertEquals(InboxDestination.Recap(monday), InboxDestination.of(entry("weekly:2026-10-26")))
        assertEquals(InboxDestination.Progress, InboxDestination.of(entry("weekly:garbage")))
    }
}
