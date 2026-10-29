// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.format

import org.openactivity.model.UnitSystem
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** A formatted number with its display unit and the unit as TalkBack should read it. */
data class Measure(val value: String, val unit: String?, val spokenUnit: String? = unit)

/**
 * Formats distances, paces, elevations and durations in the viewer's unit system.
 *
 * This is the app-shell stand-in for the `:core:i18n` units layer in the blueprint (section 8).
 */
class UnitFormatter(private val units: UnitSystem, private val locale: Locale = Locale.getDefault()) {

    /** "8.02 km" below 100 units, "123.4 km" below 1,000, then whole units ("1,204 km"). */
    fun distance(meters: Double): Measure {
        val (amount, unit, spoken) = when (units) {
            UnitSystem.METRIC -> Triple(meters / METERS_PER_KM, "km", "kilometres")
            UnitSystem.IMPERIAL -> Triple(meters / METERS_PER_MILE, "mi", "miles")
        }
        // Pick the precision from the value as it will be shown, so 99.996 km reads "100.0 km"
        // rather than "100.00 km", and 999.96 km reads "1,000 km".
        val decimals = when {
            roundTo(amount, 2) < LARGE_DISTANCE -> 2
            roundTo(amount, 1) < HUGE_DISTANCE -> 1
            else -> 0
        }
        return Measure(decimal(amount, decimals), unit, spoken)
    }

    /** Swim distance in whole metres or yards. */
    fun swimDistance(meters: Double): Measure = when (units) {
        UnitSystem.METRIC -> Measure(integer(meters), "m", "metres")
        UnitSystem.IMPERIAL -> Measure(integer(meters / METERS_PER_YARD), "yd", "yards")
    }

    /** Pace per kilometre or mile, e.g. "5:12" "/km". */
    fun pace(meters: Double, seconds: Long): Measure? {
        if (meters <= 0.0 || seconds <= 0) return null
        val perUnit = when (units) {
            UnitSystem.METRIC -> seconds / (meters / METERS_PER_KM)
            UnitSystem.IMPERIAL -> seconds / (meters / METERS_PER_MILE)
        }
        val unit = if (units == UnitSystem.METRIC) "/km" else "/mi"
        val spoken = if (units == UnitSystem.METRIC) "per kilometre" else "per mile"
        return Measure(minutesSeconds(perUnit.roundToLong()), unit, spoken)
    }

    /** Swim pace per 100 m or 100 yd. */
    fun swimPace(meters: Double, seconds: Long): Measure? {
        if (meters <= 0.0 || seconds <= 0) return null
        val unitMeters = if (units == UnitSystem.METRIC) HUNDRED_METERS else HUNDRED_METERS * METERS_PER_YARD
        val perHundred = seconds / (meters / unitMeters)
        val unit = if (units == UnitSystem.METRIC) "/100m" else "/100yd"
        val spoken = if (units == UnitSystem.METRIC) "per 100 metres" else "per 100 yards"
        return Measure(minutesSeconds(perHundred.roundToLong()), unit, spoken)
    }

    /** Elevation in metres or feet, grouped ("1,102 m"). */
    fun elevation(meters: Double): Measure = when (units) {
        UnitSystem.METRIC -> Measure(integer(meters), "m", "metres")
        UnitSystem.IMPERIAL -> Measure(integer(meters / METERS_PER_FOOT), "ft", "feet")
    }

    /** Speed in km/h or mph with one decimal, e.g. "24.3" "km/h". Null when not moving. */
    fun speed(metersPerSecond: Double): Measure? {
        if (metersPerSecond <= 0.0 || !metersPerSecond.isFinite()) return null
        return when (units) {
            UnitSystem.METRIC -> Measure(decimal(metersPerSecond * SECONDS_PER_HOUR / METERS_PER_KM, 1), "km/h", "kilometres per hour")
            UnitSystem.IMPERIAL -> Measure(decimal(metersPerSecond * SECONDS_PER_HOUR / METERS_PER_MILE, 1), "mph", "miles per hour")
        }
    }

    /** Short distance for radii and thresholds: "200 m" or "650 ft". */
    fun shortDistance(meters: Double): Measure = elevation(meters)

    /** Integer with locale grouping. */
    fun count(value: Number): String = NumberFormat.getIntegerInstance(locale).format(value)

    private fun decimal(value: Double, decimals: Int): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(value)

    private fun roundTo(value: Double, decimals: Int): Double =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()

    private fun integer(value: Double): String = NumberFormat.getIntegerInstance(locale).format(value.roundToLong())

    companion object {
        const val METERS_PER_KM = 1000.0
        const val METERS_PER_MILE = 1609.344
        const val METERS_PER_FOOT = 0.3048
        const val METERS_PER_YARD = 0.9144
        private const val HUNDRED_METERS = 100.0
        private const val LARGE_DISTANCE = 100.0
        private const val HUGE_DISTANCE = 1000.0

        /** "53:04" under an hour, "2h 31m" from an hour up. */
        fun duration(seconds: Long): String {
            val h = seconds / SECONDS_PER_HOUR
            val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            val s = seconds % SECONDS_PER_MINUTE
            return if (h > 0) "${h}h ${m}m" else "%d:%02d".format(Locale.ROOT, m, s)
        }

        /** Compact total for summaries: "3h 10m", "45m". */
        fun compactDuration(seconds: Long): String {
            val h = seconds / SECONDS_PER_HOUR
            val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

        private fun minutesSeconds(totalSeconds: Long): String =
            "%d:%02d".format(Locale.ROOT, totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)

        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3600L
    }
}
