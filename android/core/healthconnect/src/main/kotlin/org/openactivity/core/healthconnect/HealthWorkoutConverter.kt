// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import org.openactivity.core.fileimport.ActivityFileParser
import org.openactivity.core.fileimport.ImportFormat
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ImportedPoint
import org.openactivity.core.fileimport.ManualActivity
import java.time.Duration

/** A workout read from Health Connect, ready for the importer: with a track, or a summary only. */
public sealed interface HealthImport {
    public val source: HealthWorkoutRead

    /** It has a route: stored like an imported GPX, with its heart rate on the track. */
    public data class Track(override val source: HealthWorkoutRead, val activity: ImportedActivity) : HealthImport

    /** No route (an indoor or gym session, or the route was not shared): its time, distance and heart rate. */
    public data class Summary(override val source: HealthWorkoutRead, val activity: ManualActivity) : HealthImport
}

/** Turns [HealthWorkoutRead]s into the neutral import models of `:core:import`. */
public object HealthWorkoutConverter {
    /** Heart-rate samples further than this from a route point are not put on it. */
    private val HEART_RATE_MATCH: Duration = Duration.ofSeconds(10)

    /**
     * The import model for [workout], or null when it lasted no time at all. Distances are metres; with
     * a route the distance is measured along it, without one it is what the other app wrote.
     */
    public fun convert(workout: HealthWorkoutRead): HealthImport? {
        val elapsed = Duration.between(workout.start, workout.end).seconds
        if (elapsed <= 0) return null
        val sport = HealthExerciseTypes.fromHealth(workout.exerciseType)
        val route = workout.route.orEmpty().sortedBy { it.time }
        val offset = workout.startOffset?.totalSeconds
        if (route.size >= 2) {
            val heartRate = workout.heartRate.sortedBy { it.time }
            val points = route.map { p ->
                ImportedPoint(
                    latitude = p.latitude,
                    longitude = p.longitude,
                    elevation = p.altitudeMeters,
                    time = p.time,
                    heartRate = nearest(heartRate, p)?.beatsPerMinute?.toInt(),
                )
            }
            val hash = ActivityFileParser.sha256("hc:${workout.id}".toByteArray(Charsets.UTF_8))
            val activity = ImportedActivity(
                sourceName = "Health Connect",
                format = ImportFormat.GPX,
                title = workout.title,
                sport = sport,
                sportDeclared = true,
                startTime = workout.start,
                points = points,
                distanceMeters = workout.distanceMeters ?: 0.0,
                elapsedSeconds = elapsed,
                deviceName = workout.originPackage,
                dedupeKey = ActivityFileParser.dedupeKey(workout.start, points, hash),
                contentHash = hash,
                utcOffsetSeconds = offset,
            )
            return HealthImport.Track(workout, activity)
        }
        val bpm = workout.heartRate.map { it.beatsPerMinute }
        return HealthImport.Summary(
            workout,
            ManualActivity(
                sport = sport,
                startTime = workout.start,
                elapsedSeconds = elapsed,
                distanceMeters = workout.distanceMeters?.takeIf { it.isFinite() && it > 0 } ?: 0.0,
                averageHeartRate = timeWeightedAverage(workout.heartRate),
                maxHeartRate = bpm.maxOrNull()?.toInt(),
                utcOffsetSeconds = offset,
            ),
        )
    }

    /** The sample closest in time to [point] within [HEART_RATE_MATCH], by binary search over sorted [samples]. */
    private fun nearest(samples: List<HealthHeartRate>, point: HealthLocation): HealthHeartRate? {
        if (samples.isEmpty()) return null
        var lo = 0
        var hi = samples.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (samples[mid].time.isBefore(point.time)) lo = mid + 1 else hi = mid
        }
        val candidates = listOfNotNull(samples.getOrNull(lo), samples.getOrNull(lo - 1))
        val best = candidates.minByOrNull { Duration.between(it.time, point.time).abs() } ?: return null
        return best.takeIf { Duration.between(it.time, point.time).abs() <= HEART_RATE_MATCH }
    }

    /** Each sample weighted by the time until the next one, so a burst of samples does not dominate. */
    internal fun timeWeightedAverage(samples: List<HealthHeartRate>): Double? {
        val sorted = samples.sortedBy { it.time }
        if (sorted.isEmpty()) return null
        if (sorted.size == 1) return sorted[0].beatsPerMinute.toDouble()
        var weighted = 0.0
        var total = 0.0
        for (i in 0 until sorted.size - 1) {
            val dt = Duration.between(sorted[i].time, sorted[i + 1].time).toMillis() / 1000.0
            // A gap longer than a minute is a pause, not a minute at the same heart rate.
            val w = dt.coerceIn(0.0, MAX_SAMPLE_WEIGHT_SECONDS)
            weighted += sorted[i].beatsPerMinute * w
            total += w
        }
        return if (total > 0) weighted / total else sorted.map { it.beatsPerMinute }.average()
    }

    private const val MAX_SAMPLE_WEIGHT_SECONDS = 60.0
}
