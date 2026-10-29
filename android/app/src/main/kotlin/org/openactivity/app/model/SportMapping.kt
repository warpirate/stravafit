// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

import org.openactivity.model.SportType

/** The shared [SportType] behind this app-shell [Sport]. */
fun Sport.toSportType(): SportType = when (this) {
    Sport.Run -> SportType.RUN
    Sport.TrailRun -> SportType.TRAIL_RUN
    Sport.Walk -> SportType.WALK
    Sport.Hike -> SportType.HIKE
    Sport.Ride -> SportType.RIDE
    Sport.MountainBikeRide -> SportType.MOUNTAIN_BIKE_RIDE
    Sport.GravelRide -> SportType.GRAVEL_RIDE
    Sport.EBikeRide -> SportType.E_BIKE_RIDE
    Sport.Swim -> SportType.SWIM
    Sport.Rowing -> SportType.ROWING
    Sport.WeightTraining -> SportType.WEIGHT_TRAINING
    Sport.Yoga -> SportType.YOGA
    Sport.Workout -> SportType.WORKOUT
}

/** The app-shell [Sport] for this [SportType], or null when the shell's picker does not offer it. */
fun SportType.toSportOrNull(): Sport? = Sport.entries.firstOrNull { it.toSportType() == this }
