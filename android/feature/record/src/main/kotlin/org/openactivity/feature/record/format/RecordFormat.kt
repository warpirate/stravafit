// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.format

import org.openactivity.model.SpeedDisplay
import org.openactivity.model.UnitConstants
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration

/** Number formatting for the Record screen. Pure, so it is unit-tested; tabular figures come from the font. */
object RecordFormat {
    private val locale: Locale get() = Locale.getDefault()

    /** "0:07", "12:47", "1:02:03". */
    fun duration(duration: Duration): String = clock(duration.inWholeSeconds.coerceAtLeast(0))

    /** "2.41" in km or miles. */
    fun distance(meters: Double, units: UnitSystem): String =
        String.format(locale, "%.2f", meters.coerceAtLeast(0.0) / units.splitLengthMeters)

    /** "km" or "mi". */
    fun distanceUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "km" else "mi"

    /** Spoken distance unit. */
    fun distanceUnitSpoken(units: UnitSystem): String = if (units == UnitSystem.METRIC) "kilometres" else "miles"

    /**
     * Pace for [speedMps] in the sport's [display] unit ("5:18"), or null when not moving or slower than an
     * hour per unit, so the screen shows a dash instead of "999:59".
     */
    fun pace(speedMps: Double, display: SpeedDisplay, units: UnitSystem): String? {
        if (!(speedMps > 0.0) || !speedMps.isFinite()) return null
        val seconds = (paceUnitMeters(display, units) / speedMps).roundToLong()
        if (seconds >= MAX_PACE_SECONDS) return null
        return clock(seconds)
    }

    /** "/km", "/mi", "/100m" or "/500m". */
    fun paceUnit(display: SpeedDisplay, units: UnitSystem): String = when (display) {
        SpeedDisplay.PACE_PER_100M -> "/100m"
        SpeedDisplay.PACE_PER_500M -> "/500m"
        else -> if (units == UnitSystem.METRIC) "/km" else "/mi"
    }

    /** Spoken pace unit. */
    fun paceUnitSpoken(display: SpeedDisplay, units: UnitSystem): String = when (display) {
        SpeedDisplay.PACE_PER_100M -> "per 100 metres"
        SpeedDisplay.PACE_PER_500M -> "per 500 metres"
        else -> if (units == UnitSystem.METRIC) "per kilometre" else "per mile"
    }

    /** "24.3" in km/h or mph. */
    fun speed(speedMps: Double, units: UnitSystem): String {
        val safe = if (speedMps.isFinite()) speedMps.coerceAtLeast(0.0) else 0.0
        return String.format(locale, "%.1f", safe * SECONDS_PER_HOUR / units.splitLengthMeters)
    }

    /** "km/h" or "mph". */
    fun speedUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "km/h" else "mph"

    /** Spoken speed unit. */
    fun speedUnitSpoken(units: UnitSystem): String =
        if (units == UnitSystem.METRIC) "kilometres per hour" else "miles per hour"

    /** "120" in metres or feet, with grouping. */
    fun elevation(meters: Double, units: UnitSystem): String {
        val value = if (units == UnitSystem.METRIC) meters else meters / UnitConstants.METERS_PER_FOOT
        return String.format(locale, "%,d", value.roundToLong())
    }

    /** "m" or "ft". */
    fun elevationUnit(units: UnitSystem): String = if (units == UnitSystem.METRIC) "m" else "ft"

    /** Spoken elevation unit. */
    fun elevationUnitSpoken(units: UnitSystem): String = if (units == UnitSystem.METRIC) "metres" else "feet"

    private fun paceUnitMeters(display: SpeedDisplay, units: UnitSystem): Double = when (display) {
        SpeedDisplay.PACE_PER_100M -> HUNDRED_METERS
        SpeedDisplay.PACE_PER_500M -> FIVE_HUNDRED_METERS
        else -> units.splitLengthMeters
    }

    private fun clock(totalSeconds: Long): String {
        val h = totalSeconds / SECONDS_PER_HOUR.toLong()
        val m = (totalSeconds % SECONDS_PER_HOUR.toLong()) / SECONDS_PER_MINUTE
        val s = totalSeconds % SECONDS_PER_MINUTE
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
    }

    private const val SECONDS_PER_HOUR = 3600.0
    private const val SECONDS_PER_MINUTE = 60L
    private const val MAX_PACE_SECONDS = 3600L
    private const val HUNDRED_METERS = 100.0
    private const val FIVE_HUNDRED_METERS = 500.0
}
