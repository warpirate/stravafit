// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

/**
 * A WGS84 position in decimal degrees.
 *
 * @property latitude degrees north, in [-90, 90].
 * @property longitude degrees east, in [-180, 180].
 */
public data class LatLng(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}
