// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import org.openactivity.core.recordingservice.notification.NotificationFormat
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration

/**
 * Numbers on the widget. Built on [NotificationFormat] so the widget and the notification never
 * disagree; the widget adds the stricter "stopped" rule for pace.
 */
object WidgetFormat {
    /** Shown instead of a pace when stopped or crawling; never a nonsense number like 59:59. */
    const val NO_PACE: String = "--:--"

    /** "5:02", "42:10", "1:02:03". */
    fun clock(d: Duration): String = NotificationFormat.elapsed(d)

    /** "7.89" (or "7,89" where the decimal separator is a comma). */
    fun distance(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String =
        NotificationFormat.distance(meters, units, locale)

    /**
     * Pace for foot sports, speed for the rest. Pace slower than [MAX_PACE_SECONDS] per unit counts as stopped
     * and shows [NO_PACE]; speed shows "0.0" when stopped.
     */
    fun speedValue(speedMps: Double, sport: SportType, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        if (!NotificationFormat.showsPace(sport)) return NotificationFormat.speed(speedMps.coerceAtLeast(0.0), units, locale)
        if (speedMps <= 0.0 || !speedMps.isFinite()) return NO_PACE
        val seconds = (units.splitLengthMeters / speedMps).roundToLong()
        if (seconds > MAX_PACE_SECONDS) return NO_PACE
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    /** TalkBack text for a clock: "25 minutes 2 seconds", not "25 colon 02". */
    fun spokenClock(d: Duration, hours: (Long) -> String, minutes: (Long) -> String, seconds: (Long) -> String): String {
        val total = d.inWholeSeconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let(hours),
            m.takeIf { it > 0 || h > 0 }?.let(minutes),
            seconds(s),
        ).joinToString(" ")
    }

    /** 30:00 per km or mile: anything slower is standing around, not running or walking. */
    const val MAX_PACE_SECONDS: Long = 30L * 60
}
