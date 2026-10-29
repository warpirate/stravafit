// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.file

import app.stravafit.lib.fit.FitActivity
import app.stravafit.lib.fit.FitActivityEncoder
import app.stravafit.lib.fit.FitActivitySummary
import app.stravafit.lib.fit.FitEvent
import app.stravafit.lib.fit.FitEventCode
import app.stravafit.lib.fit.FitEventType
import app.stravafit.lib.fit.FitFileId
import app.stravafit.lib.fit.FitLap
import app.stravafit.lib.fit.FitLapTrigger
import app.stravafit.lib.fit.FitRecord
import app.stravafit.lib.fit.FitSession
import app.stravafit.lib.fit.FitSport
import app.stravafit.lib.geojson.Feature
import app.stravafit.lib.geojson.GeoJsonWriteOptions
import app.stravafit.lib.geojson.GeoJsonWriter
import app.stravafit.lib.geojson.LineString
import app.stravafit.lib.geojson.MultiLineString
import app.stravafit.lib.geojson.Position
import app.stravafit.lib.gpxtcx.GpxDocument
import app.stravafit.lib.gpxtcx.GpxMetadata
import app.stravafit.lib.gpxtcx.GpxTrack
import app.stravafit.lib.gpxtcx.GpxWriteOptions
import app.stravafit.lib.gpxtcx.GpxWriter
import app.stravafit.lib.gpxtcx.TcxActivity
import app.stravafit.lib.gpxtcx.TcxDocument
import app.stravafit.lib.gpxtcx.TcxLap
import app.stravafit.lib.gpxtcx.TcxSport
import app.stravafit.lib.gpxtcx.TcxTriggerMethod
import app.stravafit.lib.gpxtcx.TcxWriter
import app.stravafit.lib.gpxtcx.TrackPoint
import app.stravafit.lib.gpxtcx.TrackSegment
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.ExportPoint
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

/** Why an activity cannot be written in a given format. */
public enum class FormatUnavailableReason {
    /** GPX, GeoJSON and images need positions; the activity has no GPS samples (or all were trimmed). */
    NEEDS_ROUTE,

    /** TCX and FIT need a timestamp on every sample. */
    NEEDS_TIMES,
}

/**
 * Encodes an [ExportActivity] as GPX, TCX, FIT or GeoJSON using the project's Apache-2.0 libraries.
 *
 * Callers pass an already privacy-trimmed activity when the file leaves the device; the encoder itself
 * never trims. Image formats are handled by the renderers, not here.
 */
public class TrackFileEncoder @Inject constructor() {

    /** Returns why [activity] cannot be written as [format], or `null` when it can. */
    public fun unavailableReason(activity: ExportActivity, format: ExportFormat): FormatUnavailableReason? =
        when (format) {
            ExportFormat.TCX, ExportFormat.FIT -> if (activity.hasTimes) null else FormatUnavailableReason.NEEDS_TIMES
            else -> if (activity.hasRoute) null else FormatUnavailableReason.NEEDS_ROUTE
        }

    /**
     * Encodes [activity] as the data [format].
     *
     * @throws IllegalArgumentException for image formats, or when [unavailableReason] is not `null`.
     */
    public fun encode(activity: ExportActivity, format: ExportFormat): ByteArray {
        require(format.isData) { "$format is not a track-data format" }
        unavailableReason(activity, format)?.let { throw IllegalArgumentException("Cannot write $format: $it") }
        return when (format) {
            ExportFormat.GPX -> gpx(activity)
            ExportFormat.TCX -> tcx(activity)
            ExportFormat.FIT -> fit(activity)
            ExportFormat.GEOJSON -> geoJson(activity)
            ExportFormat.SVG, ExportFormat.PNG, ExportFormat.PDF -> error("unreachable")
        }
    }

