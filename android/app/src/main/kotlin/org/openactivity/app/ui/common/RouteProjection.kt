// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import androidx.compose.ui.geometry.Offset
import org.openactivity.app.model.LatLng
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Projects [points] into a [width] x [height] box with [padding] on every side, preserving the
 * aspect ratio (equirectangular, longitude scaled by cos(mean latitude)) and centring the result.
 *
 * Good enough for thumbnails a few kilometres across; the export pipeline in `lib/route-art`
 * uses Web Mercator for full-size output.
 */
fun projectRoute(points: List<LatLng>, width: Float, height: Float, padding: Float): List<Offset> {
    if (points.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val meanLat = points.sumOf { it.latitude } / points.size
    val lngScale = cos(Math.toRadians(meanLat))
    val xs = points.map { it.longitude * lngScale }
    val ys = points.map { it.latitude }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val spanX = max(maxX - minX, EPSILON)
    val spanY = max(maxY - minY, EPSILON)
    val innerW = max(width - 2 * padding, 1f)
    val innerH = max(height - 2 * padding, 1f)
    val scale = min(innerW / spanX, innerH / spanY)
    val offsetX = padding + (innerW - spanX * scale) / 2
    val offsetY = padding + (innerH - spanY * scale) / 2
    return points.indices.map { i ->
        Offset(
            x = (offsetX + (xs[i] - minX) * scale).toFloat(),
            // Screen y grows downwards, latitude grows northwards.
            y = (offsetY + (maxY - ys[i]) * scale).toFloat(),
        )
    }
}

private const val EPSILON = 1e-9
