// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Exact conversion constants (international yard and pound agreement, 1959). */
public object UnitConstants {
    /** Metres in one statute mile. */
    public const val METERS_PER_MILE: Double = 1609.344

    /** Metres in one international foot. */
    public const val METERS_PER_FOOT: Double = 0.3048

    /** Metres in one kilometre. */
    public const val METERS_PER_KILOMETER: Double = 1000.0
}

/**
 * The user's preferred measurement system.
 *
 * @property splitLengthMeters length of one auto split in metres (1 km or 1 mile).
 */
public enum class UnitSystem(public val splitLengthMeters: Double) {
    METRIC(UnitConstants.METERS_PER_KILOMETER),
    IMPERIAL(UnitConstants.METERS_PER_MILE),
}

/** A distance in metres. */
@JvmInline
public value class Distance(public val meters: Double) : Comparable<Distance> {
    /** Distance in kilometres. */
    public val kilometers: Double get() = meters / UnitConstants.METERS_PER_KILOMETER

    /** Distance in statute miles. */
    public val miles: Double get() = meters / UnitConstants.METERS_PER_MILE

    /** Distance in feet. */
    public val feet: Double get() = meters / UnitConstants.METERS_PER_FOOT

    public operator fun plus(other: Distance): Distance = Distance(meters + other.meters)

    public operator fun minus(other: Distance): Distance = Distance(meters - other.meters)

    override fun compareTo(other: Distance): Int = meters.compareTo(other.meters)

    public companion object {
        /** No distance. */
        public val ZERO: Distance = Distance(0.0)

        /** A distance of [value] kilometres. */
        public fun kilometers(value: Double): Distance = Distance(value * UnitConstants.METERS_PER_KILOMETER)

        /** A distance of [value] statute miles. */
        public fun miles(value: Double): Distance = Distance(value * UnitConstants.METERS_PER_MILE)
    }
}

/** A speed in metres per second. */
@JvmInline
public value class Speed(public val metersPerSecond: Double) : Comparable<Speed> {
    /** Speed in km/h. */
    public val kilometersPerHour: Double get() = metersPerSecond * 3.6

    /** Speed in mph. */
    public val milesPerHour: Double get() = metersPerSecond * 3600.0 / UnitConstants.METERS_PER_MILE

    /**
     * Time needed to cover [distance] at this speed; null when the speed is not positive and finite,
     * because a pace is undefined while standing still.
     */
    public fun paceFor(distance: Distance): Duration? =
        if (metersPerSecond > 0.0 && metersPerSecond.isFinite()) {
            (distance.meters / metersPerSecond * 1e9).toLong().nanoseconds
        } else {
            null
        }

    /** Pace per kilometre or per mile for [units]; null when not moving. */
    public fun pace(units: UnitSystem): Duration? = paceFor(Distance(units.splitLengthMeters))

    override fun compareTo(other: Speed): Int = metersPerSecond.compareTo(other.metersPerSecond)

    public companion object {
        /** Standing still. */
        public val ZERO: Speed = Speed(0.0)

        /** The speed that covers [distance] in [time]; [ZERO] when [time] is not positive. */
        public fun of(distance: Distance, time: Duration): Speed {
            val seconds = time.inWholeNanoseconds / 1e9
            return if (seconds > 0.0) Speed(distance.meters / seconds) else ZERO
        }

        /** The speed equivalent to a pace of [pace] per [distance]. */
        public fun fromPace(pace: Duration, distance: Distance): Speed = of(distance, pace)
    }
}
