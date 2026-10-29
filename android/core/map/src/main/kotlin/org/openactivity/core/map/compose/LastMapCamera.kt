// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import org.openactivity.core.map.camera.MapCamera
import org.openactivity.model.LatLng

/**
 * The last street-level camera any map in this process settled on, or the last known fix. A map opened
 * without a camera of its own (Record, Maps) starts here rather than at world zoom, so its tiles are the
 * ones it needs and the athlete's area shows at once.
 */
object LastMapCamera {
    @Volatile
    var value: MapCamera? = null
        private set

    /** Remembers [camera] when it shows a useful area (city zoom or closer). */
    fun remember(camera: MapCamera) {
        if (camera.zoom >= MIN_USEFUL_ZOOM) value = camera.copy(bearing = 0.0, tilt = 0.0)
    }

    /** Remembers a fix as the place to open at, unless a camera is already known. */
    fun rememberLocation(position: LatLng) {
        if (value == null) value = MapCamera(position, zoom = LOCATION_ZOOM)
    }

    private const val MIN_USEFUL_ZOOM = 10.0
    private const val LOCATION_ZOOM = 15.0
}
