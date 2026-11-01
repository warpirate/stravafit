// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.format

import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Activity timestamps (strava direction):
 * - today: "Today at h:mm" (also within the last hour)
 * - yesterday: "Yesterday at h:mm"
 * - within 7 days: weekday and time ("Monday at 7:02")
 * - older: "d MMM · h:mm", with the year only when it is not the current year.
 *
 * Uses the device locale and its 12/24-hour setting ([use24Hour]).
 */
class RelativeTimeFormatter(
    private val clock: Clock,
    private val locale: Locale = Locale.getDefault(),
    private val use24Hour: Boolean = false,
) {
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    /** Formats [instant] relative to the injected clock. */
    fun format(instant: Instant): String {
        val now = ZonedDateTime.now(clock)
        val then = instant.atZone(clock.zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
        val clockTime = time.format(then)
        return when {
            days == 0L -> "Today at $clockTime"
            days == 1L -> "Yesterday at $clockTime"
            days in 2..WEEK_DAYS -> "${then.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} at $clockTime"
            then.year == now.year -> "${dayMonth.format(then)} · $clockTime"
            else -> "${dayMonthYear.format(then)} · $clockTime"
        }
    }

    /** Short date for activity lists, e.g. "12 Jun". */
    fun shortDate(instant: Instant): String {
        val then = instant.atZone(clock.zone)
        val now = ZonedDateTime.now(clock)
        return if (then.year == now.year) dayMonth.format(then) else dayMonthYear.format(then)
    }

    /**
     * Age of an inbox entry (notifications-search N3): "now", "5m", "2h", "3d", then a short date
     * ("12 Jun", with the year when it is not this year).
     */
    fun age(instant: Instant): String {
        val now = clock.instant()
        val minutes = java.time.Duration.between(instant, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "now"
            minutes < MINUTES_PER_HOUR -> "${minutes}m"
            minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR}h"
            minutes < MINUTES_PER_DAY * AGE_DAYS -> "${minutes / MINUTES_PER_DAY}d"
            else -> shortDate(instant)
        }
    }

    private companion object {
        const val WEEK_DAYS = 6L
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 1_440L
        const val AGE_DAYS = 7L
    }
}
