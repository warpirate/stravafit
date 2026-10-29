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
    private var released = false
    private var activePalette: MapPalette? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Only the athlete's own pan leaves follow mode. MapLibre also dismisses tracking for its own reasons
     * (a new map view after rotation, a resize, a style reload); then tracking is simply re-applied, so the
     * puck stays centred and the locate button keeps showing "following".
     */
    private val trackingListener = object : OnCameraTrackingChangedListener {
        override fun onCameraTrackingDismissed() {
            if (released || !follow) return
            main.post {
                val component = map.locationComponent
                if (!released && follow && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
                    applyTracking(component)
                }
            }
        }
        override fun onCameraTrackingChanged(currentMode: Int) = Unit
    }

    private val moveListener = object : MapLibreMap.OnMoveListener {
        override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
            if (released || !follow) return
            follow = false
            onTrackingDismissed()
        }
        override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
        override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
    }

    init {
        map.addOnMoveListener(moveListener)
    }

    /**
     * Shows [location] (or hides the puck when null) on [style]. The location was obtained by the
     * caller under its own runtime permission, so MapLibre's permission annotation does not apply.
     */
    @SuppressLint("MissingPermission")
    fun update(style: Style, location: MapLocation?, palette: MapPalette) {
        val component = map.locationComponent
        location?.let { org.openactivity.core.map.compose.LastMapCamera.rememberLocation(it.position) }
        if (location == null) {
            if (component.isLocationComponentActivated) component.isLocationComponentEnabled = false
            return
        }
        if (!component.isLocationComponentActivated || activePalette != palette) activate(component, style, palette)
        val enabling = !component.isLocationComponentEnabled
        if (enabling) component.isLocationComponentEnabled = true
        component.renderMode = if (location.bearingDegrees != null) RenderMode.GPS else RenderMode.NORMAL
        // The fix goes in before tracking starts: MapLibre skips the tracking transition (and with it the
        // zoom) while it has no location yet, which left the camera centred at world zoom.
        component.forceLocationUpdate(location.toAndroidLocation())
        if (enabling) applyTracking(component) else zoomInIfFollowingFromAfar(component)
    }

    /** Follow mode at a world or country zoom shows nothing useful: bring the camera in to street level. */
    private fun zoomInIfFollowingFromAfar(component: LocationComponent) {
        if (!follow || component.cameraMode == CameraMode.NONE) return
        if (map.cameraPosition.zoom < MIN_FOLLOW_ZOOM) {
            component.zoomWhileTracking(FOLLOW_ZOOM, TRANSITION_MS)
        }
    }

    /** Starts or stops following the puck with the camera. */
    fun setFollow(enabled: Boolean) {
        follow = enabled
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) applyTracking(component)
    }

    /** Stops camera tracking immediately, e.g. before a programmatic camera move. */
    fun cancelTracking() {
        follow = false
        val component = map.locationComponent
        if (component.isLocationComponentActivated) component.cameraMode = CameraMode.NONE
    }

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        map.removeOnMoveListener(moveListener)
        val component = map.locationComponent
        if (component.isLocationComponentActivated) component.removeOnCameraTrackingChangedListener(trackingListener)
    }

    private fun activate(component: LocationComponent, style: Style, palette: MapPalette) {
        // The mockup's blue dot: a saturated blue centre in a white ring with a soft blue halo, large enough
        // to find at a glance and to stand out at the head of the live track in both themes.
        val options = LocationComponentOptions.builder(context)
            .foregroundTintColor(palette.puck)
            .foregroundStaleTintColor(palette.puck)
            .bearingTintColor(palette.puck)
            .backgroundTintColor(PUCK_RING)
            .backgroundStaleTintColor(PUCK_RING)
            .accuracyColor(palette.puckAccuracy)
            .accuracyAlpha(ACCURACY_ALPHA)
            .minZoomIconScale(PUCK_SCALE)
            .maxZoomIconScale(PUCK_SCALE)
            .elevation(PUCK_ELEVATION)
            .pulseEnabled(true)
            .pulseColor(palette.puck)
            .pulseAlpha(PULSE_ALPHA)
            .pulseMaxRadius(PULSE_RADIUS)
            .pulseSingleDuration(PULSE_MS)
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
        val zoom = followZoom(map.cameraPosition.zoom)
        component.setCameraMode(CameraMode.TRACKING, TRANSITION_MS, zoom, null, null, null)
    }

    internal companion object {
        const val ACCURACY_ALPHA = 0.18f
        const val PUCK_RING: Int = 0xFFFFFFFF.toInt()
        const val PUCK_SCALE = 1.35f
        const val PUCK_ELEVATION = 4f
        const val PULSE_ALPHA = 0.35f
        const val PULSE_RADIUS = 30f
        const val PULSE_MS = 2_200f
        const val MIN_FOLLOW_ZOOM = 13.0
        const val FOLLOW_ZOOM = 16.0
        const val TRANSITION_MS = 750L

        /** Zoom to follow at: keep the user's zoom when it is close enough, else street level. */
        fun followZoom(current: Double): Double = current.takeIf { it >= MIN_FOLLOW_ZOOM } ?: FOLLOW_ZOOM
    }
}
