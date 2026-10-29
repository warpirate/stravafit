// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.export

import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.RouteDetail
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.ExportPoint
import org.openactivity.core.export.model.ExportPrivacy
import org.openactivity.core.export.model.ExportStats
import org.openactivity.model.SportType
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Maps a stored activity to the export model. Paused samples are left out (they are kept in the
 * database only for cropping), and activities without a GPS track export without positions.
 */
fun ActivityDetail.toExportActivity(): ExportActivity {
    val hasTrack = activity.polyline != null
    val points = points.filterNot { it.paused }.map { p ->
        ExportPoint(
            latitude = p.latitude.takeIf { hasTrack },
            longitude = p.longitude.takeIf { hasTrack },
            elevation = p.elevationMeters,
            time = Instant.ofEpochMilli(p.epochMillis),
            heartRate = p.heartRate,
            cadence = p.cadence?.roundToInt(),
            power = p.powerWatts,
            distance = p.distanceMeters,
        )
    }
    return ExportActivity(
        id = activity.uuid,
        title = activity.title,
        sport = activity.sport,
        startTime = activity.startTime,
        points = points,
        stats = ExportStats(
            distanceMeters = activity.distanceMeters,
            movingTimeSeconds = Math.round(activity.movingTime.inWholeMilliseconds / 1000.0),
            elapsedTimeSeconds = Math.round(activity.elapsedTime.inWholeMilliseconds / 1000.0),
            movingTimeExactSeconds = activity.movingTime.inWholeMilliseconds / 1000.0,
            elevationGainMeters = activity.elevationGainMeters,
            averageHeartRate = activity.averageHeartRate?.roundToInt(),
        ),
        zone = activity.timeZone,
        privacy = ExportPrivacy(activity.hideStartMeters, activity.hideEndMeters),
    )
}

/** Maps a saved route to the export model: positions and elevations, no times. */
fun RouteDetail.toExportActivity(): ExportActivity = ExportActivity(
    id = route.uuid,
    title = route.name,
    sport = route.sport ?: SportType.OTHER,
    startTime = null,
    points = points.map { ExportPoint(it.latitude, it.longitude, elevation = it.elevationMeters, distance = it.distanceMeters) },
    stats = ExportStats(
        distanceMeters = route.distanceMeters,
        movingTimeSeconds = 0,
        elevationGainMeters = route.elevationGainMeters,
    ),
    privacy = ExportPrivacy(0.0, 0.0),
)
