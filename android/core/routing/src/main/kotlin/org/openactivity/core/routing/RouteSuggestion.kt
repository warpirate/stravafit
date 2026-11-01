// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.serialization.Serializable
import org.openactivity.model.LatLng
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import org.openactivity.polyline.PolylineCodec

/**
 * The sports route suggestions are made for, with the OSRM profile that routes them and the loop
 * lengths suggested by default.
 */
enum class SuggestionSport(
    val label: String,
    /** Path of the FOSSGIS OSRM service for this sport, up to and including the profile. */
    val osrmPath: String,
    val defaultDistancesMeters: List<Double>,
    val sportType: SportType,
) {
    RUN("Run", FOOT_PATH, listOf(3_000.0, 5_000.0, 10_000.0), SportType.RUN),
    RIDE("Ride", BIKE_PATH, listOf(20_000.0, 40_000.0), SportType.RIDE),
    WALK("Walk", FOOT_PATH, listOf(2_000.0, 4_000.0), SportType.WALK),
    HIKE("Hike", FOOT_PATH, listOf(5_000.0, 10_000.0), SportType.HIKE),
    ;

    companion object {
        /** The suggestion sport for the athlete's default sport; sports without road loops get runs. */
        fun from(sport: SportType?): SuggestionSport = when {
            sport == null -> RUN
            sport == SportType.WALK -> WALK
            sport == SportType.HIKE || sport == SportType.SNOWSHOE -> HIKE
            sport.category == SportCategory.BIKE -> RIDE
            else -> RUN
        }
    }
}

private const val FOOT_PATH = "routed-foot/route/v1/foot"
private const val BIKE_PATH = "routed-bike/route/v1/bike"

/** Elevation at [distanceMeters] along a suggested route. */
@Serializable
data class ProfileSample(val distanceMeters: Double, val elevationMeters: Double)

/**
 * A loop routed on real OpenStreetMap ways from the athlete's position. It is never stored as an
 * activity; the athlete can save it as a route.
 *
 * @property polyline the route geometry, polyline-encoded with precision 6.
 * @property elevationGainMeters null when the elevation service could not be reached.
 * @property profile elevation samples along the route; empty without elevation.
 * @property bearingDegrees direction from the start towards the middle of the loop.
 */
@Serializable
data class RouteSuggestion(
    val id: String,
    val name: String,
    val sport: SuggestionSport,
    val targetDistanceMeters: Double,
    val distanceMeters: Double,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val polyline: String,
    val profile: List<ProfileSample>,
    val startLatitude: Double,
    val startLongitude: Double,
    val bearingDegrees: Double,
) {
    /** The decoded geometry. */
    val points: List<LatLng> by lazy { PolylineCodec.decode(polyline, POLYLINE_PRECISION) }

    companion object {
        const val POLYLINE_PRECISION = 6
    }
}

/** What to suggest: loops of [distancesMeters] for [sport] starting at [start]. */
data class SuggestionRequest(
    val start: LatLng,
    val sport: SuggestionSport,
    val distancesMeters: List<Double> = sport.defaultDistancesMeters,
)

/**
 * Suggestions so far.
 *
 * @property done true on the last emission.
 * @property failed true when the routing service could not be reached; [routes] then holds what the
 *   cache had, possibly older than a day.
 */
data class SuggestionProgress(
    val routes: List<RouteSuggestion>,
    val done: Boolean,
    val failed: Boolean = false,
)
