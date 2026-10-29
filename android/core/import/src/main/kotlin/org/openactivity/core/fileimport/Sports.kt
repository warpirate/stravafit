// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import app.stravafit.lib.fit.FitSport
import org.openactivity.model.SportType

/** Maps the sport vocabularies of GPX, TCX and FIT onto [SportType]. Each returns (sport, declared). */
internal object Sports {
    private val GPX_TYPES: Map<String, SportType> = mapOf(
        "running" to SportType.RUN, "run" to SportType.RUN, "trail_running" to SportType.TRAIL_RUN,
        "trailrun" to SportType.TRAIL_RUN, "treadmill" to SportType.VIRTUAL_RUN,
        "cycling" to SportType.RIDE, "biking" to SportType.RIDE, "ride" to SportType.RIDE, "bike" to SportType.RIDE,
        "road_biking" to SportType.RIDE, "mountain_biking" to SportType.MOUNTAIN_BIKE_RIDE, "mtb" to SportType.MOUNTAIN_BIKE_RIDE,
        "gravel_cycling" to SportType.GRAVEL_RIDE, "e_biking" to SportType.E_BIKE_RIDE,
        "walking" to SportType.WALK, "walk" to SportType.WALK, "hiking" to SportType.HIKE, "hike" to SportType.HIKE,
        "swimming" to SportType.SWIM, "open_water_swimming" to SportType.SWIM, "swim" to SportType.SWIM,
        "rowing" to SportType.ROWING, "kayaking" to SportType.KAYAKING, "canoeing" to SportType.CANOEING,
        "stand_up_paddleboarding" to SportType.STAND_UP_PADDLING, "paddling" to SportType.KAYAKING,
        "cross_country_skiing" to SportType.NORDIC_SKI, "skiing" to SportType.ALPINE_SKI,
        "resort_skiing" to SportType.ALPINE_SKI, "backcountry_skiing" to SportType.BACKCOUNTRY_SKI,
        "snowboarding" to SportType.SNOWBOARD, "snowshoeing" to SportType.SNOWSHOE,
        "inline_skating" to SportType.INLINE_SKATE, "skating" to SportType.INLINE_SKATE,
    )

    fun fromGpxType(type: String?): Pair<SportType, Boolean> {
        val key = type?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return SportType.OTHER to false
        GPX_TYPES[key]?.let { return it to true }
        SportType.fromApiName(type.trim())?.let { return it to true }
        // Some apps write the FIT sport number as the GPX type.
        key.toIntOrNull()?.let { return fromFit(it) }
        return SportType.OTHER to false
    }

    fun fromTcx(sport: String): Pair<SportType, Boolean> = when (sport.trim().lowercase()) {
        "running" -> SportType.RUN to true
        "biking" -> SportType.RIDE to true
        else -> fromGpxType(sport).takeIf { it.second } ?: (SportType.OTHER to false)
    }

    fun fromFit(sport: Int?): Pair<SportType, Boolean> {
        val mapped = when (sport) {
            FitSport.RUNNING -> SportType.RUN
            FitSport.CYCLING -> SportType.RIDE
            FitSport.E_BIKING -> SportType.E_BIKE_RIDE
            FitSport.SWIMMING -> SportType.SWIM
            FitSport.WALKING -> SportType.WALK
            FitSport.HIKING -> SportType.HIKE
            FitSport.ROWING -> SportType.ROWING
            FitSport.CROSS_COUNTRY_SKIING -> SportType.NORDIC_SKI
            FitSport.ALPINE_SKIING -> SportType.ALPINE_SKI
            FitSport.SNOWBOARDING -> SportType.SNOWBOARD
            FitSport.PADDLING -> SportType.KAYAKING
            FitSport.MOUNTAINEERING -> SportType.HIKE
            FitSport.TRAINING, FitSport.FITNESS_EQUIPMENT -> SportType.WORKOUT
            else -> null
        }
        return if (mapped != null) mapped to true else SportType.OTHER to false
    }
}
