// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import org.openactivity.core.data.mapper.sportOf
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.core.database.entity.ActivityStatRow
import org.openactivity.model.SportType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Goal periods, progress and the weekly streak. Pure functions.
 *
 * Periods follow the device's calendar ("this week" is the week you are living in now), while each
 * activity counts on the day it started in its own time zone (as [StatsAggregator] does), so a Sunday
 * evening run abroad never jumps into next week once you are home.
 */
object GoalMath {
    /** Fraction of a goal at which the one-off "almost there" reminder fires. */
    const val REMINDER_FRACTION: Double = 0.8

    /** The period containing [today]: first day inclusive, end exclusive. */
    fun periodOf(period: GoalPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> = when (period) {
        GoalPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let { it to it.plusWeeks(1) }
        GoalPeriod.MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
        GoalPeriod.YEAR -> today.withDayOfYear(1).let { it to it.plusYears(1) }
    }

    /** First day of the week containing [date]. */
    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /**
     * Whether an activity of [sport] counts towards a goal for [goalSport]: everything for an all-sports
     * goal, every run type for runs, every ride type for rides, otherwise the same sport.
     */
    fun counts(goalSport: SportType?, sport: SportType): Boolean = when {
        goalSport == null -> true
        goalSport == SportType.RUN -> sport.isRunLike
        goalSport == SportType.RIDE -> sport.isRideLike
        else -> sport == goalSport
    }

    /** What one activity adds to a goal of [metric]. */
    fun amount(row: ActivityStatRow, metric: GoalMetric): Double = when (metric) {
        GoalMetric.DISTANCE -> row.distanceMeters
        GoalMetric.TIME -> row.movingMillis / MILLIS_PER_SECOND
        GoalMetric.ELEVATION -> row.elevationGainMeters
        GoalMetric.COUNT -> 1.0
    }

    /** Progress of [goal] in the period containing [now] (the device's local time). */
    fun progress(goal: Goal, rows: List<ActivityStatRow>, now: ZonedDateTime, firstDayOfWeek: DayOfWeek): GoalProgress {
        val today = now.toLocalDate()
        val (start, end) = periodOf(goal.period, today, firstDayOfWeek)
        val current = rows
            .filter { row -> StatsAggregator.localDate(row).let { it >= start && it < end } }
            .filter { row -> counts(goal.sport, sportOf(row.sport) ?: SportType.OTHER) }
            .sumOf { amount(it, goal.metric) }
        val periodStart = start.atStartOfDay(now.zone)
        val periodEnd = end.atStartOfDay(now.zone)
        val total = Duration.between(periodStart, periodEnd).toMillis().toDouble()
        val elapsed = Duration.between(periodStart, now).toMillis().toDouble()
        return GoalProgress(
            goal = goal,
            periodStart = start,
            periodEnd = end,
            current = current,
            elapsedFraction = (elapsed / total).coerceIn(0.0, 1.0),
            daysLeft = ChronoUnit.DAYS.between(today, end).toInt(),
        )
    }

    /**
     * Consecutive weeks, ending with this one, with at least one activity on [activityDays]. A week that
     * has not had an activity yet does not end the streak; it just does not add to it.
     */
    fun weeklyStreak(activityDays: Collection<LocalDate>, today: LocalDate, firstDayOfWeek: DayOfWeek): WeeklyStreak {
        val current = weekStart(today, firstDayOfWeek)
        val weeks = activityDays.map { weekStart(it, firstDayOfWeek) }.filter { it <= current }.toSet()
        val activeThisWeek = current in weeks
        var cursor = if (activeThisWeek) current else current.minusWeeks(1)
        var count = 0
        while (cursor in weeks) {
            count++
            cursor = cursor.minusWeeks(1)
        }
        return WeeklyStreak(count, activeThisWeek)
    }

    private const val MILLIS_PER_SECOND = 1_000.0
}
