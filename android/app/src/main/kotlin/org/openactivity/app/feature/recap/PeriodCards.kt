// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.StatTotals
import routeexport.CardStat
import routeexport.PeriodCard
import routeexport.Polyline
import routeexport.Privacy
import routeexport.TrackPoint
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the shareable period cards (route-art [PeriodCard]): the monthly "month in sport" card and the
 * weekly recap image. Numbers are formatted like the rest of the app; every route has the owner's hidden
 * start and finish removed before it is drawn. Pure, so the layout inputs are unit-tested.
 */
object PeriodCards {
    /** The month's totals and a grid of its route shapes. */
    fun month(month: YearMonth, activities: List<SavedActivity>, formatter: UnitFormatter, locale: Locale = Locale.getDefault()): PeriodCard {
        val inMonth = RecapMath.inMonth(activities, month)
        val routes = inMonth.map(::route)
        return PeriodCard(
            kicker = "Month in sport",
            title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            stats = stats(RecapMath.totals(inMonth), formatter),
            routes = routes.filter { it.isNotEmpty() },
            withoutRoute = routes.count { it.isEmpty() },
        )
    }

    /** The recap week: its totals, how it compares with the week before, and its route shapes. */
    fun week(recap: WeeklyRecap, formatter: UnitFormatter, locale: Locale = Locale.getDefault()): PeriodCard {
        val routes = recap.activities.map(::route)
        return PeriodCard(
            kicker = "Weekly recap",
            title = weekRange(recap.weekStart, locale),
            stats = stats(recap.totals, formatter),
            note = comparison(recap),
            routes = routes.filter { it.isNotEmpty() },
            withoutRoute = routes.count { it.isEmpty() },
        )
    }

    /** "Activities 18 · Distance 142 km · Time 14h 2m · Elevation 1,204 m"; distance and climbing only when there are any. */
    fun stats(totals: StatTotals, formatter: UnitFormatter): List<CardStat> = buildList {
        add(CardStat("Activities", formatter.count(totals.count)))
        if (totals.distanceMeters > 0) formatter.distance(totals.distanceMeters).let { add(CardStat("Distance", it.value, it.unit.orEmpty())) }
        add(CardStat("Time", UnitFormatter.compactDuration(totals.movingTime.inWholeSeconds)))
        if (totals.elevationGainMeters >= 1) formatter.elevation(totals.elevationGainMeters).let { add(CardStat("Elevation", it.value, it.unit.orEmpty())) }
    }

    /** "12% more distance than the week before", "Same distance as the week before", or the first active week. */
    fun comparison(recap: WeeklyRecap): String {
        val change = recap.distanceChange ?: recap.timeChange
        val what = if (recap.distanceChange != null) "distance" else "time"
        return when {
            recap.previous.count == 0 -> "Nothing the week before. Good to be back."
            change == null -> ""
            change > 0 -> "$change% more $what than the week before"
            change < 0 -> "${-change}% less $what than the week before"
            else -> "Same $what as the week before"
        }
    }

    /** "26 Oct – 1 Nov", "29 Dec 2025 – 4 Jan 2026" across a new year. */
    fun weekRange(weekStart: LocalDate, locale: Locale = Locale.getDefault()): String {
        val end = weekStart.plusDays(6)
        val day = DateTimeFormatter.ofPattern("d MMM", locale)
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        return if (weekStart.year == end.year) "${weekStart.format(day)} – ${end.format(day)}" else "${weekStart.format(full)} – ${end.format(full)}"
    }

    /** The drawable route of [activity] with its hidden start and finish removed; empty without one. */
    fun route(activity: SavedActivity): List<TrackPoint> {
        val points = activity.polyline?.let { runCatching { Polyline.decode(it) }.getOrNull() }.orEmpty()
        return Privacy.trimEnds(points, activity.hideStartMeters, activity.hideEndMeters)
    }
}
