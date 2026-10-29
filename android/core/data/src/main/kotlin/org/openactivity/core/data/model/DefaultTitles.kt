// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.model.SportType
import java.time.LocalTime

/** Default activity titles such as "Morning Run" and readable sport names. */
object DefaultTitles {
    private val overrides = mapOf(
        SportType.E_BIKE_RIDE to "E-Bike Ride",
        SportType.E_MOUNTAIN_BIKE_RIDE to "E-Mountain Bike Ride",
        SportType.HIGH_INTENSITY_INTERVAL_TRAINING to "HIIT",
        SportType.STAND_UP_PADDLING to "Stand Up Paddle",
        SportType.CROSSFIT to "CrossFit",
    )

    /** Readable name of [sport]: "TrailRun" becomes "Trail Run". */
    fun sportName(sport: SportType): String =
        overrides[sport] ?: sport.apiName.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

    /** "Morning", "Afternoon", "Evening" or "Night" for a local start time. */
    fun partOfDay(time: LocalTime): String = when (time.hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }

    /** Title for an activity of [sport] started at local [time], e.g. "Evening Ride". */
    fun forActivity(sport: SportType, time: LocalTime): String = "${partOfDay(time)} ${sportName(sport)}"
}
