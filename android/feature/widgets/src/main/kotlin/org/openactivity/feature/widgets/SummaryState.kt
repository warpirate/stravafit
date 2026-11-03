// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.StatTotals
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import routeexport.Polyline
import routeexport.Privacy
import routeexport.TrackPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration

/**
 * What the "This week" widget shows: this week's distance, time and activity count, and the weekly goal
 * when one is set.
 *
 * @property weekStart first day of this week (the app's week-start setting).
 */
data class WeekSummary(
    val weekStart: LocalDate,
    val count: Int,
    val distanceMeters: Double,
    val movingTime: Duration,
    val goal: WeekGoal?,
    val units: UnitSystem,
)

/**
 * The weekly goal on the widget.
 *
 * @property sport the goal's sport, or null for all sports.
 * @property current and [target] are in the metric's unit: metres, seconds, metres climbed or a count.
 */
data class WeekGoal(val metric: GoalMetric, val sport: SportType?, val current: Double, val target: Double) {
    /** 0..1 for the bar; a beaten goal shows full. */
    val fraction: Float get() = (current / target).toFloat().coerceIn(0f, 1f)

    val reached: Boolean get() = current >= target
}

/**
 * Builds the "This week" state. Of several weekly goals the all-sports one is shown (it matches the totals
 * next to it), otherwise the first one; monthly and yearly goals are not this widget's business.
 */
fun weekSummary(
    weekStart: LocalDate,
    totals: StatTotals,
    progress: List<GoalProgress>,
    units: UnitSystem,
): WeekSummary {
    val weekly = progress.filter { it.goal.period == GoalPeriod.WEEK && it.periodStart == weekStart }
    val shown = weekly.firstOrNull { it.goal.sport == null } ?: weekly.minByOrNull { it.goal.id }
    return WeekSummary(
        weekStart = weekStart,
        count = totals.count,
        distanceMeters = totals.distanceMeters,
        movingTime = totals.movingTime,
        goal = shown?.let { WeekGoal(it.goal.metric, it.goal.sport, it.current, it.goal.target) },
        units = units,
    )
}

/**
 * What the "Last activity" widget shows.
 *
 * @property route the track with the owner's hidden start and end removed, or empty without GPS (or when
 *   hiding left too little to draw).
 */
data class LastActivityCard(
    val id: Long,
    val title: String,
    val sport: SportType,
    val startTime: Instant,
    val zone: ZoneId,
    val distanceMeters: Double,
    val movingTime: Duration,
    val averageSpeedMps: Double,
    val elevationGainMeters: Double,
    val units: UnitSystem,
    val route: List<TrackPoint>,
)

/** The newest activity as a [LastActivityCard]; null when there is none. */
fun lastActivityCard(activity: SavedActivity?, units: UnitSystem): LastActivityCard? {
    activity ?: return null
    val points = activity.polyline?.let { runCatching { Polyline.decode(it) }.getOrNull() }.orEmpty()
    return LastActivityCard(
        id = activity.id,
        title = activity.title,
        sport = activity.sport,
        startTime = activity.startTime,
        zone = activity.timeZone,
        distanceMeters = activity.distanceMeters,
        movingTime = activity.movingTime,
        averageSpeedMps = activity.averageSpeedMps,
        elevationGainMeters = activity.elevationGainMeters,
        units = units,
        route = hideEnds(points, activity.hideStartMeters, activity.hideEndMeters),
    )
}

/**
 * Drops the points within [startMeters] of the start and [endMeters] of the finish, as the share images
 * do, so a home screen in public never shows where you live. Fewer than two points left means no route.
 */
internal fun hideEnds(points: List<TrackPoint>, startMeters: Double, endMeters: Double): List<TrackPoint> =
    Privacy.trimEnds(points, startMeters, endMeters)

/** "Today", "Yesterday", or the weekday and date ("Mon 3 Nov") of [start] seen from [today]. */
internal fun dayLabel(start: Instant, zone: ZoneId, today: LocalDate, weekdayAndDate: (LocalDate) -> String, todayWord: String, yesterdayWord: String): String {
    val day = start.atZone(zone).toLocalDate()
    return when (day) {
        today -> todayWord
        today.minusDays(1) -> yesterdayWord
        else -> weekdayAndDate(day)
    }
}
