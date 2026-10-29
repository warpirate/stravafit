// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.mapper

import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.core.data.model.RoutePoint
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.SavedRoute
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.BestEffortEntity
import org.openactivity.core.database.entity.LapEntity
import org.openactivity.core.database.entity.RouteEntity
import org.openactivity.core.database.entity.RoutePointEntity
import org.openactivity.core.database.entity.SplitEntity
import org.openactivity.geo.BoundingBox
import org.openactivity.metrics.DistanceEffort
import org.openactivity.metrics.DurationEffort
import org.openactivity.model.Distance
import org.openactivity.model.Lap
import org.openactivity.model.LapTrigger
import org.openactivity.model.SportType
import org.openactivity.model.Split
import org.openactivity.model.Visibility
import org.openactivity.recording.ActivityFlag
import org.openactivity.recording.AltitudeSource
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

/** Database rows to domain models. Unknown stored names fall back to safe defaults instead of crashing. */
internal fun ActivityEntity.toDomain(): SavedActivity = SavedActivity(
    id = id,
    uuid = uuid,
    sport = sportOf(sport) ?: SportType.OTHER,
    title = title,
    description = description,
    visibility = enumOrNull<Visibility>(visibility) ?: Visibility.DEFAULT,
    perceivedExertion = perceivedExertion,
    startTime = Instant.ofEpochMilli(startEpochMillis),
    timeZone = zoneOf(timeZoneId),
    elapsedTime = elapsedMillis.milliseconds,
    movingTime = movingMillis.milliseconds,
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
    elevationLossMeters = elevationLossMeters,
    altitudeSource = enumOrNull<AltitudeSource>(altitudeSource) ?: AltitudeSource.NONE,
    averageSpeedMps = averageSpeedMps,
    maxSpeedMps = maxSpeedMps,
    averageGradeAdjustedSpeedMps = averageGradeAdjustedSpeedMps,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    averagePower = averagePower,
    maxPower = maxPower,
    averageCadence = averageCadence,
    autoPauseCount = autoPauseCount,
    polyline = polyline,
    bounds = boundsOf(bboxSouth, bboxWest, bboxNorth, bboxEast),
    hideStartMeters = privacyHideStartMeters,
    hideEndMeters = privacyHideEndMeters,
    flags = flags.split(',').filter { it.isNotBlank() }.mapNotNull { enumOrNull<ActivityFlag>(it) }.toSet(),
    routeId = routeId,
)

internal fun ActivityPointEntity.toDomain() = ActivityPoint(
    latitude, longitude, elevationMeters, epochMillis, offsetMillis, heartRate, cadence, powerWatts, speedMps,
    accuracyMeters, distanceMeters, paused, segment,
)

internal fun LapEntity.toDomain() = Lap(
    index = index,
    startOffset = startOffsetMillis.milliseconds,
    elapsedTime = elapsedMillis.milliseconds,
    movingTime = movingMillis.milliseconds,
    distance = Distance(distanceMeters),
    elevationGain = Distance(elevationGainMeters),
    trigger = enumOrNull<LapTrigger>(trigger) ?: LapTrigger.MANUAL,
)

internal fun SplitEntity.toDomain() = Split(
    index = index,
    distance = Distance(distanceMeters),
    elapsedTime = elapsedMillis.milliseconds,
    movingTime = movingMillis.milliseconds,
    elevationDelta = elevationDeltaMeters?.let(::Distance),
    isPartial = isPartial,
)

internal fun BestEffortEntity.toDistanceEffort() = DistanceEffort(target, value, startSeconds, 0.0)

internal fun BestEffortEntity.toDurationEffort() = DurationEffort(target, value, startSeconds)

internal fun RouteEntity.toDomain() = SavedRoute(
    id = id,
    uuid = uuid,
    name = name,
    description = description,
    sport = sport?.let(::sportOf),
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
    elevationLossMeters = elevationLossMeters,
    polyline = polyline,
    bounds = BoundingBox(bboxSouth, bboxWest, bboxNorth, bboxEast),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)

internal fun RoutePointEntity.toDomain() = RoutePoint(latitude, longitude, elevationMeters, distanceMeters)

private fun boundsOf(south: Double?, west: Double?, north: Double?, east: Double?): BoundingBox? =
    if (south != null && west != null && north != null && east != null) BoundingBox(south, west, north, east) else null

internal fun sportOf(name: String): SportType? = enumOrNull<SportType>(name)

internal fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (e: DateTimeException) {
    ZoneOffset.UTC
}

internal inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
    enumValues<E>().firstOrNull { it.name == name }
