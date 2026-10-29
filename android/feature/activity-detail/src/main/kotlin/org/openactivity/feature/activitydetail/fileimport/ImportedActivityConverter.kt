// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ImportedPoint
import org.openactivity.geo.Spherical
import org.openactivity.metrics.ActivitySamples
import org.openactivity.metrics.BestEfforts
import org.openactivity.metrics.ElevationMetrics
import org.openactivity.metrics.ElevationSource
import org.openactivity.metrics.GradeAdjustedPace
import org.openactivity.metrics.MovingTime
import org.openactivity.metrics.MovingTimeRule
import org.openactivity.metrics.SeriesStats
import org.openactivity.metrics.Splits
import org.openactivity.metrics.StandardEfforts
import org.openactivity.model.UnitSystem
import org.openactivity.recording.AltitudeSource
import org.openactivity.recording.FinishedActivity
import org.openactivity.recording.SportProfile
import org.openactivity.recording.TrackPoint
import kotlin.math.max
import kotlin.time.Duration.Companion.nanoseconds

/** Why an imported file cannot become an activity. */
class UnusableActivityException(message: String) : Exception(message)

/**
 * Turns an [ImportedActivity] read from a GPX, TCX or FIT file into the same [FinishedActivity] the
 * recorder produces, computing every summary value with the shared metrics code (moving time, splits,
 * elevation gain with the GPS hysteresis, best efforts, grade-adjusted pace). Imported activities are
 * therefore indistinguishable from recorded ones on every screen.
 *
 * Samples without a time are dropped; samples without a position still count for time, distance,
 * heart rate and power, but cannot be stored as track points (indoor sessions keep their summary only).
 */
object ImportedActivityConverter {
    /** Window for the derived per-sample speed when the file has none. */
    private const val SPEED_WINDOW_SAMPLES = 2

    /** Window of the robust maximum speed (a single GPS jump never sets it). */
    private const val MAX_SPEED_WINDOW_SECONDS = 5.0

