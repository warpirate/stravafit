// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.graphics.Bitmap
import org.openactivity.core.map.camera.CameraMath
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.core.map.overlay.MapPalette
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.model.LatLng
import org.openactivity.polyline.PolylineCodec
import java.security.MessageDigest

/**
 * A route thumbnail to render.
 *
 * @property points the route; at least two points are needed for a picture.
 * @property widthPx output width in pixels.
 * @property heightPx output height in pixels.
 * @property density pixels per dp, so line widths match the rest of the UI.
 * @property style base map under the route.
 * @property palette route colours.
 */
data class ThumbnailRequest(
    val points: List<LatLng>,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val style: MapStyleKind,
    val palette: MapPalette,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "thumbnail size must be positive: ${widthPx}x$heightPx" }
        require(density > 0f) { "density must be positive" }
    }

    /** Width in dp. */
    val widthDp: Float get() = widthPx / density

    /** Height in dp. */
    val heightDp: Float get() = heightPx / density

    /** The camera that frames the route, shared by the snapshot and the offline drawing. */
    val camera: MapCamera? by lazy {
        CameraMath.fit(points, widthDp, heightDp, MapPadding.all(PADDING_DP), maxZoom = MAX_ZOOM)
    }

    /**
     * Stable disk-cache key: a hash of everything that changes the picture. Bump [KEY_VERSION]
     * whenever the drawing itself changes.
     */
    val cacheKey: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val material = buildString {
            append(KEY_VERSION).append('|').append(style.name).append('|')
            append(widthPx).append('x').append(heightPx).append('@').append(density).append('|')
            append(palette.hashCode()).append('|')
            append(PolylineCodec.encode(points))
        }
        digest.digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Clear margin around the route, in dp. */
        const val PADDING_DP: Float = 14f

        /** Short routes are not zoomed in further than this. */
        const val MAX_ZOOM: Double = 16.0

        private const val KEY_VERSION = 2
    }
}

/** Where a thumbnail's picture came from. */
enum class ThumbnailSource {
    /** A real base-map snapshot (needs OSM attribution where shown). */
    MapSnapshot,

    /** The offline fallback: route on a plain background, no map data. */
    Offline,
}

/** A rendered route thumbnail. */
class RouteThumbnail(val bitmap: Bitmap, val source: ThumbnailSource)

/** Renders a [ThumbnailRequest] to a bitmap, or null when it cannot. */
fun interface RouteThumbnailRenderer {
    suspend fun render(request: ThumbnailRequest): Bitmap?
}
