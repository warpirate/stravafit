// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.graphics.Bitmap
import org.openactivity.core.export.render.SvgRasterizer
import routeexport.RouteArt
import routeexport.Track
import routeexport.TrackPoint

/**
 * The small route drawing on the "Last activity" widget: the share screen's minimal sticker (route-art),
 * in StravaFit orange on a transparent background so it reads on the light and the dark widget alike.
 */
internal object WidgetRouteArt {
    private val rasterizer = SvgRasterizer()

    /** A [sizePx] square drawing of [points], or null without a route or when drawing fails. */
    fun render(points: List<TrackPoint>, sizePx: Int): Bitmap? {
        if (points.size < 2 || sizePx <= 0) return null
        return try {
            // Drawn at the share size and scaled down, so the stroke keeps the sticker's proportions.
            val svg = RouteArt.minimal(Track(points), size = ART_SIZE, color = ORANGE, strokeWidth = 44.0, padding = 0.14)
            rasterizer.render(svg, sizePx, sizePx)
        } catch (e: Exception) {
            null
        }
    }

    private const val ART_SIZE = 1080
    private const val ORANGE = "#FC5200"
}
