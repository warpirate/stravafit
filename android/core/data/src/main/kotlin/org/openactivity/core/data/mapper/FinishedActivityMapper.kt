// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.mapper

import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.BestEffortEntity
import org.openactivity.core.database.entity.LapEntity
import org.openactivity.core.database.entity.SplitEntity
import org.openactivity.geo.BoundingBox
import org.openactivity.metrics.ActivitySamples
import org.openactivity.metrics.MovingTime
import org.openactivity.metrics.MovingTimeRule
import org.openactivity.metrics.Splits
import org.openactivity.model.LatLng
import org.openactivity.model.Split
import org.openactivity.model.UnitSystem
import org.openactivity.polyline.DouglasPeucker
import org.openactivity.polyline.PolylineCodec
import org.openactivity.recording.FinishedActivity
import org.openactivity.recording.TrackPoint
import java.time.Instant

/** The rows one finished recording becomes. Child rows carry activity id 0 until inserted. */
data class ActivityRows(
    val activity: ActivityEntity,
    val points: List<ActivityPointEntity>,
    val laps: List<LapEntity>,
    val splits: List<SplitEntity>,
    val bestEfforts: List<BestEffortEntity>,
)

/** Turns a [FinishedActivity] from the recording engine into database rows. Pure; no I/O. */
object FinishedActivityMapper {
    /** Douglas-Peucker tolerance for the stored overview polyline. */
    const val POLYLINE_TOLERANCE_METERS: Double = 3.0

    /**
     * Builds the rows for [finished] saved with [options].
     *
     * Splits come from the engine for the unit system it recorded in ([recordedUnits]) and are computed
     * with the shared splits code for the other one, so both are stored.
     */
    fun toRows(
        finished: FinishedActivity,
        options: NewActivity,
        recordedUnits: UnitSystem,
        nowMillis: Long,
    ): ActivityRows {
        val start = finished.startEpochMillis ?: (nowMillis - finished.elapsedTime.inWholeMilliseconds)
        val localStart = Instant.ofEpochMilli(start).atZone(options.timeZone).toLocalTime()
        val track = finished.points.map { LatLng(it.latitude, it.longitude) }
        val bbox = BoundingBox.of(track)
        val polyline = if (track.size >= 2) {
            PolylineCodec.encode(DouglasPeucker.simplify(track, POLYLINE_TOLERANCE_METERS))
        } else {
            null
        }
        val activity = ActivityEntity(
            uuid = options.uuid,
            sport = finished.sport.name,
            title = options.title?.takeIf { it.isNotBlank() }
                ?: DefaultTitles.forActivity(finished.sport, localStart),
            description = options.description,
            visibility = options.visibility.name,
            perceivedExertion = options.perceivedExertion,
            startEpochMillis = start,
            timeZoneId = options.timeZone.id,
            elapsedMillis = finished.elapsedTime.inWholeMilliseconds,
            movingMillis = finished.movingTime.inWholeMilliseconds,
            distanceMeters = finished.distanceMeters,
            elevationGainMeters = finished.elevationGainMeters,
            elevationLossMeters = finished.elevationLossMeters,
            altitudeSource = finished.altitudeSource.name,
            averageSpeedMps = finished.averageSpeedMps,
            maxSpeedMps = finished.maxSpeedMps,
            averageGradeAdjustedSpeedMps = finished.averageGradeAdjustedSpeedMps,
            averageHeartRate = finished.averageHeartRate,
            maxHeartRate = finished.maxHeartRate,
            averagePower = finished.averagePower,
            maxPower = finished.maxPower,
            averageCadence = finished.averageCadence,
            autoPauseCount = finished.autoPauseCount,
            polyline = polyline,
            bboxSouth = bbox?.south,
            bboxWest = bbox?.west,
            bboxNorth = bbox?.north,
            bboxEast = bbox?.east,
            privacyHideStartMeters = options.hideStartMeters,
            privacyHideEndMeters = options.hideEndMeters,
            flags = finished.flags.joinToString(",") { it.name },
            routeId = options.routeId,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        val otherUnits = if (recordedUnits == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC
        val otherSplits = computeSplits(finished, otherUnits)
        return ActivityRows(
            activity = activity,
            points = finished.points.mapIndexed { i, p -> p.toEntity(i) },
            laps = finished.laps.map { lap ->
                LapEntity(
                    activityId = 0,
                    index = lap.index,
                    startOffsetMillis = lap.startOffset.inWholeMilliseconds,
                    elapsedMillis = lap.elapsedTime.inWholeMilliseconds,
                    movingMillis = lap.movingTime.inWholeMilliseconds,
                    distanceMeters = lap.distance.meters,
                    elevationGainMeters = lap.elevationGain.meters,
                    trigger = lap.trigger.name,
                )
            },
            splits = finished.splits.map { it.toEntity(recordedUnits) } + otherSplits.map { it.toEntity(otherUnits) },
            bestEfforts = finished.bestEfforts.map {
                BestEffortEntity(0, EffortKind.DISTANCE.name, it.targetMeters, it.elapsedSeconds, it.startSeconds)
            } + finished.durationEfforts.map {
                BestEffortEntity(0, EffortKind.DURATION.name, it.durationSeconds, it.distanceMeters, it.startSeconds)
            },
        )
    }

    /** Splits of [finished] in [units] from its track, using the shared moving-time rule. */
    fun computeSplits(finished: FinishedActivity, units: UnitSystem): List<Split> {
        val points = finished.points
        if (points.size < 2) return emptyList()
        val samples = ActivitySamples(
            time = DoubleArray(points.size) { (points[it].offsetNanos - points[0].offsetNanos) / 1e9 },
            distance = DoubleArray(points.size) { points[it].distanceMeters },
            altitude = if (points.any { it.altitudeMeters != null }) {
                DoubleArray(points.size) { points[it].altitudeMeters ?: Double.NaN }
            } else {
                null
            },
            paused = BooleanArray(points.size) { points[it].paused },
        )
        val mask = MovingTime.movingMask(samples, MovingTimeRule.forSport(finished.sport))
        return Splits.compute(samples, units.splitLengthMeters, mask)
    }

    private fun TrackPoint.toEntity(seq: Int) = ActivityPointEntity(
        activityId = 0,
        seq = seq,
        latitude = latitude,
        longitude = longitude,
        elevationMeters = altitudeMeters,
        epochMillis = epochMillis,
        offsetMillis = offsetNanos / 1_000_000L,
        heartRate = heartRateBpm,
        cadence = cadence,
        powerWatts = powerWatts,
        speedMps = speedMps,
        accuracyMeters = accuracyMeters,
        distanceMeters = distanceMeters,
        paused = paused,
        segment = segment,
    )

    private fun Split.toEntity(units: UnitSystem) = SplitEntity(
        activityId = 0,
        unitSystem = units.name,
        index = index,
        distanceMeters = distance.meters,
        elapsedMillis = elapsedTime.inWholeMilliseconds,
        movingMillis = movingTime.inWholeMilliseconds,
        elevationDeltaMeters = elevationDelta?.meters,
        isPartial = isPartial,
    )
}

/** Kind of a stored best effort. */
enum class EffortKind {
    /** Fastest time over a target distance. */
    DISTANCE,

    /** Furthest distance within a target duration. */
    DURATION,
}
