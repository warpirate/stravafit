// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.openactivity.app.data.sample.SampleRoutes
import org.openactivity.app.model.LatLng
import org.openactivity.core.designsystem.theme.RidgelineColors
import org.openactivity.core.designsystem.theme.RidgelineTheme
import java.util.Random

/**
 * Static route preview drawn on a Canvas: a stylised map backdrop, a 4 dp route over a 6 dp
 * casing, a hollow start circle and a filled finish circle (shape, not colour, tells them apart).
 *
 * Placeholder for the MapLibre snapshot card (ADR-012); the route geometry is already real.
 */
@Composable
fun RouteThumbnail(
    route: List<LatLng>,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f,
    contentDescription: String = "Route map",
    showBackdrop: Boolean = true,
) {
    val colors = RidgelineTheme.colors
    val seed = remember(route) { route.firstOrNull()?.hashCode()?.toLong() ?: 0L }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.small)
            .semantics { this.contentDescription = contentDescription },
    ) {
        // Strokes and padding shrink on small thumbnails so the route stays the focus.
        val scale = (size.width / FULL_WIDTH.toPx()).coerceIn(MIN_SCALE, 1f)
        if (showBackdrop) drawMapBackdrop(colors, seed, scale)
        val points = projectRoute(route, size.width, size.height, padding = 18.dp.toPx() * scale)
        if (points.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, colors.routeCasing, style = Stroke(6.dp.toPx() * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, colors.routeSelf, style = Stroke(3.5.dp.toPx() * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawEndpoints(points.first(), points.last(), colors, scale)
    }
}

private fun DrawScope.drawEndpoints(start: Offset, finish: Offset, colors: RidgelineColors, scale: Float) {
    val radius = 5.dp.toPx() * scale
    val casing = 1.5.dp.toPx() * scale
    drawCircle(colors.routeCasing, radius + casing, finish)
    drawCircle(colors.routeSelf, radius, finish)
    drawCircle(colors.routeCasing, radius + casing, start)
    drawCircle(colors.routeSelf, radius, start, style = Stroke(2.dp.toPx() * scale))
}

private val FULL_WIDTH = 300.dp
private const val MIN_SCALE = 0.55f

/** Land, a river and a few streets, deterministic per [seed] so screenshots are stable. */
private fun DrawScope.drawMapBackdrop(colors: RidgelineColors, seed: Long, scale: Float) {
    val random = Random(seed)
    drawRect(colors.mapLand)
    val riverY = size.height * (0.2f + 0.6f * random.nextFloat())
    val river = Path().apply {
        moveTo(-10f, riverY)
        cubicTo(
            size.width * 0.3f, riverY - size.height * 0.25f,
            size.width * 0.6f, riverY + size.height * 0.3f,
            size.width + 10f, riverY - size.height * 0.05f,
        )
    }
    drawPath(river, colors.mapWater, style = Stroke(14.dp.toPx() * scale, cap = StrokeCap.Round))
    val street = Stroke(1.5.dp.toPx() * scale)
    repeat(STREET_COUNT) {
        val horizontal = random.nextBoolean()
        val at = random.nextFloat()
        val tilt = (random.nextFloat() - 0.5f) * 0.3f
        if (horizontal) {
            val y = size.height * at
            drawLine(colors.mapRoad, Offset(0f, y), Offset(size.width, y + size.height * tilt), street.width)
        } else {
            val x = size.width * at
            drawLine(colors.mapRoad, Offset(x, 0f), Offset(x + size.width * tilt, size.height), street.width)
        }
    }
}

private const val STREET_COUNT = 6

@PreviewLightDark
@Composable
private fun RouteThumbnailPreview() {
    RidgelineTheme {
        Surface {
            RouteThumbnail(SampleRoutes.loop(LatLng(18.52, 73.85), 1.2, seed = 3), Modifier.padding(16.dp))
        }
    }
}
