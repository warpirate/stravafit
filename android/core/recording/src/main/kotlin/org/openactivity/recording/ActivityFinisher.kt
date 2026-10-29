// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.metrics.ActivitySamples
import org.openactivity.metrics.BestEfforts
import org.openactivity.metrics.GradeAdjustedPace
import org.openactivity.metrics.MovingTime
import org.openactivity.metrics.Splits
import org.openactivity.metrics.StandardEfforts
import org.openactivity.model.Lap
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Builds the [FinishedActivity] from the engine's final state using the shared metrics code, so the
 * numbers match what the server recomputes from the uploaded file.
 */
internal object ActivityFinisher {
    class Input(
        val config: RecordingConfig,
        val autoPauseActive: Boolean,
        val startEpochMillis: Long?,
        val elapsedNanos: Long,
        val timerNanos: Long,
        val distanceMeters: Double,
        val maxSpeedMps: Double,
        val elevationGain: Double,
        val elevationLoss: Double,
        val altitudeSource: AltitudeSource,
        val laps: List<Lap>,
        val pauses: List<PauseInterval>,
        val autoPauseCount: Int,
        val gaps: List<GpsGap>,
        val flags: Set<ActivityFlag>,
        val points: List<TrackPoint>,
        val averageHeartRate: Double?,
        val maxHeartRate: Double,
        val averagePower: Double?,
        val maxPower: Double,
        val averageCadence: Double?,
    )

    fun build(input: Input): FinishedActivity {
        val sport = input.config.sport
        val profile = input.config.profile
        val samples = samplesOf(input.points, input.pauses)
        // With auto-pause, moving time is the timer time covered by the track; otherwise it is derived from
        // the track with the shared moving-time rule (paused intervals never count). Either way the time
        // before the first fix, when nothing is known about movement, is not moving time, so the summary
        // average, the splits and the charts all divide by the same seconds. Activities without a track use
        // timer time.
        val noTrack = samples.size < 2
        val timerMode = input.autoPauseActive || noTrack
        val mask = if (timerMode) MovingTime.timerMask(samples) else MovingTime.movingMask(samples, profile.movingRule)
        val movingNanos = if (noTrack) {
            input.timerNanos
        } else {
            (MovingTime.sumIntervals(samples, mask) * 1e9).toLong()
        }.coerceIn(0L, input.elapsedNanos)
        val movingSeconds = movingNanos / 1e9
        val cap = profile.maxSpeedMps
        // Efforts run on timer time: a pause inside the fastest half mile does not count against it.
        val effortSamples = timerTimeSamples(samples)
        val bestEfforts = when {
            sport.isRunLike -> BestEfforts.forDistances(effortSamples, StandardEfforts.RUN_DISTANCES_M, cap)
            sport.isRideLike -> BestEfforts.forDistances(effortSamples, StandardEfforts.RIDE_DISTANCES_M, cap)
            else -> emptyList()
        }
        val durationEfforts = if (sport.isRideLike) {
            StandardEfforts.RIDE_DURATIONS_S.mapNotNull { BestEfforts.forDuration(effortSamples, it, cap) }
        } else {
            emptyList()
        }
        return FinishedActivity(
            sport = sport,
            startEpochMillis = input.startEpochMillis,
            elapsedTime = input.elapsedNanos.nanoseconds,
            movingTime = movingNanos.nanoseconds,
            distanceMeters = input.distanceMeters,
            elevationGainMeters = input.elevationGain,
            elevationLossMeters = input.elevationLoss,
            altitudeSource = input.altitudeSource,
            averageSpeedMps = if (movingSeconds > 0.0) input.distanceMeters / movingSeconds else 0.0,
            maxSpeedMps = input.maxSpeedMps,
            averageGradeAdjustedSpeedMps = if (sport.isRunLike) GradeAdjustedPace.averageSpeed(samples, mask) else null,
            averageHeartRate = input.averageHeartRate,
            maxHeartRate = input.maxHeartRate.takeIf { it.isFinite() }?.toInt(),
            averagePower = input.averagePower,
            maxPower = input.maxPower.takeIf { it.isFinite() }?.toInt(),
            averageCadence = input.averageCadence,
            splits = Splits.compute(samples, input.config.unitSystem.splitLengthMeters, mask),
            laps = input.laps,
            bestEfforts = bestEfforts,
            durationEfforts = durationEfforts,
            pauses = input.pauses,
            autoPauseCount = input.autoPauseCount,
            gpsGaps = input.gaps,
            flags = input.flags,
            points = input.points,
        )
    }

    /**
     * [samples] with paused intervals squeezed out of the time axis: time stands still across a pause, so
     * windows measure timer time. No distance is added while paused, so nothing else changes.
     */
    internal fun timerTimeSamples(samples: ActivitySamples): ActivitySamples {
        val n = samples.size
        if (n == 0) return samples
        val t = DoubleArray(n)
        t[0] = samples.time[0]
        for (i in 1 until n) {
            val dt = samples.time[i] - samples.time[i - 1]
            t[i] = t[i - 1] + if (samples.isPaused(i)) 0.0 else dt
        }
        return ActivitySamples(t, samples.distance, altitude = samples.altitude)
    }

    /** Converts the track to metric samples; an interval is paused when its midpoint lies inside a pause. */
    private fun samplesOf(points: List<TrackPoint>, pauses: List<PauseInterval>): ActivitySamples {
        val n = points.size
        val time = DoubleArray(n) { points[it].offsetNanos / 1e9 }
        val distance = DoubleArray(n) { points[it].distanceMeters }
        val hasAltitude = points.any { it.altitudeMeters != null }
        val altitude = if (hasAltitude) DoubleArray(n) { points[it].altitudeMeters ?: Double.NaN } else null
        val paused = BooleanArray(n)
        var k = 0
        for (i in 1 until n) {
            val mid = (points[i - 1].offsetNanos + points[i].offsetNanos) / 2
            while (k < pauses.size && pauses[k].endOffsetNanos <= mid) k++
            paused[i] = k < pauses.size && pauses[k].startOffsetNanos <= mid
        }
        return ActivitySamples(time, distance, altitude = altitude, paused = paused)
    }
}
