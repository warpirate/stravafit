// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.openactivity.core.map.camera.CameraMath
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.overlay.OverlayPlanner
import org.openactivity.model.LatLng

/**
 * Draws a route onto an Android [Canvas] with the same look as the live map: a cased line, a
 * hollow start and a filled finish. Used on top of snapshots and for the offline fallback.
 */
internal object RoutePainter {
    /**
     * @param camera the camera the canvas shows (see [ThumbnailRequest.camera]).
     * @param density pixels per dp of [canvas].
     */
    fun draw(
        canvas: Canvas,
        points: List<LatLng>,
        camera: MapCamera,
        density: Float,
        lineColor: Int,
        casingColor: Int,
        markerColor: Int,
    ) {
        if (points.size < 2) return
        val widthDp = canvas.width / density
        val heightDp = canvas.height / density
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = CameraMath.project(p, camera, widthDp, heightDp)
            if (i == 0) path.moveTo(o.x * density, o.y * density) else path.lineTo(o.x * density, o.y * density)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        stroke.color = casingColor
        stroke.strokeWidth = (OverlayPlanner.LINE_WIDTH + OverlayPlanner.CASING_EXTRA) * density * THUMB_SCALE
        canvas.drawPath(path, stroke)
        stroke.color = lineColor
        stroke.strokeWidth = OverlayPlanner.LINE_WIDTH * density * THUMB_SCALE
        canvas.drawPath(path, stroke)

        val start = CameraMath.project(points.first(), camera, widthDp, heightDp)
        val finish = CameraMath.project(points.last(), camera, widthDp, heightDp)
        val radius = OverlayPlanner.MARKER_RADIUS * density * THUMB_SCALE
        val ring = OverlayPlanner.MARKER_STROKE * density * THUMB_SCALE
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        // Finish: filled marker with a casing ring.
        fill.color = casingColor
        canvas.drawCircle(finish.x * density, finish.y * density, radius + ring / 2, fill)
        fill.color = markerColor
        canvas.drawCircle(finish.x * density, finish.y * density, radius - ring / 2, fill)
        // Start: hollow marker (casing fill, coloured ring) drawn last so it stays visible on loops.
        fill.color = markerColor
        canvas.drawCircle(start.x * density, start.y * density, radius + ring / 2, fill)
        fill.color = casingColor
        canvas.drawCircle(start.x * density, start.y * density, radius - ring / 2, fill)
    }

    /** Thumbnails are small; slightly thinner strokes keep the route from swallowing the map. */
    private const val THUMB_SCALE = 0.8f
}
