// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.geo.PrivacyZone
import org.openactivity.model.LatLng
import java.time.Instant

/**
 * A privacy zone the owner set up: routes that start or end inside it are trimmed in everything that
 * leaves the phone (shared images, stickers and files). The owner always sees the full route.
 *
 * @property uuid stable id, the backend's `privacy_zones.id` once synced.
 * @property name the owner's name for the place ("Home"); blank when unnamed.
 * @property radiusMeters one of [PrivacyZones.RADII_METERS].
 */
data class SavedPrivacyZone(
    val id: Long,
    val uuid: String,
    val name: String,
    val center: LatLng,
    val radiusMeters: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** The zone as the shared trimmer sees it. */
    fun toGeo(): PrivacyZone = PrivacyZone(center, radiusMeters.toDouble())
}

/** Limits that match Strava and the backend table. */
object PrivacyZones {
    /** At most this many zones, as Strava allows. */
    const val MAX_ZONES: Int = 10

    /** Radius choices in metres (the backend accepts 200..1600). */
    val RADII_METERS: List<Int> = listOf(200, 400, 800, 1600)

    /** Radius of a new zone. */
    const val DEFAULT_RADIUS_METERS: Int = 400

    /** Longest name (the backend's `label` check). */
    const val MAX_NAME_LENGTH: Int = 60
}

/** Why a zone could not be saved. */
enum class PrivacyZoneError {
    /** [PrivacyZones.MAX_ZONES] zones exist already. */
    LIMIT_REACHED,

    /** The zone was deleted meanwhile. */
    NOT_FOUND,
}
