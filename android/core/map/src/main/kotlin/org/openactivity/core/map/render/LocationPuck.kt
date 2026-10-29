// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.render

import android.annotation.SuppressLint
import android.content.Context
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.openactivity.core.map.MapLocation
import org.openactivity.core.map.overlay.MapPalette

/**
 * Drives MapLibre's LocationComponent from our own fixes. Its built-in location engine is never
 * started (`useDefaultLocationEngine(false)`); the puck moves only when [update] is called.
 *
 * Follow mode uses the component's camera tracking, which also stops tracking when the user pans,
 * reported through [onTrackingDismissed]. Main thread only.
 */
internal class LocationPuck(
    private val context: Context,
    private val map: MapLibreMap,
    private val onTrackingDismissed: () -> Unit,
) {
    private var follow = false
    private var activePalette: MapPalette? = null

    private val trackingListener = object : OnCameraTrackingChangedListener {
        override fun onCameraTrackingDismissed() = onTrackingDismissed()
        override fun onCameraTrackingChanged(currentMode: Int) = Unit
    }

    /**
     * Shows [location] (or hides the puck when null) on [style]. The location was obtained by the
     * caller under its own runtime permission, so MapLibre's permission annotation does not apply.
     */
    @SuppressLint("MissingPermission")
    fun update(style: Style, location: MapLocation?, palette: MapPalette) {
        val component = map.locationComponent
        if (location == null) {
            if (component.isLocationComponentActivated) component.isLocationComponentEnabled = false
            return
        }
        if (!component.isLocationComponentActivated || activePalette != palette) activate(component, style, palette)
        if (!component.isLocationComponentEnabled) {
            component.isLocationComponentEnabled = true
            applyTracking(component)
        }
        component.renderMode = if (location.bearingDegrees != null) RenderMode.GPS else RenderMode.NORMAL
        component.forceLocationUpdate(location.toAndroidLocation())
    }

    /** Starts or stops following the puck with the camera. */
    fun setFollow(enabled: Boolean) {
        follow = enabled
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) applyTracking(component)
    }

    /** Stops camera tracking immediately, e.g. before a programmatic camera move. */
    fun cancelTracking() {
        val component = map.locationComponent
        if (component.isLocationComponentActivated) component.cameraMode = CameraMode.NONE
    }

    fun release() {
        val component = map.locationComponent
        if (component.isLocationComponentActivated) component.removeOnCameraTrackingChangedListener(trackingListener)
    }

    private fun activate(component: LocationComponent, style: Style, palette: MapPalette) {
        val options = LocationComponentOptions.builder(context)
            .foregroundTintColor(palette.puck)
            .bearingTintColor(palette.puck)
            .backgroundTintColor(palette.casing)
            .accuracyColor(palette.puckAccuracy)
            .accuracyAlpha(ACCURACY_ALPHA)
            .build()
        if (component.isLocationComponentActivated) {
            component.applyStyle(options)
        } else {
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(false)
                    .locationComponentOptions(options)
                    .build(),
            )
            component.addOnCameraTrackingChangedListener(trackingListener)
        }
        activePalette = palette
    }

    private fun applyTracking(component: LocationComponent) {
        if (!follow) {
            component.cameraMode = CameraMode.NONE
            return
        }
        val zoom = map.cameraPosition.zoom.takeIf { it >= MIN_FOLLOW_ZOOM } ?: FOLLOW_ZOOM
        component.setCameraMode(CameraMode.TRACKING, TRANSITION_MS, zoom, null, null, null)
    }

    private companion object {
        const val ACCURACY_ALPHA = 0.18f
        const val MIN_FOLLOW_ZOOM = 13.0
        const val FOLLOW_ZOOM = 16.0
        const val TRANSITION_MS = 750L
    }
}
