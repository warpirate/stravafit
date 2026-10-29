// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.map.R
import org.openactivity.core.map.compose.isMapThemeDark
import org.openactivity.core.map.compose.mapPalette
import org.openactivity.core.map.di.MapEntryPoint
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.model.LatLng

/**
 * A static route preview for list cards: a cached map snapshot when one exists or the device is
 * online, otherwise the offline drawing. Map snapshots carry a visible OSM credit.
 *
 * @param points the route; nothing but the background is drawn for fewer than two points.
 * @param loader thumbnail loader; defaults to the app-wide Hilt instance.
 */
@Composable
fun RouteMapThumbnail(
    points: List<LatLng>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f,
    style: MapStyleKind = MapStyleKind.default(isMapThemeDark()),
    loader: RouteThumbnailLoader? = rememberRouteThumbnailLoader(),
) {
    val palette = mapPalette()
    val density = LocalDensity.current.density
    val inspection = LocalInspectionMode.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.small)
            .background(RidgelineTheme.colors.mapLand)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        if (widthPx <= 0 || heightPx <= 0 || points.size < 2) return@BoxWithConstraints
        val request = remember(points, widthPx, heightPx, density, style, palette) {
            ThumbnailRequest(points, widthPx, heightPx, density, style, palette)
        }
        // Lint false positive: the producer does assign `value` (ProduceStateDoesNotAssignValue).
        @SuppressLint("ProduceStateDoesNotAssignValue")
        val thumbnail by produceState<RouteThumbnail?>(null, request, loader) {
            if (inspection || loader == null) {
                // Previews cannot reach Hilt or the native renderer: show the offline drawing.
                value = OfflineRenderer.renderNow(request)?.let { RouteThumbnail(it, ThumbnailSource.Offline) }
            } else {
                // The offline drawing stands in while a map snapshot renders, so the card is never blank.
                loader.loadProgressively(request).collect { value = it }
            }
        }
        thumbnail?.let { t ->
            Image(
                bitmap = remember(t) { t.bitmap.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (t.source == ThumbnailSource.MapSnapshot) {
                Box(Modifier.align(Alignment.BottomEnd).padding(2.dp)) {
                    Text(
                        // Short form of the style's credits; the topo tiles (CC-BY-SA) need their own.
                        stringResource(if (style == MapStyleKind.Outdoor) R.string.map_thumbnail_osm_topo else R.string.map_thumbnail_osm),
                        style = MaterialTheme.typography.labelSmall,
                        color = RidgelineTheme.colors.textSecondary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

/** The app-wide [RouteThumbnailLoader] from Hilt, or null outside a Hilt application. */
@Composable
fun rememberRouteThumbnailLoader(): RouteThumbnailLoader? {
    val context = LocalContext.current
    return remember(context) { context.thumbnailLoaderOrNull() }
}

private fun Context.thumbnailLoaderOrNull(): RouteThumbnailLoader? = try {
    EntryPointAccessors.fromApplication(applicationContext, MapEntryPoint::class.java).routeThumbnailLoader()
} catch (e: IllegalStateException) {
    null
} catch (e: ClassCastException) {
    null
}

private val OfflineRenderer = CanvasThumbnailRenderer()
