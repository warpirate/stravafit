// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.recordingservice.PendingStart
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingStatus
import routeexport.TrackPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SummaryStateTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val totals = StatTotals(3, 24_150.0, 2.hours + 12.minutes, 2.hours + 30.minutes, 180.0)

    private fun progress(id: Long, sport: SportType?, period: GoalPeriod = GoalPeriod.WEEK, start: LocalDate = monday, current: Double = 12_400.0) =
        GoalProgress(Goal(id, sport, period, GoalMetric.DISTANCE, 30_000.0), start, start.plusWeeks(1), current, 0.3, 5)

    @Test
    fun `no goal shows the totals alone`() {
        val week = weekSummary(monday, totals, emptyList(), UnitSystem.METRIC)
        assertEquals(3, week.count)
        assertEquals(24_150.0, week.distanceMeters, 0.0)
        assertNull(week.goal)
    }

    @Test
    fun `the all-sports weekly goal is preferred, monthly goals are ignored`() {
        val week = weekSummary(
            monday, totals,
            listOf(progress(1, SportType.RUN), progress(2, null, GoalPeriod.MONTH), progress(3, null)),
            UnitSystem.METRIC,
        )
        assertEquals(null, week.goal?.sport)
        assertEquals(GoalMetric.DISTANCE, week.goal?.metric)
    }

    @Test
    fun `otherwise the first weekly goal`() {
        val week = weekSummary(monday, totals, listOf(progress(5, SportType.RIDE), progress(4, SportType.RUN)), UnitSystem.METRIC)
        assertEquals(SportType.RUN, week.goal?.sport)
    }

    @Test
    fun `a goal of another week does not show`() {
        val week = weekSummary(monday, totals, listOf(progress(1, null, start = monday.minusWeeks(1))), UnitSystem.METRIC)
        assertNull(week.goal)
    }

    @Test
    fun `goal bar is clamped and reached once beaten`() {
        val beaten = WeekGoal(GoalMetric.DISTANCE, null, 32_000.0, 30_000.0)
        assertEquals(1f, beaten.fraction)
        assertTrue(beaten.reached)
        val started = WeekGoal(GoalMetric.DISTANCE, null, 7_500.0, 30_000.0)
        assertEquals(0.25f, started.fraction)
        assertFalse(started.reached)
    }

    @Test
    fun `hidden start and finish are cut from the drawing`() {
        // A straight line north, about 111 m between points.
        val line = (0..20).map { TrackPoint(18.5 + it * 0.001, 73.8) }
        val kept = hideEnds(line, startMeters = 300.0, endMeters = 200.0)
        assertEquals(line[3], kept.first())
        assertEquals(line[18], kept.last())
        assertEquals(line, hideEnds(line, 0.0, 0.0))
    }

    @Test
    fun `hiding too much leaves no route`() {
        val short = (0..3).map { TrackPoint(18.5 + it * 0.001, 73.8) }
        assertTrue(hideEnds(short, 300.0, 300.0).isEmpty())
        assertTrue(hideEnds(listOf(TrackPoint(18.5, 73.8)), 0.0, 0.0).isEmpty())
    }

    @Test
    fun `day label says today, yesterday or the date in the activity's zone`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val today = LocalDate.of(2026, 11, 3)
        fun label(at: String) = dayLabel(Instant.parse(at), zone, today, { "D:$it" }, "Today", "Yesterday")
        assertEquals("Today", label("2026-11-02T19:00:00Z")) // 00:30 IST on the 3rd
        assertEquals("Yesterday", label("2026-11-02T18:00:00Z")) // 23:30 IST on the 2nd
        assertEquals("D:2026-10-31", label("2026-10-31T06:00:00Z"))
    }

    @Test
    fun `last activity without a polyline has no route`() {
        assertNull(lastActivityCard(null, UnitSystem.METRIC))
    }
}

class WidgetFormatExtrasTest {
    @Test
    fun `week time is hours and minutes`() {
        assertEquals("45m", WidgetFormat.duration(45.minutes + 30.seconds))
        assertEquals("4h 2m", WidgetFormat.duration(4.hours + 2.minutes))
        assertEquals("0m", WidgetFormat.duration((-5).seconds))
    }

    @Test
    fun `goal progress is printed in the goal's unit`() {
        assertEquals("12.40 / 30", WidgetFormat.goalProgress(GoalMetric.DISTANCE, 12_400.0, 30_000.0, UnitSystem.METRIC, Locale.UK))
        assertEquals("12,40 / 30", WidgetFormat.goalProgress(GoalMetric.DISTANCE, 12_400.0, 30_000.0, UnitSystem.METRIC, Locale.GERMANY))
        assertEquals("1h 30m / 5h 0m", WidgetFormat.goalProgress(GoalMetric.TIME, 5_400.0, 18_000.0, UnitSystem.METRIC))
        assertEquals("2 / 4", WidgetFormat.goalProgress(GoalMetric.COUNT, 2.0, 4.0, UnitSystem.METRIC))
        assertEquals("328 / 1640", WidgetFormat.goalProgress(GoalMetric.ELEVATION, 100.0, 500.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `gps line follows the GPS state`() {
        assertEquals(GpsLine(GpsLineKind.SEARCHING, null), gpsLine(null))
        assertEquals(GpsLine(GpsLineKind.READY, 6), gpsLine(GpsStatus.forAccuracy(6.4)))
        assertEquals(GpsLine(GpsLineKind.WEAK, 38), gpsLine(GpsStatus.forAccuracy(38.0)))
        assertEquals(GpsLine(GpsLineKind.OFF, null), gpsLine(GpsStatus(GpsState.DISABLED)))
        assertEquals(GpsLine(GpsLineKind.OFF, null), gpsLine(GpsStatus(GpsState.NO_PERMISSION)))
    }

    @Test
    fun `links name the app's screens`() {
        assertEquals("openactivity://record?sport=RIDE", WidgetLinks.record(SportType.RIDE))
        assertEquals("openactivity://activity/42", WidgetLinks.activity(42))
        assertEquals("openactivity://you", WidgetLinks.YOU)
    }
}

class TileModelTest {
    private val idle = RecordingUiSnapshot.idle()

    @Test
    fun `idle opens the recorder`() {
        val model = tileModel(idle, null)
        assertEquals(TileModel.Kind.READY, model.kind)
        assertFalse(model.active)
        assertEquals(TileTap.OPEN_RECORDER, model.tap)
    }

    @Test
    fun `recording pauses and paused resumes`() {
        val recording = idle.copy(status = RecordingStatus.RECORDING, sport = SportType.RUN, timerTime = 12.minutes + 3.seconds)
        val model = tileModel(recording, null)
        assertEquals(TileModel.Kind.RECORDING, model.kind)
        assertEquals(723L, model.elapsedSeconds)
        assertEquals(TileTap.PAUSE, model.tap)
        assertEquals(TileTap.RESUME, tileModel(recording.copy(status = RecordingStatus.PAUSED), null).tap)
        assertEquals(TileTap.RESUME, tileModel(recording.copy(status = RecordingStatus.AUTO_PAUSED), null).tap)
    }

    @Test
    fun `a widget start in progress shows on the tile`() {
        assertEquals(TileModel.Kind.STARTING, tileModel(idle, PendingStart(SportType.RUN, null, 0)).kind)
        val waiting = tileModel(idle, PendingStart(SportType.RUN, null, 0, waitingForGps = true))
        assertEquals(TileModel.Kind.WAITING_FOR_GPS, waiting.kind)
        assertTrue(waiting.active)
        assertEquals(TileTap.OPEN_RECORDER, waiting.tap)
    }
}
