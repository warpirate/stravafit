// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.activitydetail.chart.ActivityChart
import org.openactivity.feature.activitydetail.chart.ChartAxis
import org.openactivity.feature.activitydetail.chart.ChartColors
import org.openactivity.feature.activitydetail.chart.ChartData
import org.openactivity.feature.activitydetail.chart.ChartMath
import org.openactivity.feature.activitydetail.chart.ChartMetric
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import kotlin.math.roundToInt

/**
 * Pace or speed (over a shaded elevation silhouette, with an optional grade-adjusted pace line for runs),
 * elevation, heart-rate, cadence and power charts with a shared crosshair. Each chart appears only when
 * the activity has that data. The readout above them shows the scrubbed values.
 *
 * @param display how pace or speed is shown (the athlete's per-sport choice).
 * @param gapOnByDefault start with the GAP line shown (hilly runs); the chip next to the title toggles it.
 */
@Composable
internal fun AnalysisSection(
    data: ChartData,
    sport: SportType,
    display: SpeedDisplay,
    units: UnitSystem,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    averageSpeedMps: Double? = null,
    gapOnByDefault: Boolean = false,
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
    val paceUnit = DetailFormat.paceUnitMeters(display, units)
    val speedTicks: (ClosedFloatingPointRange<Double>) -> List<Double> = remember(paceUnit, units) {
        if (paceUnit != null) {
            { r -> ChartMath.paceTicks(r, paceUnit) }
        } else {
            { r -> ChartMath.scaledTicks(r, DetailFormat.speedDisplayFactor(units)) }
        }
    }
    // Elevation is stored in metres: label round feet in imperial rather than converted round metres.
    val elevationTicks: (ClosedFloatingPointRange<Double>) -> List<Double> = remember(units) {
        { r -> ChartMath.scaledTicks(r, DetailFormat.elevationDisplayFactor(units), 4) }
    }
    val labelStyle = RidgelineTheme.text.caption
    val hasGap = data.seriesFor(ChartMetric.GRADE_ADJUSTED_SPEED) != null
    var showGap by rememberSaveable(hasGap, gapOnByDefault) { mutableStateOf(hasGap && gapOnByDefault) }
    val gapColor = viz.compare
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Readout(data, sport, display, units, scrubIndex, showGap)
        data.seriesFor(ChartMetric.SPEED)?.let { s ->
            // The activity's own average (distance over moving time), so the chart agrees with the summary.
            val average = averageSpeedMps?.takeIf { it > 0.0 } ?: s.average
            val label = DetailFormat.speedLabel(display)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChartBlock(label, averageLabel(average, display, units), Modifier.weight(1f))
                if (hasGap) {
                    Spacer(Modifier.width(Spacing.s))
                    RidgelineFilterChip(
                        "GAP",
                        selected = showGap,
                        onClick = { showGap = !showGap },
                        modifier = Modifier.semantics { contentDescription = "Show grade adjusted pace" },
                    )
                }
            }
            if (showGap) GapLegend(label, gapColor, base.line)
            ActivityChart(
                data = data,
                metric = ChartMetric.SPEED,
                colors = base.copy(background = viz.elevationFill.copy(alpha = 0.6f)),
                labelStyle = labelStyle,
                yLabel = { v -> DetailFormat.speed(v, display, units)?.let { it.value } ?: "" },
                description = "$label chart${if (showGap) " with grade adjusted pace" else ""}. Drag to see values along the activity.",
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                backgroundMetric = ChartMetric.ELEVATION,
                tickValues = speedTicks,
                average = average,
                overlayMetric = if (showGap) ChartMetric.GRADE_ADJUSTED_SPEED else null,
                overlayColor = gapColor,
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
                tickValues = elevationTicks,
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
        data.seriesFor(ChartMetric.CADENCE)?.let { s ->
            val unit = DetailFormat.cadenceUnit(sport)
            ChartBlock("Cadence", s.average?.let { "Avg ${it.roundToInt()} $unit" })
            ActivityChart(
                data = data,
                metric = ChartMetric.CADENCE,
                colors = base.copy(line = viz.cadence),
                labelStyle = labelStyle,
                yLabel = { v -> v.roundToInt().toString() },
                description = "Cadence chart",
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                height = 96.dp,
            )
        }
        data.seriesFor(ChartMetric.POWER)?.let { s ->
            ChartBlock("Power", s.average?.let { "Avg ${it.roundToInt()} W" })
            ActivityChart(
                data = data,
                metric = ChartMetric.POWER,
                colors = base.copy(line = viz.power),
                labelStyle = labelStyle,
                yLabel = { v -> v.roundToInt().toString() },
                description = "Power chart",
                scrubIndex = scrubIndex,
                onScrub = onScrub,
                height = 96.dp,
            )
        }
        XAxisLabels(data, units)
    }
}

/** Key for the pace chart while the GAP line shows: which colour is pace and which is GAP. */
@Composable
private fun GapLegend(label: String, gapColor: Color, lineColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(lineColor)
        Text(label, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textSecondary, modifier = Modifier.padding(start = Spacing.xs, end = Spacing.m))
        LegendDot(gapColor)
        Text("Grade adjusted", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textSecondary, modifier = Modifier.padding(start = Spacing.xs))
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun ChartBlock(title: String, trailing: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textSecondary)
    }
}

@Composable
private fun Readout(data: ChartData, sport: SportType, display: SpeedDisplay, units: UnitSystem, index: Int?, showGap: Boolean) {
    val text = if (index == null || index !in data.x.indices) {
        "Drag across a chart to see values along the way"
    } else {
        buildList {
            add(xLabel(data, data.x[index], units, withUnit = true))
            data.seriesFor(ChartMetric.SPEED)?.values?.get(index)?.let { v ->
                DetailFormat.speed(v, display, units)?.let { add("${it.value}${unitSuffix(it.unit)}") } ?: add("stopped")
            }
            if (showGap) {
                data.seriesFor(ChartMetric.GRADE_ADJUSTED_SPEED)?.values?.get(index)
                    ?.let { DetailFormat.speed(it, display, units) }
                    ?.let { add("GAP ${it.value}${unitSuffix(it.unit)}") }
            }
            data.seriesFor(ChartMetric.ELEVATION)?.values?.get(index)?.takeIf { it.isFinite() }?.let {
                val e = DetailFormat.elevation(it, units)
                add("${e.value} ${e.unit}")
            }
            data.seriesFor(ChartMetric.HEART_RATE)?.values?.get(index)?.takeIf { it.isFinite() }?.let { add("${it.roundToInt()} bpm") }
            data.seriesFor(ChartMetric.CADENCE)?.values?.get(index)?.takeIf { it.isFinite() }
                ?.let { add("${it.roundToInt()} ${DetailFormat.cadenceUnit(sport)}") }
            data.seriesFor(ChartMetric.POWER)?.values?.get(index)?.takeIf { it.isFinite() }?.let { add("${it.roundToInt()} W") }
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

private fun averageLabel(average: Double?, display: SpeedDisplay, units: UnitSystem): String? =
    average?.let { DetailFormat.speed(it, display, units) }?.let { "Avg ${it.value}${unitSuffix(it.unit)}" }

private fun unitSuffix(unit: String?): String = when {
    unit == null -> ""
    unit.startsWith("/") -> unit
    else -> " $unit"
}
