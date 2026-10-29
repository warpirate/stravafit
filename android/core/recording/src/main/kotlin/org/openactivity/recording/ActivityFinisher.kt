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
        // Strava-compatible rule: with auto-pause or manual pauses, moving time is timer time; otherwise it
        // is derived from the track. Activities without a track use timer time.
        val timerMode = input.autoPauseActive || samples.size < 2 || input.pauses.any { it.kind == PauseKind.MANUAL }
        val mask = if (timerMode) MovingTime.timerMask(samples) else MovingTime.movingMask(samples, profile.movingRule)
        val movingNanos = if (timerMode) {
            input.timerNanos
        } else {
            (MovingTime.sumIntervals(samples, mask) * 1e9).toLong()
        }.coerceIn(0L, input.elapsedNanos)
        val movingSeconds = movingNanos / 1e9
        val cap = profile.maxSpeedMps
        val bestEfforts = when {
            sport.isRunLike -> BestEfforts.forDistances(samples, StandardEfforts.RUN_DISTANCES_M, cap)
            sport.isRideLike -> BestEfforts.forDistances(samples, StandardEfforts.RIDE_DISTANCES_M, cap)
            else -> emptyList()
        }
        val durationEfforts = if (sport.isRideLike) {
            StandardEfforts.RIDE_DURATIONS_S.mapNotNull { BestEfforts.forDuration(samples, it, cap) }
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
