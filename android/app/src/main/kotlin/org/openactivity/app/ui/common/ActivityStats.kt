// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import org.openactivity.app.format.Measure
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.Activity
import org.openactivity.app.model.StatTriplet
import org.openactivity.core.designsystem.component.Stat

/**
 * The three card stats for [activity], chosen by sport (core-screens 1.2) and formatted in the
 * viewer's units. Unknown values become null, which renders as a dash.
 */
fun activityStats(activity: Activity, formatter: UnitFormatter): List<Stat> {
    val distance = activity.distanceMeters
    val time = Stat("Time", UnitFormatter.duration(activity.movingTimeSeconds))
    return when (activity.sport.triplet) {
        StatTriplet.DistancePaceTime -> listOf(
            distance?.let { formatter.distance(it) }.toStat("Distance"),
            distance?.let { formatter.pace(it, activity.movingTimeSeconds) }.toStat("Pace"),
            time,
        )
        StatTriplet.DistanceElevationTime -> listOf(
            distance?.let { formatter.distance(it) }.toStat("Distance"),
            activity.elevationGainMeters?.let { formatter.elevation(it) }.toStat("Elev gain"),
            time,
        )
        StatTriplet.SwimDistancePaceTime -> listOf(
            distance?.let { formatter.swimDistance(it) }.toStat("Distance"),
            distance?.let { formatter.swimPace(it, activity.movingTimeSeconds) }.toStat("Pace"),
            time,
        )
        StatTriplet.TimeHeartRateCalories -> listOf(
            time,
            Stat("Avg HR", activity.averageHeartRate?.toString(), "bpm", "beats per minute"),
            Stat("Calories", activity.calories?.let { formatter.count(it) }, "kcal", "kilocalories"),
        )
    }
}

private fun Measure?.toStat(label: String): Stat =
    if (this == null) Stat(label, null) else Stat(label, value, unit, spokenUnit)
