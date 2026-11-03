// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import org.openactivity.model.SportType
import java.util.Locale

/**
 * Maps the "Activity Type" of a Strava export onto [SportType].
 *
 * The CSV writes display names ("Trail Run", "E-Bike Ride", "Stair-Stepper", "Rock Climb") where the
 * API uses CamelCase names ("TrailRun"); both are accepted, ignoring case, spaces, hyphens and
 * brackets. Names that Strava uses but that differ from ours are listed in [ALIASES]. Unknown types
 * become [SportType.WORKOUT] (a timed session of some kind) rather than being dropped.
 */
public object StravaSports {
    private val ALIASES: Map<String, SportType> = mapOf(
        "canoe" to SportType.CANOEING,
        "kayak" to SportType.KAYAKING,
        "rockclimb" to SportType.ROCK_CLIMBING,
        "climbing" to SportType.ROCK_CLIMBING,
        "stairstepper" to SportType.STAIR_STEPPER,
        "handcycle" to SportType.HANDCYCLE,
        "hiit" to SportType.HIGH_INTENSITY_INTERVAL_TRAINING,
        "football" to SportType.SOCCER,
        "footballsoccer" to SportType.SOCCER,
        "ebike" to SportType.E_BIKE_RIDE,
        "ebikeride" to SportType.E_BIKE_RIDE,
        "emountainbikeride" to SportType.E_MOUNTAIN_BIKE_RIDE,
        "mountainbike" to SportType.MOUNTAIN_BIKE_RIDE,
        "standuppaddle" to SportType.STAND_UP_PADDLING,
        "sup" to SportType.STAND_UP_PADDLING,
        "iceskating" to SportType.ICE_SKATE,
        "inlineskating" to SportType.INLINE_SKATE,
        "rollerskate" to SportType.INLINE_SKATE,
        "crosscountryski" to SportType.NORDIC_SKI,
        "skiing" to SportType.ALPINE_SKI,
        "sailing" to SportType.SAIL,
        "weightlifting" to SportType.WEIGHT_TRAINING,
        "strengthtraining" to SportType.WEIGHT_TRAINING,
        "swimming" to SportType.SWIM,
        "running" to SportType.RUN,
        "cycling" to SportType.RIDE,
        "walking" to SportType.WALK,
        "hiking" to SportType.HIKE,
        "virtualrowing" to SportType.VIRTUAL_ROW,
        "physicaltherapy" to SportType.PHYSICAL_THERAPY,
    )

    private val BY_KEY: Map<String, SportType> = SportType.entries.associateBy { key(it.apiName) }

    /** The sport for a Strava activity type; [SportType.WORKOUT] when the type is unknown or empty. */
    public fun fromStravaType(type: String): SportType {
        val k = key(type)
        if (k.isEmpty()) return SportType.WORKOUT
        return BY_KEY[k] ?: ALIASES[k] ?: SportType.WORKOUT
    }

    private fun key(name: String): String = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
