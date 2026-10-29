// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.format

import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportType
import org.openactivity.model.UnitConstants
import org.openactivity.model.UnitSystem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration

/** A formatted number and its unit, e.g. "5:13" and "/km". */
data class Measure(val value: String, val unit: String?, val spokenUnit: String? = unit)

/**
 * Number formatting for the detail screen. Values are rounded the way they are shown everywhere in the
 * app: distance to 2 decimals, elevation to whole metres or feet, pace to whole seconds.
 */
object DetailFormat {
    private val locale: Locale get() = Locale.getDefault()

    /** Distance in km or miles with two decimals. */
    fun distance(meters: Double, units: UnitSystem): Measure = if (units == UnitSystem.METRIC) {
        Measure(String.format(locale, "%.2f", meters / UnitConstants.METERS_PER_KILOMETER), "km", "kilometres")
    } else {
        Measure(String.format(locale, "%.2f", meters / UnitConstants.METERS_PER_MILE), "mi", "miles")
    }

    /** "52:14" below an hour, "1:02:03" above. */
    fun duration(duration: Duration): String = clock(duration.inWholeMilliseconds / 1000.0)

    /** "52:14" or "1:02:03" for [seconds]. */
    fun clock(seconds: Double): String {
        val total = seconds.roundToLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Whole metres or feet. */
    fun elevation(meters: Double, units: UnitSystem): Measure = if (units == UnitSystem.METRIC) {
        Measure(String.format(locale, "%,d", meters.roundToInt()), "m", "metres")
    } else {
        Measure(String.format(locale, "%,d", (meters / UnitConstants.METERS_PER_FOOT).roundToInt()), "ft", "feet")
    }

    /** Signed elevation change such as "+12" or "−6" (true minus sign). */
    fun elevationDelta(meters: Double, units: UnitSystem): String {
        val v = if (units == UnitSystem.METRIC) meters else meters / UnitConstants.METERS_PER_FOOT
        val rounded = v.roundToInt()
        return when {
            rounded > 0 -> "+$rounded"
            rounded < 0 -> "−${abs(rounded)}"
            else -> "0"
        }
    }

    /** Heart rate in bpm. */
    fun heartRate(bpm: Double): Measure = Measure(bpm.roundToInt().toString(), "bpm", "beats per minute")

    /**
     * Pace or speed the way [sport] prefers: time per km/mile for runs and walks, per 100 m for swims,
     * per 500 m for rowing, km/h or mph for everything else. Null when not moving.
     */
    fun speed(speedMps: Double, sport: SportType, units: UnitSystem): Measure? {
        if (!(speedMps > 0.0) || !speedMps.isFinite()) return null
        return when (sport.speedDisplay) {
            SpeedDisplay.PACE -> {
                val unitMeters = units.splitLengthMeters
                Measure(clock(unitMeters / speedMps), if (units == UnitSystem.METRIC) "/km" else "/mi", if (units == UnitSystem.METRIC) "per kilometre" else "per mile")
            }
            SpeedDisplay.PACE_PER_100M -> Measure(clock(100.0 / speedMps), "/100m", "per 100 metres")
            SpeedDisplay.PACE_PER_500M -> Measure(clock(500.0 / speedMps), "/500m", "per 500 metres")
            SpeedDisplay.SPEED -> if (units == UnitSystem.METRIC) {
                Measure(String.format(locale, "%.1f", speedMps * 3.6), "km/h", "kilometres per hour")
            } else {
                Measure(String.format(locale, "%.1f", speedMps * 3600.0 / UnitConstants.METERS_PER_MILE), "mph", "miles per hour")
            }
        }
    }

    /** "Pace" or "Speed" for [sport]. */
    fun speedLabel(sport: SportType): String = if (sport.displaysPace) "Pace" else "Speed"

    /** Header date and time in the activity's own zone, e.g. "Tuesday, June 3, 2025 at 7:12 AM". */
    fun startedAt(start: Instant, zone: ZoneId, today: LocalDate): String {
        val local = start.atZone(zone)
        val time = local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        val date = when (local.toLocalDate()) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        }
        return "$date at $time"
    }

    /** Readable best-effort name such as "5K", "1 mile" or "Half Marathon". */
    fun effortName(targetMeters: Double): String {
        NAMED_DISTANCES.entries.firstOrNull { abs(it.key - targetMeters) < 0.5 }?.let { return it.value }
        return when {
            abs(targetMeters % UnitConstants.METERS_PER_MILE) < 0.5 -> "${(targetMeters / UnitConstants.METERS_PER_MILE).roundToInt()} miles"
            targetMeters >= 1000 -> "${(targetMeters / 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.ROOT, "%.1f", it) }} km"
            else -> "${targetMeters.roundToInt()} m"
        }
    }

    /** "1 min", "1 h", "2 h" for a duration effort. */
    fun effortDuration(seconds: Double): String = when {
        seconds >= 3600 && seconds % 3600 == 0.0 -> "${(seconds / 3600).toInt()} h"
        else -> "${(seconds / 60).roundToInt()} min"
    }

    private val NAMED_DISTANCES: Map<Double, String> = mapOf(
        400.0 to "400 m",
        UnitConstants.METERS_PER_MILE / 2 to "½ mile",
        1000.0 to "1K",
        UnitConstants.METERS_PER_MILE to "1 mile",
        2 * UnitConstants.METERS_PER_MILE to "2 miles",
        5000.0 to "5K",
        10_000.0 to "10K",
        15_000.0 to "15K",
        10 * UnitConstants.METERS_PER_MILE to "10 miles",
        20_000.0 to "20K",
        21_097.5 to "Half Marathon",
        30_000.0 to "30K",
        42_195.0 to "Marathon",
        50_000.0 to "50K",
        100 * UnitConstants.METERS_PER_MILE to "100 miles",
    )
}