    private fun ExportPoint.toTrackPoint() = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        elevation = elevation,
        time = time,
        heartRate = heartRate,
        cadence = cadence,
        power = power,
        distance = distance,
    )

    private fun gpx(activity: ExportActivity): ByteArray {
        val document = GpxDocument(
            creator = CREATOR,
            metadata = GpxMetadata(name = activity.title, time = activity.startTime),
            tracks = listOf(
                GpxTrack(
                    name = activity.title,
                    type = gpxType(activity.sport),
                    // Position-less samples (trimmed or indoor) split the track into segments, as GPX
                    // readers expect for gaps.
                    segments = positionedRuns(activity.points).map { run -> TrackSegment(run.map { it.toTrackPoint() }) },
                ),
            ),
        )
        return GpxWriter(GpxWriteOptions(creator = CREATOR)).writeToString(document).toByteArray(Charsets.UTF_8)
    }

    private fun tcx(activity: ExportActivity): ByteArray {
        val points = activity.points.map { it.toTrackPoint() }
        val start = activity.startTime ?: points.first().time!!
        val derived = TcxLap.fromPoints(points, startTime = start, triggerMethod = TcxTriggerMethod.MANUAL)
        val lap = derived.copy(
            totalTimeSeconds = activity.stats.movingTimeSeconds.toDouble().takeIf { it > 0 } ?: derived.totalTimeSeconds,
            distanceMeters = activity.stats.distanceMeters.takeIf { it > 0 } ?: derived.distanceMeters,
            calories = activity.stats.calories,
        )
        val document = TcxDocument(listOf(TcxActivity(tcxSport(activity.sport), start, listOf(lap), creatorName = CREATOR)))
        return TcxWriter().writeToString(document).toByteArray(Charsets.UTF_8)
    }

    private fun fit(activity: ExportActivity): ByteArray {
        val records = activity.points.map { p ->
            FitRecord(
                timestamp = p.time!!,
                latitude = p.latitude,
                longitude = p.longitude,
                altitude = p.elevation,
                heartRate = p.heartRate,
                cadence = p.cadence,
                distance = p.distance,
                power = p.power,
            )
        }
        val start = activity.startTime ?: records.first().timestamp
        val end = records.last().timestamp
        val elapsed = Duration.between(start, end).toMillis() / 1000.0
        val timer = activity.stats.movingTimeSeconds.toDouble().takeIf { it > 0 } ?: elapsed
        val heartRates = records.mapNotNull { it.heartRate }
        val sport = fitSport(activity.sport)
        val first = records.firstOrNull { it.latitude != null }
        val distance = activity.stats.distanceMeters.takeIf { it > 0 }
            ?: records.lastOrNull { it.distance != null }?.distance
        val ascent = activity.stats.elevationGainMeters?.roundToInt()
        val fitActivity = FitActivity(
            fileId = FitFileId(timeCreated = start, productName = CREATOR),
            records = records,
            events = listOf(
                FitEvent(start, FitEventCode.TIMER, FitEventType.START, eventGroup = 0),
                FitEvent(end, FitEventCode.TIMER, FitEventType.STOP_ALL, eventGroup = 0),
            ),
            laps = listOf(
                FitLap(
                    timestamp = end, startTime = start, totalElapsedTime = elapsed, totalTimerTime = timer,
                    totalDistance = distance, startLatitude = first?.latitude, startLongitude = first?.longitude,
                    totalCalories = activity.stats.calories,
                    avgHeartRate = heartRates.averageOrNull(), maxHeartRate = heartRates.maxOrNull(),
                    totalAscent = ascent, lapTrigger = FitLapTrigger.SESSION_END, sport = sport,
                ),
            ),
            sessions = listOf(
                FitSession(
                    timestamp = end, startTime = start, sport = sport, totalElapsedTime = elapsed,
                    totalTimerTime = timer, totalDistance = distance, startLatitude = first?.latitude,
                    startLongitude = first?.longitude, totalCalories = activity.stats.calories,
                    avgHeartRate = activity.stats.averageHeartRate ?: heartRates.averageOrNull(),
                    maxHeartRate = heartRates.maxOrNull(), totalAscent = ascent, firstLapIndex = 0, numLaps = 1,
                ),
            ),
            activity = FitActivitySummary(
                timestamp = end,
                totalTimerTime = timer,
                numSessions = 1,
                localTimestamp = LocalDateTime.ofInstant(end, activity.zone),
            ),
        )
        return FitActivityEncoder().encode(fitActivity)
    }

    private fun geoJson(activity: ExportActivity): ByteArray {
        val runPoints = positionedRuns(activity.points)
        val runs = runPoints.map { run -> run.map { Position.fromLatLon(it.latitude!!, it.longitude!!, it.elevation) } }
        val geometry = if (runs.size == 1) LineString(runs.single()) else MultiLineString(runs)
        val start: Instant? = activity.startTime ?: activity.points.firstNotNullOfOrNull { it.time }
        val times = runPoints.flatten().map { it.time?.toString() }
        val feature = Feature(
            geometry = geometry,
            properties = linkedMapOf(
                "name" to activity.title,
                "sport" to activity.sport.apiName,
                "start" to start?.toString(),
                "distance_m" to activity.stats.distanceMeters,
                "moving_time_s" to activity.stats.movingTimeSeconds,
                "elapsed_time_s" to activity.stats.elapsedTimeSeconds,
                "elevation_gain_m" to activity.stats.elevationGainMeters,
                // Same convention as other track converters: one timestamp per coordinate.
                "coordinate_times" to times.takeIf { list -> list.any { it != null } },
            ),
        )
        return GeoJsonWriter(GeoJsonWriteOptions(includeBbox = true)).write(feature).toByteArray(Charsets.UTF_8)
    }

    private companion object {
        const val CREATOR = "OpenActivity"

        /** Splits samples into runs of consecutive positioned samples (runs of one point are dropped). */
        fun positionedRuns(points: List<ExportPoint>): List<List<ExportPoint>> {
            val runs = mutableListOf<MutableList<ExportPoint>>()
            var current: MutableList<ExportPoint>? = null
            for (p in points) {
                if (!p.hasPosition) {
                    current = null
                } else {
                    val run = current ?: mutableListOf<ExportPoint>().also { runs += it }
                    run += p
                    current = run
                }
            }
            return runs.filter { it.size >= 2 }.ifEmpty { runs }
        }

        fun List<Int>.averageOrNull(): Int? = if (isEmpty()) null else average().roundToInt()

        fun gpxType(sport: SportType): String = when {
            sport.isRunLike -> "running"
            sport.category == SportCategory.BIKE -> "cycling"
            sport == SportType.WALK -> "walking"
            sport == SportType.HIKE -> "hiking"
            sport == SportType.SWIM -> "swimming"
            else -> sport.apiName.lowercase()
        }

        fun tcxSport(sport: SportType): String = when (sport.category) {
            SportCategory.FOOT -> TcxSport.RUNNING
            SportCategory.BIKE -> TcxSport.BIKING
            else -> TcxSport.OTHER
        }

        fun fitSport(sport: SportType): Int = when {
            sport.isRunLike -> FitSport.RUNNING
            sport == SportType.E_BIKE_RIDE || sport == SportType.E_MOUNTAIN_BIKE_RIDE -> FitSport.E_BIKING
            sport.category == SportCategory.BIKE -> FitSport.CYCLING
            sport == SportType.WALK -> FitSport.WALKING
            sport == SportType.HIKE -> FitSport.HIKING
            sport == SportType.SWIM -> FitSport.SWIMMING
            sport == SportType.ROWING || sport == SportType.VIRTUAL_ROW -> FitSport.ROWING
            sport == SportType.NORDIC_SKI -> FitSport.CROSS_COUNTRY_SKIING
            sport == SportType.ALPINE_SKI || sport == SportType.BACKCOUNTRY_SKI -> FitSport.ALPINE_SKIING
            sport == SportType.SNOWBOARD -> FitSport.SNOWBOARDING
            sport == SportType.KAYAKING || sport == SportType.CANOEING || sport == SportType.STAND_UP_PADDLING ->
                FitSport.PADDLING
            sport.category == SportCategory.FITNESS -> FitSport.TRAINING
            else -> FitSport.GENERIC
        }
    }
}
