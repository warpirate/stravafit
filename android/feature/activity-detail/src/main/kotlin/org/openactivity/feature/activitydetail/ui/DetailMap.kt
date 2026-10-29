// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.core.map.MapLocation
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.overlay.ActivityRouteOverlay
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.thumbnail.RouteMapThumbnail
import org.openactivity.model.LatLng

/**
 * The activity's route on a MapLibre map with start and finish markers, in the route orange.
 *
 * [marker] (the scrubbed chart sample) is shown as a dot; [focus] (a selected split) is framed by the
 * camera, and the whole route again when it is null. Previews and screenshot tests, which cannot load
 * the native renderer, show the offline route drawing instead.
 */
@Composable
internal fun DetailMap(
    route: List<LatLng>,
    speeds: List<Double?>,
    marker: LatLng?,
    focus: List<LatLng>?,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.MaterialTheme.shapes.medium,
    fitPadding: MapPadding = MapPadding.all(36f),
) {
    val shapeModifier = modifier.fillMaxWidth().height(height).clip(shape)
    if (LocalInspectionMode.current) {
        BoxWithConstraints(shapeModifier) {
            // Match the box exactly so the offline drawing fills it.
            RouteMapThumbnail(points = route, contentDescription = "Route map", aspectRatio = maxWidth / maxHeight, loader = null, shape = shape)
        }
        return
    }
    val state = rememberMapState()
    val overlays = remember(route, speeds) {
        MapOverlays(
            // A solid orange line on its casing (strava direction): the purple/teal pace ramp was hard to
            // see on dark tiles. Pace lives in the chart and the splits.
            activityRoute = ActivityRouteOverlay(route, speeds.takeIf { it.size == route.size }, colorByPace = false),
        )
    }
    val target = focus?.takeIf { it.size >= 2 } ?: route
    LaunchedEffect(state, target, fitPadding) {
        state.fitTo(target, fitPadding, animate = focus != null)
    }
    RidgelineMap(
        modifier = shapeModifier,
        state = state,
        overlays = overlays,
        location = marker?.let { MapLocation(it) },
        gesturesEnabled = true,
        textureMode = true,
    )
}
