// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.notification

import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration

/** Compact number formats for the ongoing notification. Pure, locale-aware for decimals. */
object NotificationFormat {
    /** "42:10" under an hour, "1:02:03" from an hour on. */
    fun elapsed(d: Duration): String {
        val total = d.inWholeSeconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Distance in km or mi with two decimals, e.g. "7.21". */
    fun distance(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.2f", meters / units.splitLengthMeters)

    /** Pace per km or mi as "5:51", or null when not moving or slower than 99:59. */
    fun pace(speedMps: Double, units: UnitSystem): String? {
        if (speedMps <= 0.0 || !speedMps.isFinite()) return null
        val seconds = (units.splitLengthMeters / speedMps).roundToLong()
        if (seconds >= MAX_PACE_SECONDS) return null
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    /** Speed in km/h or mph with one decimal. */
    fun speed(speedMps: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", speedMps * 3600.0 / units.splitLengthMeters)

    /** True when [sport] shows pace rather than speed. */
    fun showsPace(sport: SportType?): Boolean = sport?.displaysPace ?: true

    private const val MAX_PACE_SECONDS = 100L * 60
}
