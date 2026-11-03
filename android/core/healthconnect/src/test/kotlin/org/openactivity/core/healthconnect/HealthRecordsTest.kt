// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset

/** Units, ids and limits of the records written for an activity. */
@RunWith(RobolectricTestRunner::class)
class HealthRecordsTest {
    private val start = Instant.parse("2026-10-03T01:15:00Z")
    private val pune = ZoneOffset.ofHoursMinutes(5, 30)

    private fun workout(seconds: Int = 600, sport: SportType = SportType.RUN, everySecond: Boolean = false): HealthWorkout {
        val step = if (everySecond) 1L else 5L
        val n = (seconds / step).toInt()
        return HealthWorkout(
            clientId = "rec-42",
            version = 3,
            sport = sport,
            title = "Morning Run",
            notes = "Easy loop",
            start = start,
            end = start.plusSeconds(seconds.toLong()),
            startOffset = pune,
            endOffset = pune,
            distanceMeters = 2_012.5,
            route = List(n) { i -> HealthLocation(start.plusSeconds(i * step), 18.5368 + i * 1e-5, 73.8791, 558.0 + i * 0.1, 4.0) },
            heartRate = List(n) { i -> HealthHeartRate(start.plusSeconds(i * step), 140L + i % 10) },
        )
    }

    @Test
    fun `everything allowed writes session, route, distance and heart rate`() {
        val records = HealthRecords.build(workout(), HealthPermissions.forWriting)
        val session = records[0] as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, session.exerciseType)
        assertEquals("Morning Run", session.title)
        assertEquals("Easy loop", session.notes)
        assertEquals(start, session.startTime)
        assertEquals(pune, session.startZoneOffset)
        assertEquals("rec-42:session", session.metadata.clientRecordId)
        assertEquals(3L, session.metadata.clientRecordVersion)
        val route = (session.exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route
        assertEquals(120, route.size)
        assertEquals(558.0, route[0].altitude!!.inMeters, 1e-9)
        assertEquals(4.0, route[0].horizontalAccuracy!!.inMeters, 1e-9)

        val distance = records.filterIsInstance<DistanceRecord>().single()
        assertEquals(2_012.5, distance.distance.inMeters, 1e-9)
        assertEquals(2.0125, distance.distance.inKilometers, 1e-9)
        assertEquals("rec-42:distance", distance.metadata.clientRecordId)

        val hr = records.filterIsInstance<HeartRateRecord>().single()
        assertEquals(120, hr.samples.size)
        assertEquals(140L, hr.samples[0].beatsPerMinute)
        assertEquals("rec-42:hr:0", hr.metadata.clientRecordId)
    }

    @Test
    fun `parts the user did not allow are left out`() {
        val records = HealthRecords.build(workout(), setOf(HealthPermissions.WRITE_EXERCISE))
        val session = records.single() as ExerciseSessionRecord
        assertTrue(session.exerciseRouteResult is ExerciseRouteResult.NoData)
    }

    @Test
    fun `long heart rate is split into records with their own ids`() {
        val records = HealthRecords.build(workout(seconds = 2_500, everySecond = true), HealthPermissions.forWriting)
        val hr = records.filterIsInstance<HeartRateRecord>()
        assertEquals(listOf(1_000, 1_000, 500), hr.map { it.samples.size })
        assertEquals(listOf("rec-42:hr:0", "rec-42:hr:1", "rec-42:hr:2"), hr.map { it.metadata.clientRecordId })
        assertTrue(hr.all { r -> r.samples.all { !it.time.isBefore(r.startTime) && !it.time.isAfter(r.endTime) } })
    }

    @Test
    fun `samples outside the session and impossible values are dropped`() {
        val w = workout().let {
            it.copy(
                route = it.route + HealthLocation(it.end.plusSeconds(30), 18.6, 73.9) + HealthLocation(it.start.plusSeconds(1), 95.0, 73.9),
                heartRate = it.heartRate + HealthHeartRate(it.start.minusSeconds(5), 150) + HealthHeartRate(it.start.plusSeconds(2), 0),
            )
        }
        val records = HealthRecords.build(w, HealthPermissions.forWriting)
        val route = ((records[0] as ExerciseSessionRecord).exerciseRouteResult as ExerciseRouteResult.Data).exerciseRoute.route
        assertEquals(120, route.size)
        assertEquals(120, records.filterIsInstance<HeartRateRecord>().single().samples.size)
    }

    @Test
    fun `an indoor activity has no route and no distance record`() {
        val w = workout(sport = SportType.SWIM).copy(route = emptyList(), distanceMeters = null, hasGps = false)
        val records = HealthRecords.build(w, HealthPermissions.forWriting)
        val session = records[0] as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, session.exerciseType)
        assertTrue(session.exerciseRouteResult is ExerciseRouteResult.NoData)
        assertTrue(records.none { it is DistanceRecord })
    }

    @Test
    fun `very long routes are thinned evenly, keeping both ends`() {
        val thinned = HealthRecords.thin((0 until 25_000).toList(), HealthRecords.MAX_ROUTE_POINTS)
        assertEquals(HealthRecords.MAX_ROUTE_POINTS, thinned.size)
        assertEquals(0, thinned.first())
        assertEquals(24_999, thinned.last())
        assertEquals(thinned.sorted(), thinned)
        assertNull(HealthRecords.thin(listOf<Int>(), 10).firstOrNull())
    }
}
