// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.model.SportType
import java.time.Instant
import java.time.ZoneOffset

/** Workouts other apps wrote, as the importer receives them. */
class HealthWorkoutConverterTest {
    private val start = Instant.parse("2026-10-20T00:40:00Z")

    private fun read(
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        route: List<HealthLocation>? = null,
        heartRate: List<HealthHeartRate> = emptyList(),
        distance: Double? = null,
        seconds: Long = 1_800,
    ) = HealthWorkoutRead(
        id = "6c1e0f2a-0000-4000-8000-000000000001",
        originPackage = "com.garmin.android.apps.connectmobile",
        exerciseType = type,
        title = "Pune Running",
        notes = null,
        start = start,
        end = start.plusSeconds(seconds),
        startOffset = ZoneOffset.ofHoursMinutes(5, 30),
        route = route,
        routeConsentRequired = false,
        heartRate = heartRate,
        distanceMeters = distance,
    )

    @Test
    fun `a workout with a route becomes a track with heart rate on its points`() {
        val route = List(10) { i -> HealthLocation(start.plusSeconds(i * 10L), 18.5368 + i * 1e-4, 73.8791, 560.0) }
        val hr = List(30) { i -> HealthHeartRate(start.plusSeconds(i * 3L + 1), 130L + i) }
        val result = HealthWorkoutConverter.convert(read(route = route, heartRate = hr, distance = 99.0)) as HealthImport.Track
        val activity = result.activity
        assertEquals(SportType.RUN, activity.sport)
        assertEquals("Pune Running", activity.title)
        assertEquals(10, activity.points.size)
        assertEquals(560.0, activity.points[0].elevation!!, 0.0)
        // The sample at +1 s is the nearest to the point at 0 s; the one at +31 s to the point at 30 s.
        assertEquals(130, activity.points[0].heartRate)
        assertEquals(140, activity.points[3].heartRate)
        assertEquals(19_800, activity.utcOffsetSeconds)
        assertEquals(1_800L, activity.elapsedSeconds)
    }

    @Test
    fun `heart rate far from every route point is not attached`() {
        val route = List(3) { i -> HealthLocation(start.plusSeconds(i * 60L), 18.5368 + i * 1e-3, 73.8791) }
        val hr = listOf(HealthHeartRate(start.plusSeconds(30), 150))
        val activity = (HealthWorkoutConverter.convert(read(route = route, heartRate = hr)) as HealthImport.Track).activity
        assertTrue(activity.points.all { it.heartRate == null })
    }

    @Test
    fun `a workout without a route becomes a summary`() {
        val hr = listOf(HealthHeartRate(start, 100), HealthHeartRate(start.plusSeconds(10), 120), HealthHeartRate(start.plusSeconds(40), 160))
        val result = HealthWorkoutConverter.convert(read(type = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, heartRate = hr, distance = 0.0)) as HealthImport.Summary
        val manual = result.activity
        assertEquals(SportType.WEIGHT_TRAINING, manual.sport)
        assertEquals(1_800L, manual.elapsedSeconds)
        assertEquals(0.0, manual.distanceMeters, 0.0)
        assertEquals(160, manual.maxHeartRate)
        // 100 bpm for 10 s, 120 bpm for 30 s.
        assertEquals(115.0, manual.averageHeartRate!!, 1e-9)
        assertEquals(19_800, manual.utcOffsetSeconds)
    }

    @Test
    fun `an indoor run keeps the distance the other app wrote`() {
        val result = HealthWorkoutConverter.convert(read(type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL, distance = 5_020.0)) as HealthImport.Summary
        assertEquals(SportType.VIRTUAL_RUN, result.activity.sport)
        assertEquals(5_020.0, result.activity.distanceMeters, 0.0)
        assertNull(result.activity.averageHeartRate)
    }

    @Test
    fun `a workout that lasted no time is skipped`() {
        assertNull(HealthWorkoutConverter.convert(read(seconds = 0)))
    }
}
