// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Placeholder shown for missing values. Never show "0" for unknown data. */
const val MISSING_VALUE: String = "—"

/**
 * One labelled statistic.
 *
 * @param label short label, rendered uppercase (e.g. "Distance").
 * @param value formatted number, or null when unknown (rendered as [MISSING_VALUE]).
 * @param unit optional unit rendered at 60 % size next to the value (e.g. "km", "/km").
 * @param spokenUnit unit as it should be read by TalkBack (e.g. "kilometres"); defaults to [unit].
 */
@Immutable
data class Stat(
    val label: String,
    val value: String?,
    val unit: String? = null,
    val spokenUnit: String? = unit,
) {
    /** Accessible description, e.g. "Distance, 10.20 kilometres". */
    val spoken: String
        get() = if (value == null) "$label, not available" else listOfNotNull("$label,", value, spokenUnit).joinToString(" ")
}

/** Builds "10.20 km" with the unit at [unitScale] of the value size in [unitColor]. */
fun statText(stat: Stat, valueStyle: TextStyle, unitScale: Float, unitColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(stat.value ?: MISSING_VALUE)
        if (stat.value != null && stat.unit != null) {
            withStyle(SpanStyle(fontSize = valueStyle.fontSize * unitScale, color = unitColor)) {
                append(if (stat.unit.startsWith("/")) stat.unit else " ${stat.unit}")
            }
        }
    }

/**
 * A single stat: a Title Case muted label (12/500) above a numeric value, as on every strava screen.
 * Merged into one semantics node.
 */
@Composable
fun StatValue(
    stat: Stat,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = RidgelineTheme.text.statM,
) {
    val colors = RidgelineTheme.colors
    Column(modifier.clearAndSetSemantics { contentDescription = stat.spoken }) {
        Text(
            text = stat.label,
            style = RidgelineTheme.text.caption,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = statText(stat, valueStyle, RidgelineTheme.text.unitScale, colors.textMuted),
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Inline stat block: up to three stats in equal-width columns, used on activity cards.
 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier, valueStyle: TextStyle = RidgelineTheme.text.statM) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.statGap)) {
        stats.forEach { StatValue(it, Modifier.weight(1f), valueStyle) }
    }
}

/**
 * Stat grid: rows of [columns] stats separated by hairline dividers.
 */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier, columns: Int = 3) {
    require(columns > 0) { "columns must be positive" }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { index, stat ->
                    if (index > 0) {
                        VerticalDivider(Modifier.fillMaxHeight(), color = RidgelineTheme.colors.hairline)
                        Spacer(Modifier.width(Spacing.m))
                    }
                    StatValue(stat, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun StatBlockPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
                StatRow(
                    listOf(
                        Stat("Distance", "10.20", "km", "kilometres"),
                        Stat("Pace", "5:12", "/km", "per kilometre"),
                        Stat("Time", "53:04"),
                    ),
                )
                StatGrid(
                    listOf(
                        Stat("Activities", "142"),
                        Stat("Distance", "1,204", "km"),
                        Stat("Elev gain", "9,800", "m"),
                        Stat("Time", "108h"),
                        Stat("Avg HR", null),
                    ),
                )
            }
        }
    }
}
