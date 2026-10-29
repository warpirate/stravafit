// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.component.statText
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.CameraTracking
import org.openactivity.core.map.compose.MapState
import org.openactivity.core.map.compose.RecenterButton
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RecordingUi
import org.openactivity.feature.record.format.LiveMetrics

/** Map page: live track and route, notices on top, three key numbers over the map (record detail 3.1). */
@Composable
internal fun MapPage(state: RecordUiState, recording: RecordingUi, mapState: MapState, onCyclePace: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        RidgelineMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            overlays = MapOverlays(liveTrack = recording.track, plannedRoute = state.route?.points.orEmpty()),
            location = recording.location,
            textureMode = true,
            // Keep the OSM attribution readable above the metric card.
            contentPadding = PaddingValues(bottom = ATTRIBUTION_CLEARANCE),
        )
        if (recording.isAutoPaused) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = AUTO_PAUSE_DIM)))
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.x3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            RecordingNotices(recording)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.gutter),
            horizontalAlignment = Alignment.End,
        ) {
            if (mapState.tracking != CameraTracking.Follow && recording.location != null) {
                RecenterButton(mapState)
                Spacer(Modifier.height(Spacing.s))
            }
            MetricOverlayCard(recording.metrics, recording.sport.displaysPace, onCyclePace)
        }
    }
}

/** Auto-pause, GPS-loss and recovery notices (core-screens 3.6, 3.7). */
@Composable
internal fun RecordingNotices(recording: RecordingUi) {
    if (recording.isAutoPaused) {
        RecordBanner(stringResource(R.string.record_auto_paused_banner), Modifier.fillMaxWidth(), icon = Icons.Rounded.PauseCircle)
    }
    if (recording.gpsLost && !recording.isPaused) {
        RecordBanner(stringResource(R.string.record_gps_lost_banner), Modifier.fillMaxWidth(), icon = Icons.Rounded.GpsOff)
    }
    if (recording.recovered) {
        RecordBanner(
            stringResource(R.string.record_recovered_banner),
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.History,
            warning = false,
        )
    }
}

@Composable
private fun MetricOverlayCard(metrics: LiveMetrics, paceTappable: Boolean, onCyclePace: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        // Shadows barely show on a dark map; the hairline keeps the card's edge visible there.
        border = BorderStroke(1.dp, RidgelineTheme.colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = Spacing.m).height(IntrinsicSize.Min)) {
            MetricCell(metrics.distance, Modifier.weight(1f), RidgelineTheme.text.statL)
            VerticalDivider(Modifier.fillMaxHeight(), color = RidgelineTheme.colors.hairline)
            MetricCell(
                metrics.speed,
                Modifier.weight(1f),
                RidgelineTheme.text.statL,
                onClick = if (paceTappable) onCyclePace else null,
            )
            VerticalDivider(Modifier.fillMaxHeight(), color = RidgelineTheme.colors.hairline)
            MetricCell(metrics.time, Modifier.weight(1f), RidgelineTheme.text.statL)
        }
    }
}

/** Stats page (core-screens 3.5): hero time, a two-column grid and the live splits. */
@Composable
internal fun StatsPage(recording: RecordingUi, onCyclePace: () -> Unit) {
    val metrics = recording.metrics
    val pace = if (recording.sport.displaysPace) onCyclePace else null
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.x3, bottom = Spacing.xl),
    ) {
        Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            RecordingNotices(recording)
        }
        MetricCell(metrics.time, Modifier.fillMaxWidth(), MaterialTheme.typography.displayLarge, centered = true)
        Spacer(Modifier.height(Spacing.l))
        GridRow(metrics.speed, metrics.average, leftClick = pace)
        GridRow(metrics.distance, metrics.movingTime)
        if (recording.sport.usesGps) GridRow(metrics.elevation, metrics.elevationGain)
        GridRow(metrics.split, metrics.lap)
        if (metrics.splits.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.l))
            Text(
                stringResource(R.string.record_splits).uppercase(),
                style = RidgelineTheme.text.overline,
                color = RidgelineTheme.colors.textMuted,
            )
            Spacer(Modifier.height(Spacing.s))
            metrics.splits.asReversed().forEach { SplitLine(it) }
        }
    }
}

@Composable
private fun GridRow(left: Stat, right: Stat, leftClick: (() -> Unit)? = null) {
    HorizontalDivider(color = RidgelineTheme.colors.hairline)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = Spacing.m)) {
        MetricCell(left, Modifier.weight(1f), RidgelineTheme.text.statL, onClick = leftClick, centered = true)
        VerticalDivider(Modifier.fillMaxHeight(), color = RidgelineTheme.colors.hairline)
        MetricCell(right, Modifier.weight(1f), RidgelineTheme.text.statL, centered = true)
    }
}

@Composable
private fun SplitLine(row: LiveMetrics.SplitRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (row.partial) stringResource(R.string.record_split_partial, row.number) else row.number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.width(SPLIT_NUMBER_WIDTH),
        )
        Text(
            statText(Stat("", row.value, row.unit), RidgelineTheme.text.statS, RidgelineTheme.text.unitScale, RidgelineTheme.colors.textMuted),
            style = RidgelineTheme.text.statS,
        )
    }
}

/**
 * A labelled number, value above label. With [onClick] the cell is a button (the pace field cycles split,
 * current and average pace); TalkBack reads the value and unit as one phrase.
 */
@Composable
private fun MetricCell(
    stat: Stat,
    modifier: Modifier,
    valueStyle: TextStyle,
    onClick: (() -> Unit)? = null,
    centered: Boolean = true,
) {
    val clickLabel = stringResource(R.string.record_pace_cycle)
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick) else Modifier
    Column(
        modifier
            .then(clickable)
            .clearAndSetSemantics { contentDescription = stat.spoken }
            .padding(horizontal = Spacing.s),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            statText(stat, valueStyle, RidgelineTheme.text.unitScale, RidgelineTheme.colors.textMuted),
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            stat.label.uppercase(),
            style = RidgelineTheme.text.overline,
            color = RidgelineTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

private const val AUTO_PAUSE_DIM = 0.4f
private val SPLIT_NUMBER_WIDTH = 72.dp
private val ATTRIBUTION_CLEARANCE = 116.dp
