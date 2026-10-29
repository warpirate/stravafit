// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.model.SportType
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration

/** Sums over a set of activities. */
data class StatTotals(
    val count: Int,
    val distanceMeters: Double,
    val movingTime: Duration,
    val elapsedTime: Duration,
    val elevationGainMeters: Double,
) {
    operator fun plus(other: StatTotals): StatTotals = StatTotals(
        count + other.count,
        distanceMeters + other.distanceMeters,
        movingTime + other.movingTime,
        elapsedTime + other.elapsedTime,
        elevationGainMeters + other.elevationGainMeters,
    )

    companion object {
        val ZERO: StatTotals = StatTotals(0, 0.0, Duration.ZERO, Duration.ZERO, 0.0)
    }
}

/**
 * Totals of one calendar week.
 *
 * @property weekStart the first day of the week (per the requested first day of week).
 */
data class WeekTotals(val weekStart: LocalDate, val totals: StatTotals)

/** Totals of one year, overall and per sport. */
data class YearTotals(val year: Int, val totals: StatTotals, val bySport: Map<SportType, StatTotals>)

/**
 * The best result for one target across all activities.
 *
 * For distance efforts [value] is seconds (lower is better); for duration efforts it is metres.
 */
data class PersonalBest(
    val target: Double,
    val value: Double,
    val activityId: Long,
    val activityTitle: String,
    val sport: SportType,
    val achievedAt: Instant,
)
