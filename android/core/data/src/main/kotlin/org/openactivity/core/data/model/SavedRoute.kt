// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.geo.BoundingBox
import org.openactivity.model.SportType
import java.time.Instant

/** A saved route without its points. */
data class SavedRoute(
    val id: Long,
    val uuid: String,
    val name: String,
    val description: String,
    val sport: SportType?,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val polyline: String,
    val bounds: BoundingBox,
    val createdAt: Instant,
)

/** A route point; [distanceMeters] is cumulative from the start. */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val distanceMeters: Double,
)

/** A route with all its points. */
data class RouteDetail(val route: SavedRoute, val points: List<RoutePoint>)

/** A point to import, as parsed from a GPX track or route. */
data class RoutePointInput(val latitude: Double, val longitude: Double, val elevationMeters: Double? = null)
