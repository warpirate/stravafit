// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.openactivity.model.LatLng
import org.openactivity.polyline.PolylineCodec
import java.util.Locale

/**
 * A route from OSRM.
 *
 * @property roadNames distance travelled on each named way, in route order (unnamed ways left out).
 */
data class OsrmRoute(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<LatLng>,
    val roadNames: List<Pair<String, Double>>,
)

/**
 * Client for the FOSSGIS OSRM servers at routing.openstreetmap.de (foot and bike profiles). Requests go
 * through one [RateLimiter] so the app never sends more than one per second.
 */
class OsrmClient(
    private val transport: HttpTransport,
    private val limiter: RateLimiter,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    /**
     * Routes through [waypoints] in order for [sport]. Returns null when OSRM finds no route (for example
     * a waypoint far from any way).
     *
     * @throws java.io.IOException when the server cannot be reached or answers with an error.
     */
    suspend fun route(sport: SuggestionSport, waypoints: List<LatLng>): OsrmRoute? {
        val response = limiter.run { transport.get(url(sport, waypoints)) }
        if (response.code == HTTP_OK) return parse(response.body)
        // OSRM answers 400 with {"code":"NoRoute"} or "NoSegment" when it cannot route these points.
        val code = runCatching { json.decodeFromString<OsrmResponse>(response.body).code }.getOrNull()
        if (code in NO_ROUTE_CODES) return null
        throw RoutingServiceException("Routing service answered ${response.code}${code?.let { " ($it)" }.orEmpty()}")
    }

    /** The request URL: full overview geometry as polyline6 and steps for the road names. */
    fun url(sport: SuggestionSport, waypoints: List<LatLng>): String {
        require(waypoints.size >= 2) { "a route needs at least two waypoints" }
        val coordinates = waypoints.joinToString(";") { String.format(Locale.ROOT, "%.6f,%.6f", it.longitude, it.latitude) }
        return "$baseUrl/${sport.osrmPath}/$coordinates?overview=full&geometries=polyline6&steps=true&continue_straight=true"
    }

    @Serializable
    internal data class OsrmResponse(val code: String = "", val routes: List<Route> = emptyList())

    @Serializable
    internal data class Route(
        val distance: Double = 0.0,
        val duration: Double = 0.0,
        val geometry: String = "",
        val legs: List<Leg> = emptyList(),
    )

    @Serializable
    internal data class Leg(val steps: List<Step> = emptyList())

    @Serializable
    internal data class Step(
        val name: String = "",
        val distance: Double = 0.0,
        @SerialName("ref") val ref: String = "",
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://routing.openstreetmap.de"
        private const val HTTP_OK = 200
        private val NO_ROUTE_CODES = setOf("NoRoute", "NoSegment")
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses an OSRM `route` response. Returns null for a response without a route.
         *
         * @throws RoutingServiceException for a body that is not an OSRM response.
         */
        fun parse(body: String): OsrmRoute? {
            val response = try {
                json.decodeFromString<OsrmResponse>(body)
            } catch (e: SerializationException) {
                throw RoutingServiceException("Unreadable routing response: ${e.message}")
            } catch (e: IllegalArgumentException) {
                throw RoutingServiceException("Unreadable routing response: ${e.message}")
            }
            if (response.code != "Ok") return null
            val route = response.routes.firstOrNull() ?: return null
            val points = try {
                PolylineCodec.decode(route.geometry, RouteSuggestion.POLYLINE_PRECISION)
            } catch (e: IllegalArgumentException) {
                throw RoutingServiceException("Unreadable route geometry")
            }
            if (points.size < 2) return null
            val names = route.legs.flatMap { it.steps }
                .filter { it.name.isNotBlank() && it.distance > 0 }
                .map { it.name.trim() to it.distance }
            return OsrmRoute(route.distance, route.duration, points, names)
        }
    }
}
