// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration

/** Rules for personal segments. */
object SegmentRules {
    /** A segment must be at least this long (Strava's own minimum for a new segment). */
    const val MIN_LENGTH_METERS: Double = 200.0

    /** Names are trimmed to this many characters. */
    const val MAX_NAME_LENGTH: Int = 80
}

/**
 * A personal segment.
 *
 * @property elevations elevation at each of [points], or null when the source activity had none.
 * @property averageGradePercent (finish - start elevation) / distance, in percent.
 * @property sourceActivityId the activity it was cut from, if that still matters to the caller.
 */
data class Segment(
    val id: Long,
    val name: String,
    val sport: SportType,
    val points: List<LatLng>,
    val elevations: List<Double>?,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val averageGradePercent: Double,
    val starred: Boolean,
    val createdAt: Instant,
    val sourceActivityId: Long?,
) {
    /** Highest and lowest point, or null without elevations. */
    val elevationRange: ClosedFloatingPointRange<Double>? get() = elevations?.takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }
}

/** A segment with how often you did it and your best time. */
data class SegmentSummary(
    val segment: Segment,
    val effortCount: Int,
    val bestElapsed: Duration?,
    val bestActivityId: Long?,
    val bestAt: Instant?,
)

/**
 * One effort on a segment, with its activity and segment for lists.
 *
 * @property rankAllTime place among all efforts when it was saved (frozen), null for the very first effort.
 * @property rankYear place among efforts of [year] when it was saved, null for the first one that year.
 */
data class SegmentEffort(
    val id: Long,
    val segmentId: Long,
    val segmentName: String,
    val segmentSport: SportType,
    val segmentDistanceMeters: Double,
    val segmentGradePercent: Double,
    val activityId: Long,
    val activityTitle: String,
    val activitySport: SportType,
    val startTime: Instant,
    val timeZone: ZoneId,
    val year: Int,
    val startIndex: Int,
    val endIndex: Int,
    val elapsed: Duration,
    val moving: Duration,
    val distanceMeters: Double,
    val averageSpeedMps: Double,
    val averageHeartRate: Double?,
    val maxHeartRate: Int?,
    val averagePower: Double?,
    val averageCadence: Double?,
    val rankAllTime: Int?,
    val rankYear: Int?,
    val isPersonalRecord: Boolean,
) {
    /** The medal to show (1 to 3): the all-time one, else the yearly one, else none. */
    val medalRank: Int?
        get() = rankAllTime?.takeIf { it <= MEDALS } ?: rankYear?.takeIf { it <= MEDALS }

    /** True when only the yearly ranking earned the medal (an outlined medal). */
    val isYearlyMedal: Boolean get() = rankAllTime?.takeIf { it <= MEDALS } == null && medalRank != null

    private companion object {
        const val MEDALS = 3
    }
}

/**
 * What a range of an activity would make as a segment, for the create screen.
 *
 * @property startIndex first point (in the activity's point list) of the range.
 * @property endIndex last point of the range.
 */
data class SegmentDraft(
    val startIndex: Int,
    val endIndex: Int,
    val points: List<LatLng>,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val averageGradePercent: Double,
    val hasElevation: Boolean,
) {
    /** Long enough to save. */
    val isLongEnough: Boolean get() = distanceMeters >= SegmentRules.MIN_LENGTH_METERS
}
