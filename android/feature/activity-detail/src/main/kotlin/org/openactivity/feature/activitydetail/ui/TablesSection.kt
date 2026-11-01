// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.MISSING_VALUE
import org.openactivity.core.designsystem.component.RankMedal
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.activitydetail.EffortRow
import org.openactivity.feature.activitydetail.LapRow
import org.openactivity.feature.activitydetail.SplitRow
import org.openactivity.feature.activitydetail.format.Measure

/**
 * Splits table: number, pace or speed, a bar relative to the fastest split, grade-adjusted pace (hilly
 * runs, [showGap]), elevation change and heart rate. Tapping a row selects it (the map frames that split);
 * tapping it again releases it.
 */
@Composable
internal fun SplitsTable(
    rows: List<SplitRow>,
    unitHeader: String,
    speedHeader: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    showGap: Boolean = false,
) {
    val hasHr = rows.any { it.heartRate != null }
    val hasElev = rows.any { it.elevationDelta != null }
    Column(modifier.fillMaxWidth()) {
        TableHeader(unitHeader, speedHeader, if (showGap) "GAP" else null, if (hasElev) "Elev" else null, if (hasHr) "HR" else null)
        rows.forEachIndexed { i, row ->
            val isSelected = selected == i
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(if (isSelected) null else i) }
                    .semantics(mergeDescendants = true) { this.selected = isSelected }
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.label, style = RidgelineTheme.text.statS.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(UNIT_COL))
                SpeedCell(row.speed, Modifier.width(SPEED_COL))
                PaceBar(row.barFraction, row.isFastest, Modifier.weight(1f))
                if (showGap) NumberCell(row.gap?.value, Modifier.width(GAP_COL))
                if (hasElev) NumberCell(row.elevationDelta, Modifier.width(SMALL_COL))
                if (hasHr) NumberCell(row.heartRate, Modifier.width(SMALL_COL))
            }
        }
        val fastest = rows.indexOfFirst { it.isFastest }
        val slowest = rows.indexOfFirst { it.isSlowest }
        if (fastest >= 0) {
            Text(
                buildString {
                    append("Fastest: ${rows[fastest].label}")
                    if (slowest >= 0) append("  ·  Slowest: ${rows[slowest].label}")
                },
                style = RidgelineTheme.text.caption,
                color = RidgelineTheme.colors.textMuted,
                modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs),
            )
        }
    }
}

/** Laps table: number, distance, moving time, pace or speed with a relative bar. */
@Composable
internal fun LapsTable(rows: List<LapRow>, speedHeader: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs)) {
            HeaderText("Lap", Modifier.width(UNIT_COL))
            HeaderText("Dist", Modifier.width(DIST_COL))
            HeaderText("Time", Modifier.width(DIST_COL))
            HeaderText(speedHeader, Modifier.weight(1f))
        }
        HorizontalDivider(color = RidgelineTheme.colors.hairline)
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = Spacing.xs).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.number.toString(), style = RidgelineTheme.text.statS.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(UNIT_COL))
                Text("${row.distance.value} ${row.distance.unit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(DIST_COL))
                Text(row.time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(DIST_COL))
                SpeedCell(row.speed, Modifier.width(SPEED_COL))
                PaceBar(row.barFraction, highlight = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Best efforts achieved in this activity. Medalled ones carry a gold, silver or bronze medal (solid for an
 * all-time place, outlined for a place within the year) and the place in words.
 */
@Composable
internal fun EffortsList(rows: List<EffortRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(row.name, row.result, row.medalLabel?.let(::spokenMedal)).joinToString(", ")
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    row.rank?.let { RankMedal(it, yearly = row.yearly) }
                }
                Text(row.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Spacing.m).weight(1f))
                row.medalLabel?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.isPersonalBest) RidgelineTheme.colors.signalText else RidgelineTheme.colors.textSecondary,
                        modifier = Modifier.padding(end = Spacing.s),
                    )
                }
                Text(row.result, style = RidgelineTheme.text.statS, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** "PR" read out in full. */
internal fun spokenMedal(label: String): String = if (label == "PR") "personal record" else label

@Composable
private fun TableHeader(unit: String, speed: String, gap: String?, elev: String?, hr: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs)) {
        HeaderText(unit, Modifier.width(UNIT_COL))
        HeaderText(speed, Modifier.width(SPEED_COL))
        HeaderText(if (gap == null) "Relative pace" else "Relative", Modifier.weight(1f))
        if (gap != null) HeaderText(gap, Modifier.width(GAP_COL))
        if (elev != null) HeaderText(elev, Modifier.width(SMALL_COL))
        if (hr != null) HeaderText(hr, Modifier.width(SMALL_COL))
    }
    HorizontalDivider(color = RidgelineTheme.colors.hairline)
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(text, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted, modifier = modifier)
}

@Composable
private fun SpeedCell(speed: Measure?, modifier: Modifier) {
    Text(
        speed?.value ?: MISSING_VALUE,
        style = RidgelineTheme.text.statS,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun NumberCell(text: String?, modifier: Modifier) {
    Text(text ?: MISSING_VALUE, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary, modifier = modifier)
}

@Composable
private fun PaceBar(fraction: Float, highlight: Boolean, modifier: Modifier) {
    Box(modifier.height(12.dp).padding(end = Spacing.m)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(3.dp))
                .background(if (highlight) RidgelineTheme.colors.viz.pace else RidgelineTheme.colors.viz.pace.copy(alpha = 0.45f)),
        )
    }
}

private val UNIT_COL = 44.dp
private val SPEED_COL = 64.dp
private val SMALL_COL = 44.dp
private val GAP_COL = 52.dp
private val DIST_COL = 76.dp
