// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Map backdrop for the pre-start screen with the user's location dot and an accuracy halo whose
 * radius tracks the reported accuracy (record detail 2.4). Stand-in for the live MapLibre view.
 *
 * @param accuracyMeters null before the first fix: the halo is drawn at its widest, dashed.
 */
@Composable
fun LocationMapPlaceholder(accuracyMeters: Float?, modifier: Modifier = Modifier) {
    val colors = RidgelineTheme.colors
    val description = accuracyMeters?.let { "Map. Your location, accurate to ${it.toInt()} metres" }
        ?: "Map. Searching for your location"
    Canvas(modifier.semantics { contentDescription = description }) {
        drawRect(colors.mapLand)
        // Park and river
        drawCircle(colors.mapWater.copy(alpha = 0.6f), size.minDimension * 0.22f, Offset(size.width * 0.15f, size.height * 0.2f))
        val river = Path().apply {
            moveTo(-20f, size.height * 0.78f)
            cubicTo(size.width * 0.35f, size.height * 0.62f, size.width * 0.6f, size.height * 0.95f, size.width + 20f, size.height * 0.7f)
        }
        drawPath(river, colors.mapWater, style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
        val major = 9.dp.toPx()
        val minor = 4.dp.toPx()
        drawLine(colors.mapRoad, Offset(0f, size.height * 0.42f), Offset(size.width, size.height * 0.36f), major)
        drawLine(colors.mapRoad, Offset(size.width * 0.58f, 0f), Offset(size.width * 0.5f, size.height), major)
        listOf(0.12f, 0.27f, 0.62f).forEach { y ->
            drawLine(colors.mapRoad, Offset(0f, size.height * y), Offset(size.width, size.height * (y + 0.04f)), minor)
        }
        listOf(0.18f, 0.34f, 0.78f, 0.9f).forEach { x ->
            drawLine(colors.mapRoad, Offset(size.width * x, 0f), Offset(size.width * (x - 0.05f), size.height), minor)
        }

        val center = Offset(size.width * 0.54f, size.height * 0.46f)
        val metresToPx = 1.6.dp.toPx()
        val haloRadius = min(max((accuracyMeters ?: MAX_HALO_METERS) * metresToPx, 22.dp.toPx()), size.minDimension * 0.42f)
        drawCircle(colors.routeSelf.copy(alpha = 0.16f), haloRadius, center)
        drawCircle(colors.routeSelf.copy(alpha = 0.5f), haloRadius, center, style = Stroke(1.dp.toPx()))
        drawCircle(colors.routeCasing, 10.dp.toPx(), center)
        drawCircle(colors.routeSelf, 7.dp.toPx(), center)
    }
}

private const val MAX_HALO_METERS = 80f
