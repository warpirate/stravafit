// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.overlay.ActivityRouteOverlay
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.thumbnail.RouteMapThumbnail
import org.openactivity.model.LatLng

/**
 * A live map with one saved route drawn on it, framed to the whole route whenever [points] change.
 *
 * Previews and screenshot tests cannot load the native map renderer, so in inspection mode the
 * route is drawn with the offline thumbnail renderer instead of an empty map.
 *
 * @param points the route, or empty to show the map without one.
 * @param aspectRatio width / height of the map.
 * @param animateFit true to fly to a new route, false to jump.
 */
@Composable
fun PlannedRouteMap(
    points: List<LatLng>,
    contentDescription: String,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    animateFit: Boolean = false,
) {
    val shaped = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
    if (LocalInspectionMode.current && points.size >= 2) {
        RouteMapThumbnail(points, contentDescription, shaped, aspectRatio = aspectRatio, loader = null)
        return
    }
    val mapState = rememberMapState()
    LaunchedEffect(points) { if (points.size >= 2) mapState.fitTo(points, animate = animateFit) }
    RidgelineMap(
        modifier = shaped.aspectRatio(aspectRatio),
        state = mapState,
        // The same orange line with a green start and chequered finish as on the Maps tab and in cards.
        overlays = MapOverlays(activityRoute = if (points.size >= 2) ActivityRouteOverlay(points, colorByPace = false) else null),
        textureMode = true,
    )
}
