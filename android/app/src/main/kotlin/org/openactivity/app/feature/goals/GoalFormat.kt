// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.goals

import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.label
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import kotlin.math.roundToLong

/** Words and numbers for goals, shared by the goal cards, the goals screen and the inbox. Pure. */
internal object GoalFormat {
    /** Metres in a kilometre or a mile, for converting typed targets. */
    fun distanceUnitMeters(units: UnitSystem): Double = if (units == UnitSystem.METRIC) 1_000.0 else METERS_PER_MILE

    /** Metres in the elevation unit (m or ft). */
    fun elevationUnitMeters(units: UnitSystem): Double = if (units == UnitSystem.METRIC) 1.0 else METERS_PER_FOOT

    /** "Weekly", "Monthly", "Yearly". */
    fun periodAdjective(period: GoalPeriod): String = when (period) {
        GoalPeriod.WEEK -> "Weekly"
        GoalPeriod.MONTH -> "Monthly"
        GoalPeriod.YEAR -> "Yearly"
    }

    /** "this week", "this month", "this year". */
    fun periodPhrase(period: GoalPeriod): String = when (period) {
        GoalPeriod.WEEK -> "this week"
        GoalPeriod.MONTH -> "this month"
        GoalPeriod.YEAR -> "this year"
    }

    /** "Distance", "Time", "Elevation", "Activities". */
    fun metricLabel(metric: GoalMetric): String = when (metric) {
        GoalMetric.DISTANCE -> "Distance"
        GoalMetric.TIME -> "Time"
        GoalMetric.ELEVATION -> "Elevation"
        GoalMetric.COUNT -> "Activities"
    }

    /** "Weekly run goal", "Yearly goal" (all sports). */
    fun title(goal: Goal): String {
        val sport = goal.sport?.let { " ${sportNoun(it)}" }.orEmpty()
        return "${periodAdjective(goal.period)}$sport goal"
    }

    /** "run", "ride", "swim"...: the sport as it reads inside a sentence. */
    fun sportNoun(sport: SportType): String = sport.label.lowercase()

    /** A value of [metric] with its unit: "30 km", "5h 30m", "1,200 m", "4 activities". */
    fun amount(metric: GoalMetric, value: Double, formatter: UnitFormatter): String = when (metric) {
        GoalMetric.DISTANCE -> formatter.distance(value).let { "${trimZeros(it.value)} ${it.unit}" }
        GoalMetric.TIME -> hoursMinutes(value)
        GoalMetric.ELEVATION -> formatter.elevation(value).let { "${it.value} ${it.unit}" }
        GoalMetric.COUNT -> value.roundToLong().let { if (it == 1L) "1 activity" else "${formatter.count(it)} activities" }
    }

    /** The bare number, without its unit ("24.1" of "24.1 km"), for "24.1 / 30 km". */
    fun number(metric: GoalMetric, value: Double, formatter: UnitFormatter): String = when (metric) {
        GoalMetric.DISTANCE -> trimZeros(formatter.distance(value).value)
        GoalMetric.TIME -> hoursMinutes(value)
        GoalMetric.ELEVATION -> formatter.elevation(value).value
        GoalMetric.COUNT -> formatter.count(value.roundToLong())
    }

    /** "24.1 / 30 km" (time reads "2h 10m / 5h"). */
    fun ofTarget(progress: GoalProgress, formatter: UnitFormatter): String {
        val metric = progress.goal.metric
        return if (metric == GoalMetric.TIME) {
            "${amount(metric, progress.current, formatter)} / ${amount(metric, progress.goal.target, formatter)}"
        } else {
            "${number(metric, progress.current, formatter)} / ${amount(metric, progress.goal.target, formatter)}"
        }
    }

    /** "Goal reached", "5.9 km to go", or "On pace" style status under a goal bar. */
    fun status(progress: GoalProgress, formatter: UnitFormatter): String = when {
        progress.reached -> "Goal reached"
        else -> "${amount(progress.goal.metric, progress.remaining, formatter)} to go"
    }

    /** "4 days left", "1 day left", "Last day". */
    fun daysLeft(progress: GoalProgress): String = when (progress.daysLeft) {
        0, 1 -> "Last day"
        else -> "${progress.daysLeft} days left"
    }

    /** "On pace" or "Behind pace": the expected-progress comparison from the goal-state machine. */
    fun pace(progress: GoalProgress): String = if (progress.onPace) "On pace" else "Behind pace"

    /** Progress as a whole percentage, capped at 100 for display. */
    fun percent(progress: GoalProgress): Int = (progress.fraction.coerceIn(0.0, 1.0) * 100).toInt()

    /** The target as the editor shows it: km or mi for distance, hours for time, m or ft for elevation. */
    fun editableTarget(metric: GoalMetric, target: Double, units: UnitSystem): Double = when (metric) {
        GoalMetric.DISTANCE -> target / distanceUnitMeters(units)
        GoalMetric.TIME -> target / SECONDS_PER_HOUR
        GoalMetric.ELEVATION -> target / elevationUnitMeters(units)
        GoalMetric.COUNT -> target
    }

    /** The stored target from what the editor shows (the inverse of [editableTarget]). */
    fun storedTarget(metric: GoalMetric, shown: Double, units: UnitSystem): Double = when (metric) {
        GoalMetric.DISTANCE -> shown * distanceUnitMeters(units)
        GoalMetric.TIME -> shown * SECONDS_PER_HOUR
        GoalMetric.ELEVATION -> shown * elevationUnitMeters(units)
        GoalMetric.COUNT -> shown
    }

    /** Unit after the target field: "km", "mi", "hours", "m", "ft", "activities". */
    fun editorUnit(metric: GoalMetric, units: UnitSystem): String = when (metric) {
        GoalMetric.DISTANCE -> if (units == UnitSystem.METRIC) "km" else "mi"
        GoalMetric.TIME -> "hours"
        GoalMetric.ELEVATION -> if (units == UnitSystem.METRIC) "m" else "ft"
        GoalMetric.COUNT -> "activities"
    }

    /**
     * "30.00" to "30", "24.10" to "24.1" (either decimal separator): goal targets are round numbers.
     * Only a two-digit fraction is touched, so a thousands group ("2,000") never is.
     */
    internal fun trimZeros(number: String): String {
        val sep = number.indexOfLast { it == '.' || it == ',' }
        val fraction = if (sep < 0) "" else number.substring(sep + 1)
        if (fraction.length != 2 || fraction.any { !it.isDigit() }) return number
        return number.trimEnd('0').let { if (it.endsWith('.') || it.endsWith(',')) it.dropLast(1) else it }
    }

    /** "5h", "2h 10m", "45m". */
    private fun hoursMinutes(seconds: Double): String {
        val total = seconds.roundToLong()
        val minutes = (total % SECONDS_PER_HOUR.toLong()) / SECONDS_PER_MINUTE
        val hours = total / SECONDS_PER_HOUR.toLong()
        return if (hours > 0 && minutes == 0L) "${hours}h" else UnitFormatter.compactDuration(total)
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val METERS_PER_MILE = 1_609.344
    private const val METERS_PER_FOOT = 0.3048
    private const val SECONDS_PER_HOUR = 3_600.0
}
