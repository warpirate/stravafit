// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.model

import org.openactivity.model.SportType
import java.time.Instant
import java.time.ZoneId

/**
 * Everything the share/export pipeline needs to know about one activity.
 *
 * This is a neutral model owned by the export module: the app maps its own storage into it (see the
 * `ExportActivityProvider` in the share-export feature), so export code never depends on the database.
 *
 * @property id stable identifier, used for file names and caching.
 * @property title user-visible activity name.
 * @property sport the activity's sport.
 * @property startTime when the activity started, or `null` when unknown.
 * @property zone time zone the activity was recorded in, for local-time subtitles.
 * @property points recorded samples in chronological order; may be empty (manual entries).
 * @property stats summary values as the app computed them (shown on cards; files carry raw samples).
 * @property privacy the owner's privacy settings, applied when the privacy-trim toggle is on.
 */
public data class ExportActivity(
    val id: String,
    val title: String,
    val sport: SportType,
    val startTime: Instant?,
    val points: List<ExportPoint>,
    val stats: ExportStats,
    val zone: ZoneId = ZoneId.systemDefault(),
    val privacy: ExportPrivacy = ExportPrivacy(),
) {
    /** Samples that carry a position. */
    val positionedPoints: List<ExportPoint> get() = points.filter { it.hasPosition }

    /** True when at least two samples carry a position, so a route can be drawn. */
    val hasRoute: Boolean get() = points.count { it.hasPosition } >= 2

    /** True when at least two samples carry an elevation. */
    val hasElevation: Boolean get() = points.count { it.elevation != null } >= 2

    /** True when any sample carries a heart rate. */
    val hasHeartRate: Boolean get() = points.any { it.heartRate != null }

    /** True when every sample has a time, which TCX and FIT require. */
    val hasTimes: Boolean get() = points.isNotEmpty() && points.all { it.time != null }
}

/**
 * One recorded sample. Units are SI: degrees, metres, beats per minute, revolutions or steps per
 * minute, watts.
 *
 * @property latitude WGS-84 latitude, or `null` for samples without a fix (indoor activities).
 * @property longitude WGS-84 longitude; `null` exactly when [latitude] is.
 * @property distance cumulative distance from the start in metres, when the recorder provides it.
 */
public data class ExportPoint(
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double? = null,
    val time: Instant? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val distance: Double? = null,
) {
    init {
        require((latitude == null) == (longitude == null)) { "latitude and longitude must be set together" }
        if (latitude != null && longitude != null) {
            require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
            require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
        }
    }

    /** True when this sample has a position. */
    val hasPosition: Boolean get() = latitude != null
}

/**
 * Summary values shown on cards.
 *
 * @property distanceMeters total distance.
 * @property movingTimeSeconds time spent moving.
 * @property elapsedTimeSeconds wall-clock duration including pauses.
 * @property elevationGainMeters total ascent, when known.
 * @property averageHeartRate average heart rate, when recorded.
 * @property calories estimated energy, when known.
 */
public data class ExportStats(
    val distanceMeters: Double,
    val movingTimeSeconds: Long,
    val elapsedTimeSeconds: Long = movingTimeSeconds,
    val elevationGainMeters: Double? = null,
    val averageHeartRate: Int? = null,
    val calories: Int? = null,
    /**
     * Moving time with its fraction. Images derive pace from it so they round the same value the same way
     * as the activity detail and the feed (5:48 /km everywhere, never 5:47 on the poster).
     */
    val movingTimeExactSeconds: Double = movingTimeSeconds.toDouble(),
)

/**
 * Privacy settings used when trimming is on. Mirrors the app's privacy-zone settings.
 *
 * @property hideStartMeters track distance hidden from the start.
 * @property hideEndMeters track distance hidden from the end.
 * @property zones circles (home, work) that must not be revealed near either end.
 */
public data class ExportPrivacy(
    val hideStartMeters: Double = DEFAULT_HIDE_METERS,
    val hideEndMeters: Double = DEFAULT_HIDE_METERS,
    val zones: List<ExportPrivacyZone> = emptyList(),
) {
    /** True when trimming would remove anything at all. */
    public val hidesAnything: Boolean get() = hideStartMeters > 0.0 || hideEndMeters > 0.0 || zones.isNotEmpty()

    public companion object {
        /** Default trim when the owner has not configured anything: about one city block. */
        public const val DEFAULT_HIDE_METERS: Double = 200.0
    }
}

/** A hidden circle around [latitude]/[longitude] with [radiusMeters]. */
public data class ExportPrivacyZone(val latitude: Double, val longitude: Double, val radiusMeters: Double)
