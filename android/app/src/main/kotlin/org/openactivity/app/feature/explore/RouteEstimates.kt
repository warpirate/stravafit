// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import kotlin.math.roundToLong

/**
 * Moving-time estimates for a planned route: a flat speed per sport plus a climbing penalty per
 * metre of ascent (Naismith's rule for walking, gentler for running and cycling). Deliberately
 * simple and documented; personalised estimates from your own history can replace it later.
 */
object RouteEstimates {

    /** Flat speed in m/s and seconds added per metre climbed. */
    data class Model(val flatSpeedMps: Double, val secondsPerMeterClimb: Double)

    /** The model for [sport]; routes without a sport are estimated as runs. */
    fun modelFor(sport: SportType?): Model {
        val s = sport ?: SportType.RUN
        return when {
            s == SportType.WALK || s == SportType.HIKE || s == SportType.SNOWSHOE -> Model(5.0 / 3.6, 6.0)
            s.category == SportCategory.FOOT -> Model(1000.0 / 360.0, 3.0)
            s.category == SportCategory.BIKE -> Model(22.0 / 3.6, 2.4)
            else -> Model(10.0 / 3.6, 2.0)
        }
    }

    /** Estimated moving time in seconds over [distanceMeters] with [elevationGainMeters] of ascent. */
    fun estimatedSeconds(distanceMeters: Double, elevationGainMeters: Double, sport: SportType?): Long {
        if (distanceMeters <= 0.0) return 0
        val model = modelFor(sport)
        return (distanceMeters / model.flatSpeedMps + elevationGainMeters.coerceAtLeast(0.0) * model.secondsPerMeterClimb).roundToLong()
    }
}
