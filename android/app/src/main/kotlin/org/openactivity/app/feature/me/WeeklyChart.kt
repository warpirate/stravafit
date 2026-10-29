// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/**
 * Twelve-week line chart (strava direction): points joined by an orange line over a soft orange area,
 * the busiest week labelled on a dashed top line, and this week's point filled orange with a dark
 * callout. TalkBack reads a one-sentence summary instead of individual points.
 */
@Composable
fun WeeklyChart(chart: WeeklyChartUi, modifier: Modifier = Modifier) {
    val accent = RidgelineTheme.colors.accent
    val area = RidgelineTheme.colors.accentArea
    val grid = RidgelineTheme.colors.borderStrong
    val surface = MaterialTheme.colorScheme.surface
    val calloutBg = RidgelineTheme.colors.pillInverse
    val calloutFg = RidgelineTheme.colors.onPillInverse
    val measurer = rememberTextMeasurer()
    val labelStyle = RidgelineTheme.text.caption.copy(color = RidgelineTheme.colors.textMuted)
    val calloutStyle = RidgelineTheme.text.caption.copy(color = calloutFg, fontWeight = FontWeight.Bold)
    Column(modifier.clearAndSetSemantics { contentDescription = chart.summary }) {
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = Spacing.s)) {
            val count = chart.fractions.size
            if (count == 0) return@Canvas
            val top = 22.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val inset = 8.dp.toPx()
            val usableW = size.width - inset * 2 - 40.dp.toPx()
            fun x(i: Int) = inset + if (count == 1) usableW else usableW * i / (count - 1)
            fun y(f: Float) = bottom - (bottom - top) * f
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            drawLine(grid, Offset(0f, top), Offset(size.width, top), 1.dp.toPx(), pathEffect = dash)
            drawLine(grid, Offset(0f, (top + bottom) / 2), Offset(size.width, (top + bottom) / 2), 1.dp.toPx(), pathEffect = dash)
            drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
            drawText(measurer.measure(chart.maxLabel, labelStyle), topLeft = Offset(0f, top - 18.dp.toPx()))

            val line = Path()
            val fill = Path()
            chart.fractions.forEachIndexed { i, f ->
                if (i == 0) {
                    line.moveTo(x(i), y(f))
                    fill.moveTo(x(i), bottom)
                }
                line.lineTo(x(i), y(f))
                fill.lineTo(x(i), y(f))
            }
            fill.lineTo(x(count - 1), bottom)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(area, area.copy(alpha = 0f)), startY = top, endY = bottom))
            drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx()))
            chart.fractions.forEachIndexed { i, f ->
                val c = Offset(x(i), y(f))
                if (i == count - 1) {
                    drawCircle(accent, 6.dp.toPx(), c)
                    drawLine(accent, Offset(c.x, c.y), Offset(c.x, bottom), 1.5.dp.toPx(), pathEffect = dash)
                } else {
                    drawCircle(surface, 4.5.dp.toPx(), c)
                    drawCircle(accent, 4.5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
            }
            if (chart.currentLabel.isNotEmpty()) {
                val text = measurer.measure(chart.currentLabel, calloutStyle)
                val pad = 6.dp.toPx()
                val w = text.size.width + pad * 2
                val h = text.size.height + pad
                val last = Offset(x(count - 1), y(chart.fractions.last()))
                val left = (last.x - w / 2).coerceIn(0f, size.width - w)
                val boxTop = (last.y + 10.dp.toPx()).coerceAtMost(bottom - h)
                drawRoundRect(calloutBg, Offset(left, boxTop), Size(w, h), CornerRadius(4.dp.toPx()))
                drawText(text, topLeft = Offset(left + pad, boxTop + pad / 2))
            }
        }
        if (chart.averageLabel.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    append("Past ${chart.fractions.size} weeks · avg ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(chart.averageLabel) }
                    append(" / week")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
