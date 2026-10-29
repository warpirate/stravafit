// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import org.openactivity.core.data.mapper.sportOf
import org.openactivity.core.data.mapper.zoneOf
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeekTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.core.database.entity.ActivityStatRow
import org.openactivity.model.SportType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.time.Duration.Companion.milliseconds

/**
 * Buckets activities into weeks, years and sports. Pure functions over [ActivityStatRow]s.
 *
 * An activity belongs to the calendar day on which it started in its own time zone, so a Monday-morning
 * run recorded abroad stays on Monday when you are back home.
 */
object StatsAggregator {
    /** Local start date of [row] in the zone it was recorded in. */
    fun localDate(row: ActivityStatRow): LocalDate =
        Instant.ofEpochMilli(row.startEpochMillis).atZone(zoneOf(row.timeZoneId)).toLocalDate()

    /** Totals of [rows]. */
    fun totals(rows: List<ActivityStatRow>): StatTotals = rows.fold(StatTotals.ZERO) { acc, r -> acc + r.toTotals() }

    /**
     * Totals for the [weeks] most recent weeks ending with the week that contains [today], oldest first.
     * Weeks without activities are present with zero totals, so charts have a stable x axis.
     */
    fun weekly(rows: List<ActivityStatRow>, weeks: Int, today: LocalDate, firstDayOfWeek: DayOfWeek): List<WeekTotals> {
        require(weeks > 0) { "weeks must be positive" }
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val starts = (weeks - 1 downTo 0).map { currentWeek.minusWeeks(it.toLong()) }
        val byWeek = rows.groupBy { localDate(it).with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }
        return starts.map { start -> WeekTotals(start, totals(byWeek[start].orEmpty())) }
    }

    /** Totals for calendar [year], overall and per sport. */
    fun year(rows: List<ActivityStatRow>, year: Int): YearTotals {
        val inYear = rows.filter { localDate(it).year == year }
        return YearTotals(year, totals(inYear), bySport(inYear))
    }

    /** Totals per sport. Rows with an unknown sport count as [SportType.OTHER]. */
    fun bySport(rows: List<ActivityStatRow>): Map<SportType, StatTotals> =
        rows.groupBy { sportOf(it.sport) ?: SportType.OTHER }.mapValues { (_, list) -> totals(list) }

    private fun ActivityStatRow.toTotals() = StatTotals(
        count = 1,
        distanceMeters = distanceMeters,
        movingTime = movingMillis.milliseconds,
        elapsedTime = elapsedMillis.milliseconds,
        elevationGainMeters = elevationGainMeters,
    )
}
