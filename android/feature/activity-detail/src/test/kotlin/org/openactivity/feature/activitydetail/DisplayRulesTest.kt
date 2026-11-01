// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.FinishedActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/** Elapsed-time headline, grade-adjusted pace and pace-or-speed choice on the detail screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisplayRulesTest : RepositoryBackedTest() {
    init {
        Locale.setDefault(Locale.US)
    }

    private fun stored(finished: FinishedActivity): ActivityDetail = runBlocking {
        val id = activities.saveFinished(finished, NewActivity("rules-${finished.sport}-${finished.elapsedTime}"), UnitSystem.METRIC)
        activities.observeActivityDetail(id).first()!!
    }

    private fun labels(a: SavedActivity, display: SpeedDisplay? = null) =
        (if (display == null) DetailRows.stats(a, UnitSystem.METRIC, true) else DetailRows.stats(a, UnitSystem.METRIC, true, display))
            .map { it.label }

    @Test
    fun `moving time leads for runs and elapsed time leads for sports that headline it`() {
        val run = stored(Recordings.hillyRun(seconds = 900)).activity
        val runLabels = labels(run)
        assertTrue(runLabels.indexOf("Moving Time") < runLabels.indexOf("Elapsed Time"))
        assertEquals("Moving Time", runLabels[2])

        val ski = run.copy(sport = SportType.ALPINE_SKI, elapsedTime = 50.minutes, movingTime = 31.minutes)
        val skiStats = DetailRows.stats(ski, UnitSystem.METRIC, true)
        assertEquals("Elapsed Time", skiStats[2].label)
        assertEquals("50:00", skiStats[2].value)
        assertTrue(skiStats.any { it.label == "Moving Time" && it.value == "31:00" })

        listOf(SportType.YOGA, SportType.WEIGHT_TRAINING, SportType.WORKOUT, SportType.CROSSFIT).forEach { sport ->
            assertEquals(sport.name, "Elapsed Time", DetailRows.stats(run.copy(sport = sport), UnitSystem.METRIC, false)[2].label)
        }
    }

    @Test
    fun `runs show average grade adjusted pace, other sports do not`() {
        val run = stored(Recordings.hillyRun(seconds = 900)).activity
        assertNotNull(run.averageGradeAdjustedSpeedMps)
        val gap = DetailRows.stats(run, UnitSystem.METRIC, true).single { it.label == "Avg GAP" }
        assertEquals("/km", gap.unit)
        assertEquals(DetailFormat.speed(run.averageGradeAdjustedSpeedMps!!, SportType.RUN, UnitSystem.METRIC)!!.value, gap.value)

        assertFalse(labels(run.copy(sport = SportType.HIKE)).contains("Avg GAP"))
        assertFalse(labels(run.copy(averageGradeAdjustedSpeedMps = null)).contains("Avg GAP"))
    }

    @Test
    fun `hilly runs get a GAP column per split`() {
        val detail = stored(Recordings.hillyRun(seconds = 1800))
        val rows = DetailRows.splits(detail, UnitSystem.METRIC)
        assertTrue(rows.all { it.gap != null })
        assertTrue(DetailRows.showsSplitGap(rows))
        // The hill climbs in the first half: GAP is quicker than the actual pace on the first split.
        val first = rows.first()
        assertTrue(paceSeconds(first.gap!!.value) < paceSeconds(first.speed!!.value))

        val content = ActivityDetailViewModel.buildContent(detail, UnitSystem.METRIC, ActivityExtras.NONE, emptyList(), LocalDate.of(2025, 6, 10))
        assertTrue(content.showSplitGap)
        assertNotNull(content.chart!!.seriesFor(org.openactivity.feature.activitydetail.chart.ChartMetric.GRADE_ADJUSTED_SPEED))
    }

    @Test
    fun `flat or non-run splits have no GAP column`() {
        val detail = stored(Recordings.hillyRun(seconds = 900))
        val ride = detail.copy(activity = detail.activity.copy(sport = SportType.RIDE))
        val rows = DetailRows.splits(ride, UnitSystem.METRIC)
        assertTrue(rows.all { it.gap == null })
        assertFalse(DetailRows.showsSplitGap(rows))
        val flat = detail.copy(points = detail.points.map { it.copy(elevationMeters = 100.0) })
        assertFalse(DetailRows.showsSplitGap(DetailRows.splits(flat, UnitSystem.METRIC)))
    }

    @Test
    fun `the per-sport display choice reaches stats, splits, laps and labels`() {
        val detail = stored(Recordings.hillyRun(seconds = 1800))
        val content = ActivityDetailViewModel.buildContent(
            detail, UnitSystem.METRIC, ActivityExtras.NONE, emptyList(), LocalDate.of(2025, 6, 10), SpeedDisplay.SPEED,
        )
        assertEquals(SpeedDisplay.SPEED, content.speedDisplay)
        val avg = content.stats.single { it.label == "Avg Speed" }
        assertEquals("km/h", avg.unit)
        assertTrue(content.stats.any { it.label == "Max Speed" })
        assertTrue(content.splits.all { it.speed?.unit == "km/h" })
        assertTrue(content.laps.all { it.speed?.unit == "km/h" })
        assertEquals("Speed", DetailFormat.speedLabel(content.speedDisplay))
    }

    @Test
    fun `swim pace per 100 yards`() {
        val m = DetailFormat.speed(1.0, SpeedDisplay.PACE_PER_100YD, UnitSystem.IMPERIAL)!!
        assertEquals("1:31", m.value)
        assertEquals("/100yd", m.unit)
        assertEquals("/100m", DetailFormat.speed(1.0, SpeedDisplay.PACE_PER_100M, UnitSystem.IMPERIAL)!!.unit)
        assertEquals(91.44, DetailFormat.paceUnitMeters(SpeedDisplay.PACE_PER_100YD, UnitSystem.METRIC)!!, 1e-9)
        assertNull(DetailFormat.paceUnitMeters(SpeedDisplay.SPEED, UnitSystem.METRIC))
        // A sport's default with imperial units now picks yards for swims.
        assertEquals("/100yd", DetailFormat.speed(1.0, SportType.SWIM, UnitSystem.IMPERIAL)!!.unit)
    }

    private fun paceSeconds(text: String): Int = text.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
}
