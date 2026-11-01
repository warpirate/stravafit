// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.model.SportType
import java.time.LocalDate

/** How long a goal runs before it starts again. */
enum class GoalPeriod { WEEK, MONTH, YEAR }

/**
 * What a goal counts. [COUNT] (number of activities) is offered for weekly goals only.
 */
enum class GoalMetric {
    /** Metres. */
    DISTANCE,

    /** Moving time, in seconds. */
    TIME,

    /** Metres climbed. */
    ELEVATION,

    /** Number of activities. */
    COUNT,
    ;

    /** Whether this metric can be chosen for [period]. */
    fun allowedFor(period: GoalPeriod): Boolean = this != COUNT || period == GoalPeriod.WEEK
}

/**
 * A training goal: [target] of [metric] in every [period].
 *
 * @property sport the sport it counts (runs include trail and virtual runs, rides every kind of ride), or
 *   null for all sports together.
 * @property target in the unit of [metric]: metres, seconds or a count.
 */
data class Goal(
    val id: Long = 0,
    val sport: SportType?,
    val period: GoalPeriod,
    val metric: GoalMetric,
    val target: Double,
) {
    init {
        require(target > 0.0 && target.isFinite()) { "target must be positive" }
        require(metric.allowedFor(period)) { "$metric goals are weekly only" }
    }
}

/**
 * Where a goal stands in its current period.
 *
 * @property periodStart first day of the period (inclusive), in the device's calendar.
 * @property periodEnd first day after the period (exclusive).
 * @property current what has been done so far, in the unit of the goal's metric.
 * @property elapsedFraction how much of the period has passed, 0..1.
 * @property daysLeft whole days left, today included.
 */
data class GoalProgress(
    val goal: Goal,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val current: Double,
    val elapsedFraction: Double,
    val daysLeft: Int,
) {
    /** Progress towards the target; above 1 once it is beaten. */
    val fraction: Double get() = current / goal.target

    val reached: Boolean get() = current >= goal.target

    /** Where an even pace would be by now (training-analytics 10, goal-state machine). */
    val expected: Double get() = goal.target * elapsedFraction

    /** On track: at or ahead of the even pace. */
    val onPace: Boolean get() = reached || current >= expected

    /** What is still missing, never negative. */
    val remaining: Double get() = (goal.target - current).coerceAtLeast(0.0)
}

/**
 * Consecutive weeks with at least one activity.
 *
 * @property weeks the streak length. The current week counts once it has an activity, and does not break
 *   the streak while it is still empty.
 * @property activeThisWeek whether this week already has an activity.
 */
data class WeeklyStreak(val weeks: Int, val activeThisWeek: Boolean) {
    companion object {
        val NONE: WeeklyStreak = WeeklyStreak(0, false)
    }
}
