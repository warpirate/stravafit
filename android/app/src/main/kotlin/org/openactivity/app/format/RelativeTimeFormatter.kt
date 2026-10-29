// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.format

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Activity timestamps per home-feed detail 2.2:
 * - under 1 h: "N min ago"
 * - today: "Today at h:mm"
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
        val age = Duration.between(then, now)
        if (!age.isNegative && age < Duration.ofHours(1)) {
            val minutes = age.toMinutes().coerceAtLeast(1)
            return "$minutes min ago"
        }
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

    private companion object {
        const val WEEK_DAYS = 6L
    }
}
