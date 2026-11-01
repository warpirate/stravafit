// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.model.SportType
import java.time.LocalTime

/**
 * English activity titles such as "Morning Run" and readable sport names. The app names activities
 * through [ActivityTitles] bound to string resources (so titles are localised); this English form is
 * the fallback for pure code and tests.
 */
object DefaultTitles : ActivityTitles {
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
    fun partOfDay(time: LocalTime): String = when (PartOfDay.of(time)) {
        PartOfDay.MORNING -> "Morning"
        PartOfDay.AFTERNOON -> "Afternoon"
        PartOfDay.EVENING -> "Evening"
        PartOfDay.NIGHT -> "Night"
    }

    /** Title for an activity of [sport] started at local [time], e.g. "Evening Ride". */
    override fun forActivity(sport: SportType, time: LocalTime): String = "${partOfDay(time)} ${sportName(sport)}"
}
