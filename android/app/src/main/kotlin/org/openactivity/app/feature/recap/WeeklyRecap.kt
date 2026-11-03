// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.repository.GoalMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * How a weekly goal did over the recap week.
 *
 * @property achieved in the goal's unit (metres, seconds, metres climbed or a count).
 */
data class GoalResult(val goal: Goal, val achieved: Double) {
    val reached: Boolean get() = achieved >= goal.target
}

/**
 * A week summed up: its totals against the week before, the longest activity, and the weekly goals.
 *
 * @property weekStart first day of the week (the app's week-start setting); the week runs seven days.
 * @property activities the week's activities, oldest first.
 */
data class WeeklyRecap(
    val weekStart: LocalDate,
    val totals: StatTotals,
    val previous: StatTotals,
    val longest: SavedActivity?,
    val goals: List<GoalResult>,
    val activities: List<SavedActivity>,
) {
    /** Last day of the week, inclusive. */
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    /** Distance against the week before in whole percent, or null when the week before had none. */
    val distanceChange: Int? get() = RecapMath.percentChange(totals.distanceMeters, previous.distanceMeters)

    /** Moving time against the week before in whole percent, or null when the week before had none. */
    val timeChange: Int? get() = RecapMath.percentChange(totals.movingTime.inWholeSeconds.toDouble(), previous.movingTime.inWholeSeconds.toDouble())
}

/**
 * Week and month boundaries and the recap sums. Pure.
 *
 * An activity belongs to the calendar day it started on in its own time zone (as everywhere else in the
 * app), so a Sunday-evening run abroad stays in its week once you are home. The week starts on the app's
 * first day of the week (Monday by default, the ISO week).
 */
object RecapMath {
    /** Local start date of [activity] in the zone it was recorded in. */
    fun localDate(activity: SavedActivity): LocalDate = activity.startTime.atZone(activity.timeZone).toLocalDate()

    /** The week that ended most recently before [now] (the device's local time): its first day. */
    fun lastCompletedWeek(now: ZonedDateTime, firstDayOfWeek: DayOfWeek): LocalDate =
        GoalMath.weekStart(now.toLocalDate(), firstDayOfWeek).minusWeeks(1)

    /** The activities of the week starting [weekStart], oldest first. */
    fun inWeek(activities: List<SavedActivity>, weekStart: LocalDate): List<SavedActivity> =
        activities.filter { localDate(it).let { d -> d >= weekStart && d < weekStart.plusWeeks(1) } }.sortedBy { it.startTime }

    /** The activities of [month], oldest first. */
    fun inMonth(activities: List<SavedActivity>, month: YearMonth): List<SavedActivity> =
        activities.filter { YearMonth.from(localDate(it)) == month }.sortedBy { it.startTime }

    /** Totals of [activities]. */
    fun totals(activities: List<SavedActivity>): StatTotals = activities.fold(StatTotals.ZERO) { acc, a ->
        acc + StatTotals(1, a.distanceMeters, a.movingTime, a.elapsedTime, a.elevationGainMeters)
    }

    /** The week starting [weekStart] summed up, with [goals]' weekly ones judged on it. */
    fun recap(all: List<SavedActivity>, goals: List<Goal>, weekStart: LocalDate): WeeklyRecap {
        val week = inWeek(all, weekStart)
        val before = inWeek(all, weekStart.minusWeeks(1))
        return WeeklyRecap(
            weekStart = weekStart,
            totals = totals(week),
            previous = totals(before),
            longest = longest(week),
            goals = goals.filter { it.period == GoalPeriod.WEEK }.map { goal ->
                GoalResult(goal, week.filter { GoalMath.counts(goal.sport, it.sport) }.sumOf { amount(it, goal.metric) })
            },
            activities = week,
        )
    }

    /** The longest by distance; by moving time when nothing had a distance (gym, yoga). */
    fun longest(activities: List<SavedActivity>): SavedActivity? =
        if (activities.any { it.distanceMeters > 0 }) activities.maxByOrNull { it.distanceMeters } else activities.maxByOrNull { it.movingTime }

    /** Whole-percent change from [before] to [now]; null without a base to compare with. */
    fun percentChange(now: Double, before: Double): Int? = if (before <= 0.0) null else ((now - before) / before * 100).roundToInt()

    /** What one activity adds to a goal of [metric]. */
    fun amount(activity: SavedActivity, metric: GoalMetric): Double = when (metric) {
        GoalMetric.DISTANCE -> activity.distanceMeters
        GoalMetric.TIME -> activity.movingTime.inWholeMilliseconds / 1000.0
        GoalMetric.ELEVATION -> activity.elevationGainMeters
        GoalMetric.COUNT -> 1.0
    }
}
