// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Duration
import java.time.Instant

/** A Garmin Training Center (TCX v2) document holding one or more [activities]. */
public data class TcxDocument(val activities: List<TcxActivity>)

/**
 * A TCX `<Activity>`.
 *
 * @property sport Raw `Sport` attribute. The schema only allows [TcxSport.RUNNING], [TcxSport.BIKING] and
 *   [TcxSport.OTHER], but files in the wild carry other values, so the reader keeps the raw string.
 * @property id The activity id, which TCX defines as the start time.
 * @property creatorName Name of the recording device (`<Creator><Name>`), if present.
 */
public data class TcxActivity(
    val sport: String,
    val id: Instant,
    val laps: List<TcxLap>,
    val notes: String? = null,
    val creatorName: String? = null,
)

/** Values of the TCX `Sport` attribute that the schema allows. */
public object TcxSport {
    public const val RUNNING: String = "Running"
    public const val BIKING: String = "Biking"
    public const val OTHER: String = "Other"
}

/**
 * A TCX `<Lap>`.
 *
 * @property totalTimeSeconds Lap duration as written by the device (usually timer time).
 * @property distanceMeters Lap distance.
 * @property maximumSpeed Maximum speed in m/s.
 * @property cadence Average cadence (`<Cadence>` for cycling, `ns3:LX/AvgRunCadence` for running).
 * @property averageWatts Average power from the `ns3:LX/AvgWatts` extension.
 * @property tracks The lap's `<Track>` elements; more than one usually means the timer was paused.
 */
public data class TcxLap(
    val startTime: Instant,
    val totalTimeSeconds: Double,
    val distanceMeters: Double,
    val maximumSpeed: Double? = null,
    val calories: Int? = null,
    val averageHeartRate: Int? = null,
    val maximumHeartRate: Int? = null,
    val intensity: TcxIntensity = TcxIntensity.ACTIVE,
    val cadence: Int? = null,
    val triggerMethod: TcxTriggerMethod = TcxTriggerMethod.MANUAL,
    val averageSpeed: Double? = null,
    val averageWatts: Int? = null,
    val maximumWatts: Int? = null,
    val tracks: List<TrackSegment> = emptyList(),
    val notes: String? = null,
) {
    /** All trackpoints of all tracks of this lap, in order. */
    val trackpoints: List<TrackPoint> get() = tracks.flatMap { it.points }

    public companion object {
        /**
         * Builds a lap whose totals are derived from [points]: duration from the first to the last
         * timestamp, distance from the cumulative distances, and heart-rate average/maximum.
         * The [startTime] defaults to the first point's time.
         *
         * TCX trackpoint distances are cumulative from the start of the activity, so for every lap
         * after the first pass the previous lap's final cumulative distance as [startDistance];
         * the lap distance is the last cumulative distance minus [startDistance].
         */
        public fun fromPoints(
            points: List<TrackPoint>,
            startTime: Instant = requireNotNull(points.firstNotNullOfOrNull { it.time }) {
                "startTime is required when no point has a time"
            },
            triggerMethod: TcxTriggerMethod = TcxTriggerMethod.MANUAL,
            startDistance: Double = 0.0,
        ): TcxLap {
            val lastTime = points.lastOrNull { it.time != null }?.time ?: startTime
            val heartRates = points.mapNotNull { it.heartRate }
            return TcxLap(
                startTime = startTime,
                totalTimeSeconds = Duration.between(startTime, lastTime).toMillis() / 1000.0,
                distanceMeters = points.lastOrNull { it.distance != null }?.distance
                    ?.let { (it - startDistance).coerceAtLeast(0.0) } ?: 0.0,
                maximumSpeed = points.mapNotNull { it.speed }.maxOrNull(),
                averageHeartRate = if (heartRates.isEmpty()) null else Math.round(heartRates.average()).toInt(),
                maximumHeartRate = heartRates.maxOrNull(),
                triggerMethod = triggerMethod,
                tracks = listOf(TrackSegment(points)),
            )
        }
    }
}

/** TCX `Intensity_t`. */
public enum class TcxIntensity(public val xmlValue: String) {
    ACTIVE("Active"),
    RESTING("Resting"),
    ;

    internal companion object {
        fun parse(value: String?): TcxIntensity =
            entries.firstOrNull { it.xmlValue.equals(value?.trim(), ignoreCase = true) } ?: ACTIVE
    }
}

/** TCX `TriggerMethod_t`: what ended the lap. */
public enum class TcxTriggerMethod(public val xmlValue: String) {
    MANUAL("Manual"),
    DISTANCE("Distance"),
    LOCATION("Location"),
    TIME("Time"),
    HEART_RATE("HeartRate"),
    ;

    internal companion object {
        fun parse(value: String?): TcxTriggerMethod =
            entries.firstOrNull { it.xmlValue.equals(value?.trim(), ignoreCase = true) } ?: MANUAL
    }
}
