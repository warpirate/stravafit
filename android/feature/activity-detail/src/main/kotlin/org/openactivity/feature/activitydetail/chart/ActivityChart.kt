// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colours of one chart.
 *
 * @property line the series line.
 * @property fill area under the line, or [Color.Unspecified] for none.
 * @property background optional elevation silhouette drawn behind the series.
 * @property labelBackground plate behind tick labels so the line never runs through them.
 */
data class ChartColors(
    val line: Color,
    val fill: Color,
    val grid: Color,
    val label: Color,
    val average: Color,
    val scrub: Color,
    val background: Color = Color.Unspecified,
    val labelBackground: Color = Color.Unspecified,
)

/**
 * A line chart of one [metric] over the shared x samples of [data], drawn in a Compose canvas.
 *
 * Touching or dragging horizontally reports the sample under the finger through [onScrub] (null when
 * the finger lifts); [scrubIndex] draws the crosshair, so every chart and the map stay in sync.
 * [yLabel] formats tick values (pace sports pass speed values and print them as pace).
 *
 * @param backgroundMetric optional series drawn as a silhouette behind the line (elevation under pace).
 * @param tickValues picks the labelled y values for the padded value range; round numbers by default
 * (pace charts pass [ChartMath.paceTicks] so the labels are round paces).
 */
@Composable
fun ActivityChart(
    data: ChartData,
    metric: ChartMetric,
    colors: ChartColors,
    labelStyle: TextStyle,
    yLabel: (Double) -> String,
    description: String,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    backgroundMetric: ChartMetric? = null,
    tickValues: ((ClosedFloatingPointRange<Double>) -> List<Double>)? = null,
) {
    val series = data.seriesFor(metric) ?: return
    val background = backgroundMetric?.let { data.seriesFor(it) }
    val measurer = rememberTextMeasurer()
    val range = remember(series) { ChartMath.padded(series.min, series.max) }
    val ticks = remember(range, tickValues) {
        (tickValues?.invoke(range) ?: ChartMath.niceTicks(range.start, range.endInclusive, 3)).filter { it in range }
    }
    val scrub by rememberUpdatedState(onScrub)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        scrub(ChartMath.indexAtTouch(data, offset.x, size.width.toFloat()))
                        tryAwaitRelease()
                        scrub(null)
                    },
                )
            }
            .pointerInput(data) {
                detectHorizontalDragGestures(
                    onDragStart = { scrub(ChartMath.indexAtTouch(data, it.x, size.width.toFloat())) },
                    onDragEnd = { scrub(null) },
                    onDragCancel = { scrub(null) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrub(ChartMath.indexAtTouch(data, change.position.x, size.width.toFloat()))
                    },
                )
            },
    ) {
        val xMax = data.xMax.takeIf { it > 0 } ?: 1.0
        fun xOf(i: Int) = (data.x[i] / xMax * size.width).toFloat()

        if (background != null && colors.background != Color.Unspecified) {
            val bgRange = ChartMath.padded(background.min, background.max, 0.15)
            drawArea(data, background.values, ::xOf, bgRange.start, bgRange.endInclusive, colors.background)
        }
        // Grid lines first; their labels go on top of the series at the end.
        for (t in ticks) {
            val y = yOf(t, range.start, range.endInclusive)
            drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (colors.fill != Color.Unspecified) {
            drawArea(data, series.values, ::xOf, range.start, range.endInclusive, colors.fill)
        }
        drawSeriesLine(series.values, ::xOf, range.start, range.endInclusive, colors.line)
        series.average?.let { avg ->
            val y = yOf(avg, range.start, range.endInclusive)
            drawLine(
                colors.average,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        for (t in ticks) {
            drawTickLabel(measurer.measure(yLabel(t), labelStyle.copy(color = colors.label)), yOf(t, range.start, range.endInclusive), colors.labelBackground)
        }
        scrubIndex?.takeIf { it in data.x.indices }?.let { i ->
            val x = xOf(i)
            drawLine(colors.scrub, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
            val v = series.values[i]
            if (v.isFinite()) {
                val y = yOf(v, range.start, range.endInclusive)
                drawCircle(colors.scrub, radius = 4.5.dp.toPx(), center = Offset(x, y))
                drawCircle(colors.line, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

/** Draws a tick label just above its grid line at the left edge, on a small plate when [plate] is set. */
private fun DrawScope.drawTickLabel(layout: TextLayoutResult, lineY: Float, plate: Color) {
    val padH = 4.dp.toPx()
    val padV = 1.dp.toPx()
    val top = (lineY - layout.size.height - 2.dp.toPx() - padV).coerceAtLeast(padV)
    if (plate != Color.Unspecified) {
        drawRoundRect(
            color = plate,
            topLeft = Offset(0f, top - padV),
            size = Size(layout.size.width + padH * 2, layout.size.height + padV * 2),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
    drawText(layout, topLeft = Offset(padH, top))
}

private fun DrawScope.yOf(value: Double, min: Double, max: Double): Float =
    (size.height - (value - min) / (max - min) * size.height).toFloat()

/** Draws [values] as a line, breaking it at NaN gaps. */
private fun DrawScope.drawSeriesLine(values: DoubleArray, xOf: (Int) -> Float, min: Double, max: Double, color: Color) {
    val path = Path()
    var penDown = false
    for (i in values.indices) {
        val v = values[i]
        if (!v.isFinite()) {
            penDown = false
            continue
        }
        val p = Offset(xOf(i), yOf(v, min, max))
        if (penDown) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
        penDown = true
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Fills the area between [values] and the bottom edge, one closed shape per run without gaps. */
private fun DrawScope.drawArea(data: ChartData, values: DoubleArray, xOf: (Int) -> Float, min: Double, max: Double, color: Color) {
    var start = -1
    val path = Path()
    fun close(endExclusive: Int) {
        if (start < 0 || endExclusive - start < 2) return
        path.moveTo(xOf(start), size.height)
        for (i in start until endExclusive) path.lineTo(xOf(i), yOf(values[i], min, max))
        path.lineTo(xOf(endExclusive - 1), size.height)
        path.close()
    }
    for (i in data.x.indices) {
        if (values[i].isFinite()) {
            if (start < 0) start = i
        } else {
            close(i)
            start = -1
        }
    }
    close(data.x.size)
    drawPath(path, color)
}
