// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length

/**
 * Builds the Health Connect records for a [HealthWorkout]. Pure: no Health Connect connection is needed,
 * so the units, ids and limits are unit-tested.
 *
 * Every record carries a client record id derived from the activity's uuid and its version, so writing
 * an activity again (the "write past activities" button, a retry) updates the earlier copy instead of
 * adding a second one.
 */
public object HealthRecords {
    /** Heart-rate samples per record; very long records are slow to write and to read back. */
    internal const val HEART_RATE_SAMPLES_PER_RECORD = 1_000

    /** Route points per session; a long day out at one point a second is thinned evenly to this. */
    internal const val MAX_ROUTE_POINTS = 10_000

    /** The session, then distance and heart rate, as allowed by [granted]. */
    public fun build(workout: HealthWorkout, granted: Set<String>): List<Record> {
        require(workout.end.isAfter(workout.start)) { "A workout must end after it starts" }
        val records = ArrayList<Record>()
        records += session(workout, includeRoute = HealthPermissions.ROUTE_WRITE in granted)
        val distance = workout.distanceMeters
        if (HealthPermissions.DISTANCE_WRITE in granted && distance != null && distance > 0.0 && distance.isFinite()) {
            records += DistanceRecord(
                startTime = workout.start,
                startZoneOffset = workout.startOffset,
                endTime = workout.end,
                endZoneOffset = workout.endOffset,
                distance = Length.meters(distance),
                metadata = metadata(workout, "distance"),
            )
        }
        if (HealthPermissions.HEART_RATE_WRITE in granted) records += heartRate(workout)
        return records
    }

    internal fun session(workout: HealthWorkout, includeRoute: Boolean): ExerciseSessionRecord {
        val route = if (includeRoute) route(workout) else emptyList()
        return ExerciseSessionRecord(
            startTime = workout.start,
            startZoneOffset = workout.startOffset,
            endTime = workout.end,
            endZoneOffset = workout.endOffset,
            metadata = metadata(workout, "session"),
            exerciseType = HealthExerciseTypes.toHealth(workout.sport, outdoors = workout.hasGps),
            title = workout.title.take(MAX_TITLE),
            notes = workout.notes?.takeIf { it.isNotBlank() }?.take(MAX_NOTES),
            exerciseRoute = if (route.size >= 2) ExerciseRoute(route) else null,
        )
    }

    /** Route points inside the session, in time order, one per instant, thinned to [MAX_ROUTE_POINTS]. */
    internal fun route(workout: HealthWorkout): List<ExerciseRoute.Location> {
        val inside = workout.route
            .filter { !it.time.isBefore(workout.start) && it.time.isBefore(workout.end) }
            .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            .sortedBy { it.time }
            .distinctBy { it.time }
        return thin(inside, MAX_ROUTE_POINTS).map { p ->
            ExerciseRoute.Location(
                time = p.time,
                latitude = p.latitude,
                longitude = p.longitude,
                horizontalAccuracy = p.horizontalAccuracyMeters?.takeIf { it >= 0 && it.isFinite() }?.let(Length::meters),
                altitude = p.altitudeMeters?.takeIf { it.isFinite() }?.let(Length::meters),
            )
        }
    }

    /** Heart rate in records of at most [HEART_RATE_SAMPLES_PER_RECORD] samples, each covering its samples. */
    internal fun heartRate(workout: HealthWorkout): List<HeartRateRecord> {
        val samples = workout.heartRate
            .filter { !it.time.isBefore(workout.start) && !it.time.isAfter(workout.end) }
            .filter { it.beatsPerMinute in MIN_BPM..MAX_BPM }
            .sortedBy { it.time }
            .distinctBy { it.time }
        return samples.chunked(HEART_RATE_SAMPLES_PER_RECORD).mapIndexed { index, chunk ->
            val start = chunk.first().time
            // Samples must lie before the record's end, so the record runs a second past its last one.
            val end = chunk.last().time.plusSeconds(1)
            HeartRateRecord(
                startTime = start,
                startZoneOffset = workout.startOffset,
                endTime = end,
                endZoneOffset = workout.endOffset,
                samples = chunk.map { HeartRateRecord.Sample(it.time, it.beatsPerMinute) },
                metadata = metadata(workout, "hr:$index"),
            )
        }
    }

    private fun metadata(workout: HealthWorkout, part: String): Metadata =
        Metadata.activelyRecorded(PHONE, "${workout.clientId}:$part", workout.version)

    /** Every n-th point (first and last always kept) so that at most [max] remain. */
    internal fun <T> thin(points: List<T>, max: Int): List<T> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { i -> points[Math.round(i * step).toInt().coerceAtMost(points.size - 1)] }
    }

    private val PHONE = Device(type = Device.TYPE_PHONE)

    private const val MAX_TITLE = 1_000
    private const val MAX_NOTES = 1_000

    /** Health Connect accepts 1..300 bpm. */
    private const val MIN_BPM = 1L
    private const val MAX_BPM = 300L
}
