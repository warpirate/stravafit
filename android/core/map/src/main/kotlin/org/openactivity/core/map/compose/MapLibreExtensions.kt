// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import android.view.View
import androidx.core.view.doOnLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.openactivity.core.map.camera.CameraMath
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.render.toCameraPosition
import org.openactivity.core.map.render.toMapCamera
import org.openactivity.core.map.render.toStyleBuilder
import org.openactivity.core.map.style.MapStyleDefinition
import kotlin.coroutines.resume

private const val CAMERA_ANIMATION_MS = 600

internal suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont ->
    getMapAsync { if (cont.isActive) cont.resume(it) }
}

internal suspend fun MapLibreMap.awaitStyle(definition: MapStyleDefinition): Style = suspendCancellableCoroutine { cont ->
    setStyle(definition.toStyleBuilder()) { style -> if (cont.isActive) cont.resume(style) }
}

/** Suspends until the view has a non-zero size. */
internal suspend fun View.awaitLaidOut() {
    if (isLaidOut && width > 0 && height > 0) return
    suspendCancellableCoroutine { cont -> doOnLayout { if (cont.isActive) cont.resume(Unit) } }
}

internal fun MapLibreMap.configureDefaults() {
    uiSettings.isAttributionEnabled = false
    uiSettings.isLogoEnabled = false
    uiSettings.isCompassEnabled = true
    setMinZoomPreference(MIN_ZOOM)
}

internal fun MapLibreMap.jumpTo(camera: MapCamera) {
    moveCamera(CameraUpdateFactory.newCameraPosition(camera.toCameraPosition()))
}

internal fun CameraPosition.toMapCameraOrNull(): MapCamera? = runCatching { toMapCamera() }.getOrNull()

/** Runs [command] against this map, using [view]'s size for fitting. */
internal fun MapLibreMap.execute(command: CameraCommand, view: View, animate: Boolean) {
    val camera = when (command) {
        is CameraCommand.MoveTo -> command.camera
        is CameraCommand.FitBounds -> {
            val density = view.resources.displayMetrics.density
            CameraMath.fit(
                bounds = command.bounds,
                viewportWidth = view.width / density,
                viewportHeight = view.height / density,
                padding = command.padding,
                maxZoom = command.maxZoom,
            )
        }
    }
    val update = CameraUpdateFactory.newCameraPosition(camera.toCameraPosition())
    if (animate) animateCamera(update, CAMERA_ANIMATION_MS) else moveCamera(update)
}

private const val MIN_ZOOM = 1.0
