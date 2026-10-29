// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import android.content.Context
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.model.LatLng

/**
 * The last street-level camera any map settled on, or the last known fix, kept across process restarts.
 * A map opened without a camera of its own (Record, Maps, the recorder after a crash) starts here rather
 * than at world zoom, so its tiles are the ones it needs and the athlete's area shows at once instead of
 * a blank beige map while the camera flies in from space.
 */
object LastMapCamera {
    @Volatile
    var value: MapCamera? = null
        private set

    @Volatile
    private var loaded = false

    /** Reads the stored camera once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value == null && prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)) {
            value = MapCamera(
                LatLng(prefs.getFloat(KEY_LAT, 0f).toDouble(), prefs.getFloat(KEY_LON, 0f).toDouble()),
                zoom = prefs.getFloat(KEY_ZOOM, LOCATION_ZOOM.toFloat()).toDouble(),
            )
        }
    }

    /** Remembers [camera] when it shows a useful area (city zoom or closer). */
    fun remember(camera: MapCamera, context: Context? = null) {
        if (camera.zoom < MIN_USEFUL_ZOOM) return
        val c = camera.copy(bearing = 0.0, tilt = 0.0)
        value = c
        context?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putFloat(KEY_LAT, c.target.latitude.toFloat())
            ?.putFloat(KEY_LON, c.target.longitude.toFloat())
            ?.putFloat(KEY_ZOOM, c.zoom.toFloat())
            ?.apply()
    }

    /** Remembers a fix as the place to open at, unless a camera is already known. */
    fun rememberLocation(position: LatLng) {
        if (value == null) value = MapCamera(position, zoom = LOCATION_ZOOM)
    }

    private const val PREFS = "map_last_camera"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_ZOOM = "zoom"
    private const val MIN_USEFUL_ZOOM = 10.0
    private const val LOCATION_ZOOM = 15.0
}
