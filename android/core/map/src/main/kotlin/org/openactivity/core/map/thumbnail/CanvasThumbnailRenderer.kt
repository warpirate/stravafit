// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.core.map.overlay.ColorMath
import org.openactivity.core.map.style.MapStyleKind

/**
 * Offline fallback: the route on a plain background with a faint grid, no map data and no network.
 * Uses the same framing as the snapshot, so the picture does not jump when the real map arrives.
 */
class CanvasThumbnailRenderer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RouteThumbnailRenderer {
    override suspend fun render(request: ThumbnailRequest): Bitmap? = withContext(dispatcher) { renderNow(request) }

    /** Synchronous variant for previews and tests. */
    fun renderNow(request: ThumbnailRequest): Bitmap? {
        val camera = request.camera ?: return null
        if (request.points.size < 2) return null
        val bitmap = Bitmap.createBitmap(request.widthPx, request.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val land = if (request.style == MapStyleKind.Dark) DARK_LAND else request.palette.land
        canvas.drawColor(land)
        drawGrid(canvas, request.density, ColorMath.lerp(land, request.palette.casing, GRID_STRENGTH))
        RoutePainter.draw(
            canvas = canvas,
            points = request.points,
            camera = camera,
            density = request.density,
            lineColor = request.palette.activityRoute,
            casingColor = request.palette.casing,
            markerColor = request.palette.marker,
        )
        return bitmap
    }

    private fun drawGrid(canvas: Canvas, density: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            strokeWidth = density
        }
        val step = GRID_DP * density
        var x = step
        while (x < canvas.width) {
            canvas.drawLine(x, 0f, x, canvas.height.toFloat(), paint)
            x += step
        }
        var y = step
        while (y < canvas.height) {
            canvas.drawLine(0f, y, canvas.width.toFloat(), y, paint)
            y += step
        }
    }

    private companion object {
        const val GRID_DP = 24f
        const val GRID_STRENGTH = 0.35
        val DARK_LAND = 0xFF1B2326.toInt()
    }
}
