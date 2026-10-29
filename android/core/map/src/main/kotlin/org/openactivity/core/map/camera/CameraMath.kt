// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.camera

import androidx.compose.ui.geometry.Offset
import org.openactivity.geo.BoundingBox
import org.openactivity.geo.WebMercator
import org.openactivity.model.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/**
 * Camera and projection maths shared by the live map, the snapshotter and the offline thumbnail
 * renderer, so all three put a route in exactly the same place.
 *
 * Works in MapLibre's logical units: at zoom `z` the Web Mercator world is `512 * 2^z` dp wide.
 * Unlike `LatLngBounds`, everything here copes with boxes that cross the antimeridian.
 */
object CameraMath {
    /** MapLibre's logical tile size in dp. */
    const val TILE_SIZE: Double = 512.0

    /** Zoom used when the content is a single point (or has no extent). */
    const val DEFAULT_MAX_ZOOM: Double = 16.0

    private const val DEG = PI / 180.0

    /** Normalised world x in [0, 1) for [longitude], west edge at 0. */
    fun worldX(longitude: Double): Double = (longitude + 180.0) / 360.0

    /** Normalised world y in [0, 1] for [latitude], north edge at 0; clamped at the Mercator limit. */
    fun worldY(latitude: Double): Double {
        val lat = latitude.coerceIn(-WebMercator.MAX_LATITUDE, WebMercator.MAX_LATITUDE)
        val s = sin(lat * DEG)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    /** Inverse of [worldX], wrapped into [-180, 180). */
    fun longitudeOf(worldX: Double): Double {
        val wrapped = worldX - floor(worldX)
        return wrapped * 360.0 - 180.0
    }

    /** Inverse of [worldY]. */
    fun latitudeOf(worldY: Double): Double = atan(sinh(PI * (1 - 2 * worldY.coerceIn(0.0, 1.0)))) / DEG

    /** Width of the world in dp at [zoom]. */
    fun worldSize(zoom: Double): Double = TILE_SIZE * 2.0.pow(zoom)

    /** Ground resolution in metres per dp at [latitude] and [zoom]. */
    fun metersPerDp(latitude: Double, zoom: Double): Double =
        cos(latitude * DEG) * 2 * PI * WebMercator.RADIUS_M / worldSize(zoom)

    /**
     * The camera that shows all of [bounds] inside a [viewportWidth] x [viewportHeight] dp viewport,
     * keeping [padding] clear. Padding that would leave no room is ignored rather than producing a
     * nonsensical zoom. A box with no extent gets [maxZoom].
     */
    fun fit(
        bounds: BoundingBox,
        viewportWidth: Float,
        viewportHeight: Float,
        padding: MapPadding = MapPadding.None,
        minZoom: Double = 0.0,
        maxZoom: Double = DEFAULT_MAX_ZOOM,
    ): MapCamera {
        require(viewportWidth > 0f && viewportHeight > 0f) { "viewport must have a positive size" }
        require(minZoom <= maxZoom) { "minZoom must not exceed maxZoom" }
        val west = worldX(bounds.west)
        val east = worldX(bounds.east) + if (bounds.crossesAntimeridian) 1.0 else 0.0
        val north = worldY(bounds.north)
        val south = worldY(bounds.south)
        val spanX = east - west
        val spanY = south - north

        var pad = padding
        var availableW = viewportWidth - pad.left - pad.right
        var availableH = viewportHeight - pad.top - pad.bottom
        if (availableW < 1f || availableH < 1f) {
            pad = MapPadding.None
            availableW = viewportWidth
            availableH = viewportHeight
        }
        val zoomX = if (spanX > 0) log2(availableW / (spanX * TILE_SIZE)) else Double.POSITIVE_INFINITY
        val zoomY = if (spanY > 0) log2(availableH / (spanY * TILE_SIZE)) else Double.POSITIVE_INFINITY
        val zoom = min(zoomX, zoomY).coerceIn(minZoom, maxZoom)

        // Shift the centre so the content sits in the middle of the padded area, not the viewport.
        val scale = worldSize(zoom)
        val centreX = (west + east) / 2 - (pad.left - pad.right) / 2 / scale
        val centreY = (north + south) / 2 - (pad.top - pad.bottom) / 2 / scale
        return MapCamera(target = LatLng(latitudeOf(centreY), longitudeOf(centreX)), zoom = zoom)
    }

    /** [fit] for a set of points, or null when there are none. */
    fun fit(
        points: Collection<LatLng>,
        viewportWidth: Float,
        viewportHeight: Float,
        padding: MapPadding = MapPadding.None,
        minZoom: Double = 0.0,
        maxZoom: Double = DEFAULT_MAX_ZOOM,
    ): MapCamera? = BoundingBox.of(points)?.let { fit(it, viewportWidth, viewportHeight, padding, minZoom, maxZoom) }

    /**
     * Where [point] lands, in dp from the viewport's top-left corner, for a north-up [camera] (bearing
     * and tilt are ignored). Takes the shorter way round the world, so points just across the
     * antimeridian from the camera stay next to it.
     */
    fun project(point: LatLng, camera: MapCamera, viewportWidth: Float, viewportHeight: Float): Offset {
        val scale = worldSize(camera.zoom)
        var dx = worldX(point.longitude) - worldX(camera.target.longitude)
        if (dx > 0.5) dx -= 1.0 else if (dx < -0.5) dx += 1.0
        val dy = worldY(point.latitude) - worldY(camera.target.latitude)
        return Offset((viewportWidth / 2 + dx * scale).toFloat(), (viewportHeight / 2 + dy * scale).toFloat())
    }
}
