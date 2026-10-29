// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToLong

/** Conversions between FIT wire units and SI / java.time values. */
public object FitUnits {
    /** Unix time of the FIT epoch, 1989-12-31T00:00:00Z. */
    public const val FIT_EPOCH_UNIX_SECONDS: Long = 631_065_600L

    /** Timestamps below this value are relative (seconds since device power-on), not absolute. */
    public const val MIN_ABSOLUTE_TIMESTAMP: Long = 0x1000_0000L

    private const val SEMICIRCLES_PER_DEGREE = 2_147_483_648.0 / 180.0

    /** FIT `date_time` (seconds since the FIT epoch) for [instant]; sub-second precision is dropped. */
    public fun toFitTime(instant: Instant): Long {
        val seconds = instant.epochSecond - FIT_EPOCH_UNIX_SECONDS
        require(seconds in 0..0xFFFF_FFFEL) { "$instant is outside the FIT date_time range" }
        return seconds
    }

    /** The instant for a FIT `date_time` value. */
    public fun fromFitTime(fitSeconds: Long): Instant = Instant.ofEpochSecond(fitSeconds + FIT_EPOCH_UNIX_SECONDS)

    /** FIT `local_date_time` for a wall-clock time. */
    public fun toFitLocalTime(local: LocalDateTime): Long =
        local.toEpochSecond(ZoneOffset.UTC) - FIT_EPOCH_UNIX_SECONDS

    /** Wall-clock time for a FIT `local_date_time` value. */
    public fun fromFitLocalTime(fitSeconds: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(fitSeconds + FIT_EPOCH_UNIX_SECONDS, 0, ZoneOffset.UTC)

    /** Degrees to semicircles (`deg × 2^31 / 180`), rounded to the nearest integer. */
    public fun toSemicircles(degrees: Double): Int {
        require(degrees in -180.0..180.0) { "Degrees out of range: $degrees" }
        return (degrees * SEMICIRCLES_PER_DEGREE).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE - 1L).toInt()
    }

    /** Semicircles to degrees. */
    public fun fromSemicircles(semicircles: Long): Double = semicircles / SEMICIRCLES_PER_DEGREE
}
