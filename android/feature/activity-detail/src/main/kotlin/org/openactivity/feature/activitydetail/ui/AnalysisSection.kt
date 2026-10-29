// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.activitydetail.chart.ActivityChart
import org.openactivity.feature.activitydetail.chart.ChartAxis
import org.openactivity.feature.activitydetail.chart.ChartColors
import org.openactivity.feature.activitydetail.chart.ChartData
import org.openactivity.feature.activitydetail.chart.ChartMath
import org.openactivity.feature.activitydetail.chart.ChartMetric
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import kotlin.math.roundToInt

/**
 * Pace or speed (over a shaded elevation silhouette), elevation and heart-rate charts with a shared
 * crosshair. The readout above them shows the scrubbed values, or the averages when nothing is touched.
 */
@Composable
internal fun AnalysisSection(
    data: ChartData,
    sport: SportType,
    units: UnitSystem,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viz = RidgelineTheme.colors.viz
    val base = ChartColors(
        line = viz.pace,
        fill = Color.Unspecified,
        grid = RidgelineTheme.colors.hairline,
        label = RidgelineTheme.colors.textMuted,
        average = viz.goalLine.copy(alpha = 0.55f),
        scrub = MaterialTheme.colorScheme.onSurface,
        labelBackground = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
    )
    val paceUnit = DetailFormat.paceUnitMeters(sport, units)
    val speedTicks: (ClosedFloatingPointRange<Double>) -> List<Double> = remember(paceUnit, units) {
        if (paceUnit != null) {
            { r -> ChartMath.paceTicks(r, paceUnit) }
        } else {
            { r -> ChartMath.scaledTicks(r, DetailFormat.speedDisplayFactor(units)) }
        }
    }
    val labelStyle = RidgelineTheme.text.caption
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Readout(data, sport, units, scrubIndex)
        data.seriesFor(ChartMetric.SPEED)?.let { s ->
            ChartBlock(DetailFormat.speedLabel(sport), averageLabel(s.average, sport, units))
            ActivityChart(
                data = data,
                metric = ChartMetric.SPEED,
                colors = base.copy(background = viz.elevationFill.copy(alpha = 0.6f)),
                labelStyle = labelStyle,
                yLabel = { v -> DetailFormat.speed(v, sport, units)?.let { it.value } ?: "" },
                description = "${DetailFormat.speedLabel(sport)} chart. Drag to see values along the activity.",
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                backgroundMetric = ChartMetric.ELEVATION,
                tickValues = speedTicks,
            )
        }
        data.seriesFor(ChartMetric.ELEVATION)?.let { s ->
            val range = DetailFormat.elevation(s.max - s.min, units)
            ChartBlock("Elevation", "Range ${range.value} ${range.unit}")
            ActivityChart(
                data = data,
                metric = ChartMetric.ELEVATION,
                colors = base.copy(line = viz.elevationLine, fill = viz.elevationFill, average = Color.Transparent),
                labelStyle = labelStyle,
                yLabel = { v -> DetailFormat.elevation(v, units).value },
                description = "Elevation chart",
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                height = 96.dp,
            )
        }
        data.seriesFor(ChartMetric.HEART_RATE)?.let { s ->
            ChartBlock("Heart rate", s.average?.let { "Avg ${it.roundToInt()} bpm" })
            ActivityChart(
                data = data,
                metric = ChartMetric.HEART_RATE,
                colors = base.copy(line = viz.heartRate),
                labelStyle = labelStyle,
                yLabel = { v -> v.roundToInt().toString() },
                description = "Heart rate chart",
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                height = 96.dp,
            )
        }
        XAxisLabels(data, units)
    }
}

@Composable
private fun ChartBlock(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textSecondary)
    }
}

@Composable
private fun Readout(data: ChartData, sport: SportType, units: UnitSystem, index: Int?) {
    val text = if (index == null || index !in data.x.indices) {
        "Drag across a chart to see values along the way"
    } else {
        buildList {
            add(xLabel(data, data.x[index], units, withUnit = true))
            data.seriesFor(ChartMetric.SPEED)?.values?.get(index)?.let { v ->
                DetailFormat.speed(v, sport, units)?.let { add("${it.value}${unitSuffix(it.unit)}") } ?: add("stopped")
            }
            data.seriesFor(ChartMetric.ELEVATION)?.values?.get(index)?.takeIf { it.isFinite() }?.let {
                val e = DetailFormat.elevation(it, units)
                add("${e.value} ${e.unit}")
            }
            data.seriesFor(ChartMetric.HEART_RATE)?.values?.get(index)?.takeIf { it.isFinite() }?.let { add("${it.roundToInt()} bpm") }
        }.joinToString("  ·  ")
    }
    Text(
        text,
        style = if (index == null) RidgelineTheme.text.caption else MaterialTheme.typography.labelLarge,
        color = if (index == null) RidgelineTheme.colors.textMuted else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(min = 20.dp),
    )
}

@Composable
private fun XAxisLabels(data: ChartData, units: UnitSystem) {
    val max = axisValue(data, data.xMax, units)
    if (max <= 0.0) return
    val ticks = ChartMath.niceTicks(0.0, max, 5).filter { it <= max + 1e-9 }
    val unit = if (data.axis == ChartAxis.DISTANCE) (if (units == UnitSystem.METRIC) "km" else "mi") else "min"
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            ticks.forEachIndexed { i, t ->
                Text(
                    formatAxis(data, t) + if (i == ticks.lastIndex) " $unit" else "",
                    style = RidgelineTheme.text.caption,
                    color = RidgelineTheme.colors.textMuted,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val center = (ticks[i] / max * width).toInt()
                p.placeRelative((center - p.width / 2).coerceIn(0, (width - p.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

/** X value in display units: km or miles for distance axes, minutes for time axes. */
private fun axisValue(data: ChartData, x: Double, units: UnitSystem): Double = when (data.axis) {
    ChartAxis.DISTANCE -> x / units.splitLengthMeters
    ChartAxis.TIME -> x / 60.0
}

private fun formatAxis(data: ChartData, value: Double): String =
    if (value % 1.0 == 0.0 || data.axis == ChartAxis.TIME) value.roundToInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", value)

private fun xLabel(data: ChartData, x: Double, units: UnitSystem, withUnit: Boolean): String = when (data.axis) {
    ChartAxis.DISTANCE -> {
        val d = DetailFormat.distance(x, units)
        if (withUnit) "${d.value} ${d.unit}" else d.value
    }
    ChartAxis.TIME -> DetailFormat.clock(x)
}

private fun averageLabel(average: Double?, sport: SportType, units: UnitSystem): String? =
    average?.let { DetailFormat.speed(it, sport, units) }?.let { "Avg ${it.value}${unitSuffix(it.unit)}" }

private fun unitSuffix(unit: String?): String = when {
    unit == null -> ""
    unit.startsWith("/") -> unit
    else -> " $unit"
}
