// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Filled elevation profile with its highest and lowest elevations; TalkBack reads one summary. */
@Composable
fun ElevationProfile(profile: ElevationProfileUi, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    Column(modifier.clearAndSetSemantics { contentDescription = profile.summary }) {
        Row(Modifier.fillMaxWidth()) {
            Text("Elevation", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "Low ${profile.minLabel} · High ${profile.maxLabel}",
                style = RidgelineTheme.text.caption,
                color = RidgelineTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            if (profile.points.size < 2) return@Canvas
            val inset = 2.dp.toPx()
            val h = size.height - inset * 2
            fun at(p: Pair<Float, Float>) = Offset(p.first * size.width, inset + (1f - p.second) * h)
            val outline = Path().apply {
                moveTo(at(profile.points.first()).x, at(profile.points.first()).y)
                profile.points.drop(1).forEach { lineTo(at(it).x, at(it).y) }
            }
            val area = Path().apply {
                addPath(outline)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, fill)
            drawPath(outline, line, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}
