// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class DetailRowsTest {
    init {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `pace and speed follow the sport and units`() {
        assertEquals("5:00", DetailFormat.speed(1000.0 / 300, SportType.RUN, UnitSystem.METRIC)!!.value)
        assertEquals("/km", DetailFormat.speed(1000.0 / 300, SportType.RUN, UnitSystem.METRIC)!!.unit)
        assertEquals("/mi", DetailFormat.speed(3.0, SportType.RUN, UnitSystem.IMPERIAL)!!.unit)
        assertEquals("1:40", DetailFormat.speed(1.0, SportType.SWIM, UnitSystem.METRIC)!!.value)
        assertEquals("36.0", DetailFormat.speed(10.0, SportType.RIDE, UnitSystem.METRIC)!!.value)
        assertEquals("mph", DetailFormat.speed(10.0, SportType.RIDE, UnitSystem.IMPERIAL)!!.unit)
        assertNull(DetailFormat.speed(0.0, SportType.RUN, UnitSystem.METRIC))
    }

    @Test
    fun `durations, elevation and effort names read naturally`() {
        assertEquals("52:14", DetailFormat.duration(3134.seconds))
        assertEquals("1:02:03", DetailFormat.duration(3723.seconds))
        assertEquals("+12", DetailFormat.elevationDelta(12.4, UnitSystem.METRIC))
        assertEquals("−6", DetailFormat.elevationDelta(-6.2, UnitSystem.METRIC))
        assertEquals("276", DetailFormat.elevation(84.0, UnitSystem.IMPERIAL).value)
        assertEquals("5K", DetailFormat.effortName(5000.0))
        assertEquals("½ mile", DetailFormat.effortName(804.672))
        assertEquals("Half Marathon", DetailFormat.effortName(21_097.5))
        assertEquals("40 km", DetailFormat.effortName(40_000.0))
        assertEquals("5 min", DetailFormat.effortDuration(300.0))
        assertEquals("2 h", DetailFormat.effortDuration(7200.0))
    }

    @Test
    fun `split heart rate averages only the split's moving samples`() {
        val points = List(20) { i ->
            ActivityPoint(
                latitude = 0.0, longitude = 0.0, elevationMeters = null, epochMillis = i * 1000L, offsetMillis = i * 1000L,
                heartRate = if (i < 10) 120 else 160, cadence = null, powerWatts = null, speedMps = 3.0, accuracyMeters = null,
                distanceMeters = 100.0 + i * 10.0, paused = i == 5, segment = 0,
            )
        }
        assertEquals(120.0, DetailRows.averageHeartRate(points, 0.0, 100.0)!!, 1e-9)
        assertEquals(160.0, DetailRows.averageHeartRate(points, 100.0, 1000.0)!!, 1e-9)
        assertNull(DetailRows.averageHeartRate(points, 500.0, 600.0))
    }

    @Test
    fun `split rows mark fastest and slowest and label the partial split`() {
        val finished = Recordings.hillyRun(seconds = 1500)
        val detail = org.openactivity.core.data.model.ActivityDetail(
            activity = sampleActivity(finished),
            points = emptyList(),
            laps = finished.laps,
            metricSplits = finished.splits,
            imperialSplits = emptyList(),
            bestEfforts = finished.bestEfforts,
            durationEfforts = emptyList(),
        )
        val rows = DetailRows.splits(detail, UnitSystem.METRIC)
        assertEquals(detail.metricSplits.size, rows.size)
        assertEquals(1, rows.count { it.isFastest })
        assertEquals(1, rows.count { it.isSlowest })
        assertTrue(rows.last().isPartial)
        assertTrue(rows.last().label.contains("."))
        assertEquals(1f, rows.maxOf { it.barFraction }, 1e-6f)
        assertTrue(rows.all { it.heartRate == null }) // no points given
        assertEquals(rows[1].startMeters, rows[0].endMeters, 1e-9)
    }

    private fun sampleActivity(f: org.openactivity.recording.FinishedActivity) = org.openactivity.core.data.model.SavedActivity(
        id = 1, uuid = "u", sport = f.sport, title = "t", description = "", visibility = org.openactivity.model.Visibility.FOLLOWERS,
        perceivedExertion = null, startTime = java.time.Instant.ofEpochMilli(f.startEpochMillis!!), timeZone = java.time.ZoneOffset.UTC,
        elapsedTime = f.elapsedTime, movingTime = f.movingTime, distanceMeters = f.distanceMeters,
        elevationGainMeters = f.elevationGainMeters, elevationLossMeters = f.elevationLossMeters, altitudeSource = f.altitudeSource,
        averageSpeedMps = f.averageSpeedMps, maxSpeedMps = f.maxSpeedMps, averageGradeAdjustedSpeedMps = null,
        averageHeartRate = f.averageHeartRate, maxHeartRate = f.maxHeartRate, averagePower = null, maxPower = null,
        averageCadence = null, autoPauseCount = 0, polyline = null, bounds = null, hideStartMeters = 0.0, hideEndMeters = 0.0,
        flags = emptySet(), routeId = null,
    )
}
