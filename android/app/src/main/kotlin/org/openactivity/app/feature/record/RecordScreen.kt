// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.notInThisBuild
import org.openactivity.core.designsystem.component.RecordButton
import org.openactivity.core.designsystem.component.StatusPill
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.SheetTopShape
import org.openactivity.core.designsystem.theme.Spacing

/** Full-screen Record pre-start, connected to [RecordViewModel]. */
@Composable
fun RecordScreen(onClose: () -> Unit, viewModel: RecordViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    RecordContent(
        state = state,
        actions = RecordActions(
            onClose = onClose,
            onOpenSettings = { messenger.show(notInThisBuild("Record settings")) },
            onOpenSportPicker = { viewModel.setSportPickerOpen(true) },
            onStart = { messenger.show(notInThisBuild("Recording")) },
            onAddRoute = { messenger.show(notInThisBuild("Routes")) },
            onToggleAutoPause = viewModel::toggleAutoPause,
            onToggleAudioCues = viewModel::toggleAudioCues,
            onOpenSensors = { messenger.show(notInThisBuild("Sensors")) },
        ),
    )
    if (state.isSportPickerOpen) {
        SportPickerSheet(
            selected = state.sport,
            recent = state.recentSports,
            onSelect = viewModel::selectSport,
            onDismiss = { viewModel.setSportPickerOpen(false) },
        )
    }
}

/** Callbacks for [RecordContent]. */
data class RecordActions(
    val onClose: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenSportPicker: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onAddRoute: () -> Unit = {},
    val onToggleAutoPause: () -> Unit = {},
    val onToggleAudioCues: () -> Unit = {},
    val onOpenSensors: () -> Unit = {},
)

/**
 * Stateless pre-start screen (core-screens 3.1): map with location halo or an indoor timer,
 * GPS status pill, and a bottom panel with sport, quick settings and the Start button.
 */
@Composable
fun RecordContent(state: RecordUiState, actions: RecordActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.gps is GpsState.Indoor) {
                IndoorBackdrop(Modifier.fillMaxSize())
            } else {
                val accuracy = when (val gps = state.gps) {
                    is GpsState.Acquiring -> gps.accuracyMeters
                    is GpsState.Ready -> gps.accuracyMeters
                    GpsState.Indoor -> null
                }
                LocationMapPlaceholder(accuracy, Modifier.fillMaxSize())
            }
            TopControls(actions, Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xxl, start = Spacing.gutter, end = Spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GpsPill(state.gps)
                if (state.showSlowFixHint) {
                    Spacer(Modifier.height(Spacing.s))
                    SlowFixHint()
                }
            }
        }
        StartPanel(state, actions)
    }
}

@Composable
private fun TopControls(actions: RecordActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.SpaceBetween) {
        MapIconButton(onClick = actions.onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close recorder")
        }
        MapIconButton(onClick = actions.onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Record settings")
        }
    }
}

/** 48 dp round button floating over the map. */
@Composable
private fun MapIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun GpsPill(gps: GpsState) {
    val colors = RidgelineTheme.colors
    when (gps) {
        is GpsState.Acquiring -> StatusPill(
            text = "Acquiring GPS…",
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            icon = {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.textSecondary,
                    trackColor = colors.hairline,
                )
            },
        )
        is GpsState.Ready -> StatusPill(
            text = "GPS ready · ±${gps.accuracyMeters.toInt()} m",
            containerColor = colors.successContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            icon = { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(16.dp), tint = colors.success) },
        )
        GpsState.Indoor -> StatusPill(
            text = "Indoor · GPS off",
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            icon = { Icon(Icons.Rounded.GpsOff, contentDescription = null, Modifier.size(16.dp), tint = colors.textSecondary) },
        )
    }
}

@Composable
private fun SlowFixHint() {
    Surface(shape = MaterialTheme.shapes.medium, color = RidgelineTheme.colors.warningContainer) {
        Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.GpsNotFixed, contentDescription = null, Modifier.size(16.dp), tint = RidgelineTheme.colors.onWarningContainer)
            Spacer(Modifier.width(Spacing.s))
            Text(
                "Still searching. Stand still in the open; you can start now and the track fills in.",
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.onWarningContainer,
            )
        }
    }
}

@Composable
private fun IndoorBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(RidgelineTheme.colors.surfaceSunken), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("0:00", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "TIME",
                style = RidgelineTheme.text.overline,
                color = RidgelineTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StartPanel(state: RecordUiState, actions: RecordActions) {
    Surface(
        shape = SheetTopShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(top = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.s, bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(RidgelineTheme.colors.hairline, PillShape),
            )
            Spacer(Modifier.height(Spacing.l))
            QuickSettingsRow(state, actions)
            Spacer(Modifier.height(Spacing.xl))
            StartRow(state, actions)
        }
    }
}

@Composable
private fun StartRow(state: RecordUiState, actions: RecordActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SportButton(state.sport, actions.onOpenSportPicker)
        }
        RecordButton(state = state.startButtonState, onClick = actions.onStart, diameter = 80.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            RouteButton(enabled = state.gps !is GpsState.Indoor, onClick = actions.onAddRoute)
        }
    }
}