    /**
     * Converts [imported], with splits in [units].
     *
     * @throws UnusableActivityException when the file has no timed samples (a planned route, not a recording).
     */
    fun convert(imported: ImportedActivity, units: UnitSystem): FinishedActivity {
        val timed = imported.points.filter { it.time != null }.sortedBy { it.time }
        if (timed.size < 2) {
            throw UnusableActivityException("The file has no timestamps, so it looks like a planned route rather than a recorded activity")
        }
        val sport = imported.sport
        val profile = SportProfile.forSport(sport)
        val t0 = timed.first().time!!
        val n = timed.size
        val time = DoubleArray(n) { (timed[it].time!!.toEpochMilli() - t0.toEpochMilli()) / 1000.0 }
        val distance = cumulativeDistance(timed)
        val hasAltitude = timed.count { it.elevation != null } >= 2
        val altitude = if (hasAltitude) DoubleArray(n) { timed[it].elevation ?: Double.NaN } else null
        val heartRate = DoubleArray(n) { timed[it].heartRate?.toDouble() ?: Double.NaN }
        val power = DoubleArray(n) { timed[it].power?.toDouble() ?: Double.NaN }
        val cadence = DoubleArray(n) { timed[it].cadence?.toDouble() ?: Double.NaN }
        val samples = ActivitySamples(time, distance, altitude = altitude, heartRate = heartRate, power = power, cadence = cadence)

        val rule = MovingTimeRule.forSport(sport)
        val mask = MovingTime.movingMask(samples, rule)
        val elapsedSeconds = time[n - 1]
        val movingSeconds = MovingTime.sumIntervals(samples, mask).coerceIn(0.0, elapsedSeconds)
        val totalDistance = distance[n - 1]
        val elevation = altitude?.let { ElevationMetrics.compute(it, ElevationSource.GPS.hysteresisMeters) }
        val speeds = sampleSpeeds(timed, time, distance)
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
        val track = timed.indices.filter { timed[it].hasPosition }.map { i ->
            val p = timed[i]
            TrackPoint(
                offsetNanos = (time[i] * 1e9).toLong(),
                epochMillis = p.time!!.toEpochMilli(),
                latitude = p.latitude!!,
                longitude = p.longitude!!,
                rawLatitude = p.latitude!!,
                rawLongitude = p.longitude!!,
                accuracyMeters = null,
                altitudeMeters = p.elevation,
                speedMps = speeds[i],
                distanceMeters = distance[i],
                paused = false,
                segment = 0,
                heartRateBpm = p.heartRate,
                cadence = p.cadence?.toDouble(),
                powerWatts = p.power,
            )
        }
        return FinishedActivity(
            sport = sport,
            startEpochMillis = (imported.startTime ?: t0).toEpochMilli().coerceAtMost(t0.toEpochMilli()),
            elapsedTime = (elapsedSeconds * 1e9).toLong().nanoseconds,
            movingTime = (movingSeconds * 1e9).toLong().nanoseconds,
            distanceMeters = totalDistance,
            elevationGainMeters = elevation?.gain ?: 0.0,
            elevationLossMeters = elevation?.loss ?: 0.0,
            altitudeSource = if (hasAltitude) AltitudeSource.GPS else AltitudeSource.NONE,
            averageSpeedMps = if (movingSeconds > 0.0) totalDistance / movingSeconds else 0.0,
            maxSpeedMps = BestEfforts.maxAverageSpeed(samples, MAX_SPEED_WINDOW_SECONDS, cap),
            averageGradeAdjustedSpeedMps = if (sport.isRunLike && hasAltitude) GradeAdjustedPace.averageSpeed(samples, mask) else null,
            averageHeartRate = SeriesStats.timeWeightedAverage(time, heartRate, mask),
            maxHeartRate = SeriesStats.max(heartRate)?.toInt(),
            averagePower = SeriesStats.timeWeightedAverage(time, power, mask),
            maxPower = SeriesStats.max(power)?.toInt(),
            averageCadence = SeriesStats.timeWeightedAverage(time, cadence, mask),
            splits = Splits.compute(samples, units.splitLengthMeters, mask),
            laps = emptyList(),
            bestEfforts = bestEfforts,
            durationEfforts = durationEfforts,
            pauses = emptyList(),
            autoPauseCount = 0,
            gpsGaps = emptyList(),
            flags = emptySet(),
            points = track,
        )
    }

    /**
     * Cumulative distance: the device's own distance when every timed sample carries a non-decreasing
     * value (wheel sensors and foot pods beat GPS), otherwise measured along the positions.
     */
    internal fun cumulativeDistance(points: List<ImportedPoint>): DoubleArray {
        val n = points.size
        val device = points.map { it.distance }
        if (device.all { it != null } && device.zipWithNext().all { (a, b) -> b!! >= a!! } && device.last()!! > device.first()!!) {
            val base = device.first()!!
            return DoubleArray(n) { device[it]!! - base }
        }
        val out = DoubleArray(n)
        var last: ImportedPoint? = null
        for (i in 0 until n) {
            val p = points[i]
            out[i] = if (i == 0) 0.0 else out[i - 1]
            if (p.hasPosition) {
                last?.let { out[i] += Spherical.distance(it.latitude!!, it.longitude!!, p.latitude!!, p.longitude!!) }
                last = p
            }
        }
        return out
    }

    /** Device speed where present, otherwise a centred difference over a few samples. */
    private fun sampleSpeeds(points: List<ImportedPoint>, time: DoubleArray, distance: DoubleArray): DoubleArray {
        val n = points.size
        return DoubleArray(n) { i ->
            points[i].speed?.takeIf { it.isFinite() && it >= 0.0 } ?: run {
                val a = max(0, i - SPEED_WINDOW_SAMPLES)
                val b = minOf(n - 1, i + SPEED_WINDOW_SAMPLES)
                val dt = time[b] - time[a]
                if (dt > 0.0) (distance[b] - distance[a]) / dt else 0.0
            }
        }
    }
}
