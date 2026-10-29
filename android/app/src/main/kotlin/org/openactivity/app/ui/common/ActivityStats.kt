// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import org.openactivity.app.format.Measure
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportCategory
import kotlin.math.roundToInt

/**
 * The three card stats for [activity], chosen by sport (core-screens 1.2) and formatted in the
 * viewer's units. Unknown values become null, which renders as a dash, never "0".
 *
 * - pace sports (run, walk, hike): distance, pace, time
 * - rides: distance, elevation gain, time
 * - swims: distance in m/yd, pace per 100, time
 * - other GPS sports: distance, speed, time
 * - activities without distance (indoor): time, average heart rate, elevation or elapsed time
 */
fun activityStats(activity: SavedActivity, formatter: UnitFormatter): List<Stat> {
    // Rounded like the activity detail and the Save screen, so every surface shows the same time.
    val exactSeconds = activity.movingTime.inWholeMilliseconds / 1000.0
    val seconds = Math.round(exactSeconds)
    val distance = activity.distanceMeters.takeIf { it > 0.0 }
    val time = Stat("Time", UnitFormatter.duration(seconds))
    if (distance == null) {
        return listOf(
            time,
            Stat("Avg HR", activity.averageHeartRate?.roundToInt()?.toString(), "bpm", "beats per minute"),
            Stat("Elapsed", UnitFormatter.duration(Math.round(activity.elapsedTime.inWholeMilliseconds / 1000.0))),
        )
    }
    val sport = activity.sport
    return when {
        sport.category == SportCategory.BIKE -> listOf(
            formatter.distance(distance).toStat("Distance"),
            formatter.elevation(activity.elevationGainMeters).toStat("Elev Gain"),
            time,
        )
        sport.speedDisplay == SpeedDisplay.PACE -> listOf(
            formatter.distance(distance).toStat("Distance"),
            formatter.pace(distance, exactSeconds).toStat("Pace"),
            time,
        )
        sport.speedDisplay == SpeedDisplay.PACE_PER_100M -> listOf(
            formatter.swimDistance(distance).toStat("Distance"),
            formatter.swimPace(distance, exactSeconds).toStat("Pace"),
            time,
        )
        else -> listOf(
            formatter.distance(distance).toStat("Distance"),
            formatter.speed(activity.averageSpeedMps).toStat("Speed"),
            time,
        )
    }
}

/**
 * "8.02 km · 5:12 /km · 41:40": the non-empty stats on one line, for list rows. Elapsed time is
 * left out, since a second bare duration next to moving time would be ambiguous.
 */
fun List<Stat>.statsLine(): String = filter { it.value != null && it.label != "Elapsed" }.joinToString(" · ") { stat ->
    val unit = stat.unit?.let { if (it.startsWith("/")) it else " $it" }.orEmpty()
    "${stat.value}$unit"
}

/** Converts a formatted [Measure] to a labelled [Stat]; null renders as a dash. */
fun Measure?.toStat(label: String): Stat =
    if (this == null) Stat(label, null) else Stat(label, value, unit, spokenUnit)
