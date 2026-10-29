// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

/**
 * Per-sample data series stored with an activity. All streams of one activity share the same index:
 * element *i* of every stream describes the same sample.
 *
 * @property key stable identifier used in storage and the network API.
 * @property unit unit of each element.
 */
public enum class StreamType(public val key: String, public val unit: String) {
    /** Seconds since the activity start (elapsed, monotonic). */
    TIME("time", "s"),

    /** Cumulative distance. */
    DISTANCE("distance", "m"),

    /** Latitude/longitude pairs. */
    LATLNG("latlng", "deg"),

    /** Altitude above mean sea level. */
    ALTITUDE("altitude", "m"),

    /** Smoothed speed. */
    VELOCITY_SMOOTH("velocity_smooth", "m/s"),

    /** Heart rate. */
    HEARTRATE("heartrate", "bpm"),

    /** Cadence (crank revolutions, or steps of one foot, per minute). */
    CADENCE("cadence", "rpm"),

    /** Power. */
    WATTS("watts", "W"),

    /** Temperature. */
    TEMP("temp", "degC"),

    /** Moving/stopped classification. */
    MOVING("moving", "bool"),

    /** Smoothed grade. */
    GRADE_SMOOTH("grade_smooth", "%"),
    ;

    public companion object {
        /** Looks up a stream type by [key]; null for unknown keys. */
        public fun fromKey(key: String): StreamType? = entries.firstOrNull { it.key == key }
    }
}
