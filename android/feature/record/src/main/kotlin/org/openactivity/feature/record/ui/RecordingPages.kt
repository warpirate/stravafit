// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.SheetTopShape
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.MapState
import org.openactivity.core.map.compose.RecenterButton
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RecordingUi
import org.openactivity.feature.record.format.LiveMetrics

/** Map page: live track and route with the locate button. */
@Composable
internal fun MapPage(state: RecordUiState, recording: RecordingUi, mapState: MapState) {
    Box(Modifier.fillMaxSize()) {
        RidgelineMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            overlays = MapOverlays(liveTrack = recording.track, plannedRoute = state.route?.points.orEmpty()),
            location = recording.location,
            textureMode = true,
            contentPadding = PaddingValues(bottom = Spacing.x3),
        )
        if (recording.isAutoPaused) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = AUTO_PAUSE_DIM)))
        }
        if (recording.location != null) {
            RecenterButton(
                mapState,
                Modifier.align(Alignment.BottomEnd).padding(end = Spacing.gutter, bottom = Spacing.x3),
            )
        }
    }
}

/** Second page of the top area: completed splits, moving time and the lap in progress. */
@Composable
internal fun SplitsPage(recording: RecordingUi) {
    val metrics = recording.metrics
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.gutter)
            .padding(top = TOP_ROW_CLEARANCE, bottom = Spacing.x3),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
            PanelCell(metrics.movingTime, Modifier.weight(1f), RidgelineTheme.text.statL)
            PanelCell(metrics.lap, Modifier.weight(1f), RidgelineTheme.text.statL)
            if (recording.sport.usesGps) PanelCell(metrics.elevation, Modifier.weight(1f), RidgelineTheme.text.statL)
        }
        Spacer(Modifier.height(Spacing.m))
        Text(stringResource(R.string.record_splits), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.s))
        metrics.splits.asReversed().forEach { SplitLine(it) }
    }
}

/** Auto-pause, GPS-loss and recovery notices. */
@Composable
internal fun RecordingNotices(recording: RecordingUi, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (recording.isAutoPaused) {
            RecordBanner(stringResource(R.string.record_auto_paused_banner), Modifier.fillMaxWidth(), icon = Icons.Rounded.PauseCircle)
        }
        if (recording.gpsLost && !recording.isPaused) {
            RecordBanner(stringResource(R.string.record_gps_lost_banner), Modifier.fillMaxWidth(), icon = Icons.Rounded.GpsOff)
        }
        // The recovery notice is informational: it closes itself after a few seconds, or on its close button.
        var recoveredDismissed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        if (recording.recovered && !recoveredDismissed) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(RECOVERED_BANNER_MS)
                recoveredDismissed = true
            }
            RecordBanner(
                stringResource(R.string.record_recovered_banner),
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.History,
                warning = false,
                onClose = { recoveredDismissed = true },
            )
        }
    }
}

/**
 * The white stats panel: Time, Split Avg | Distance, Avg Pace · Elev Gain · Heart Rate, the split progress
 * bar and the controls. While paused a "Paused" strip heads it.
 */
@Composable
internal fun RecordingPanel(
    recording: RecordingUi,
    actions: RecordActions,
    locked: Boolean,
    onLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val metrics = recording.metrics
    val paused = recording.isPaused
    val onPace = if (recording.sport.displaysPace && !locked) actions.onCyclePaceMode else null
    Surface(modifier = modifier, shape = SheetTopShape, color = MaterialTheme.colorScheme.surface, shadowElevation = Elevation.sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (paused) PausedStrip()
            Column(Modifier.fillMaxWidth().padding(top = Spacing.m), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.record_time), style = RidgelineTheme.text.labelRecord, color = RidgelineTheme.colors.textMuted)
                Text(
                    metrics.time.value.orEmpty(),
                    style = if (paused) RidgelineTheme.text.displayPaused else RidgelineTheme.text.display,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = metrics.time.spoken },
                )
            }
            Spacer(Modifier.height(Spacing.m))
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PanelCell(metrics.speed, Modifier.weight(1f).padding(vertical = Spacing.m), RidgelineTheme.text.statXl, onClick = onPace)
                VerticalDivider(Modifier.fillMaxHeight(), color = RidgelineTheme.colors.hairline)
                val primaryRight = if (recording.sport.usesGps) metrics.distance else metrics.movingTime
                PanelCell(primaryRight, Modifier.weight(1f).padding(vertical = Spacing.m), RidgelineTheme.text.statXl)
            }
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.m)) {
                PanelCell(metrics.average, Modifier.weight(1f), RidgelineTheme.text.statL)
                if (recording.sport.usesGps) {
                    PanelCell(metrics.elevationGain, Modifier.weight(1f), RidgelineTheme.text.statL)
                } else {
                    PanelCell(metrics.lap, Modifier.weight(1f), RidgelineTheme.text.statL)
                }
                PanelCell(metrics.heartRate, Modifier.weight(1f), RidgelineTheme.text.statL, leading = {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = RidgelineTheme.colors.heartRate)
                })
            }
            if (recording.sport.usesGps) SplitProgressRow(metrics)
            RecordingControls(recording, actions, locked, onLockChange)
        }
    }
}

@Composable
private fun PausedStrip() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(RidgelineTheme.colors.paused, SheetTopShape)
            .padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Pause, contentDescription = null, tint = RidgelineTheme.colors.onPaused, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(R.string.record_status_paused), style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.onPaused)
    }
}

/** "Km 9 ━━━━──── 730 m to split". */
@Composable
private fun SplitProgressRow(metrics: LiveMetrics) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(metrics.splitNumber, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
        Spacer(Modifier.width(Spacing.m))
        LinearProgressIndicator(
            progress = { metrics.splitProgress },
            modifier = Modifier.weight(1f).height(8.dp),
            color = RidgelineTheme.colors.accent,
            trackColor = RidgelineTheme.colors.hairline,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            stringResource(R.string.record_to_split, metrics.toSplit),
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
        )
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
 * A panel number: the label carries the unit ("Split Avg (/km)") over a bare value, as on the strava record
 * screen. With [onClick] the cell is a button (the pace field cycles split, current and average pace).
 */
@Composable
private fun PanelCell(
    stat: Stat,
    modifier: Modifier,
    valueStyle: TextStyle,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val clickLabel = stringResource(R.string.record_pace_cycle)
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick) else Modifier
    Column(
        modifier
            .then(clickable)
            .clearAndSetSemantics { contentDescription = stat.spoken }
            .padding(horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val label = if (stat.unit.isNullOrEmpty()) stat.label else "${stat.label} (${stat.unit})"
        Text(label, style = RidgelineTheme.text.labelRecord, color = RidgelineTheme.colors.textMuted, maxLines = 1, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                stat.value ?: "--",
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

private const val AUTO_PAUSE_DIM = 0.4f
private val SPLIT_NUMBER_WIDTH = 72.dp
private val TOP_ROW_CLEARANCE = 72.dp

/** How long the "recovered after the app was closed" notice stays. */
internal const val RECOVERED_BANNER_MS = 8_000L
