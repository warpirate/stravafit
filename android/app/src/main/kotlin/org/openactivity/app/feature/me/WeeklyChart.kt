// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/**
 * Twelve-week volume bars. The current week is filled primary; earlier weeks use the container
 * tint. A dashed hairline marks the busiest week, labelled with its distance. TalkBack reads a
 * one-sentence summary instead of individual bars.
 */
@Composable
fun WeeklyChart(chart: WeeklyChartUi, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val past = MaterialTheme.colorScheme.primaryContainer
    val empty = RidgelineTheme.colors.hairline
    val grid = RidgelineTheme.colors.borderStrong
    Column(modifier.clearAndSetSemantics { contentDescription = chart.summary }) {
        Text(chart.maxLabel, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(top = Spacing.xs),
        ) {
            val count = chart.fractions.size
            if (count == 0) return@Canvas
            val gap = 6.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            val radius = CornerRadius(4.dp.toPx())
            drawLine(
                grid,
                Offset(0f, 0f),
                Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            chart.fractions.forEachIndexed { index, fraction ->
                val minHeight = 3.dp.toPx()
                val height = (size.height * fraction).coerceAtLeast(minHeight)
                val color = when {
                    fraction <= 0f -> empty
                    index == count - 1 -> primary
                    else -> past
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * (barWidth + gap), size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Text("12 weeks ago", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted, modifier = Modifier.weight(1f))
            Text("This week", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
        }
    }
}
