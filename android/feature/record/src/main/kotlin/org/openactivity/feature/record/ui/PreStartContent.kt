// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.core.map.compose.MapLayersButton
import org.openactivity.core.map.compose.MapState
import org.openactivity.core.map.compose.RecenterButton
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.isMapThemeDark
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.feature.record.GpsIndicator
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordPhase
import org.openactivity.feature.record.RecordSheet
import org.openactivity.feature.record.RecordUiState

/**
 * Pre-start screen (strava direction, `record-light.png`): a full map centred on the user with the close
 * chevron, the GPS pill top-centre and the settings gear; layers and locate buttons; then the start panel
 * with the sport and route pills, Auto-pause · Start · Audio cues and a settings summary line. In landscape
 * the panel moves beside the map so the map keeps most of the screen.
 */
@Composable
internal fun PreStartContent(state: RecordUiState, actions: RecordActions, mapState: MapState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                PreStartMap(state, actions, mapState, Modifier.weight(1f).fillMaxHeight())
                StartPanel(state, actions, Modifier.width(LANDSCAPE_PANEL_WIDTH).fillMaxHeight(), sideBySide = true)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PreStartMap(state, actions, mapState, Modifier.weight(1f).fillMaxWidth())
                StartPanel(state, actions, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PreStartMap(state: RecordUiState, actions: RecordActions, mapState: MapState, modifier: Modifier) {
    val dark = isMapThemeDark()
    var chosenStyle by rememberSaveable { mutableStateOf<MapStyleKind?>(null) }
    val styleKind = chosenStyle ?: MapStyleKind.default(dark)
    Box(modifier) {
        if (state.sport.usesGps) {
            val route = state.route
            LaunchedEffect(route?.id) {
                // Show the whole route once when it is chosen; Recenter brings the user back.
                if (route != null) mapState.fitTo(route.points, MapPadding.all(ROUTE_FIT_PADDING_DP))
            }
            RidgelineMap(
                modifier = Modifier.fillMaxSize(),
                state = mapState,
                styleKind = styleKind,
                overlays = MapOverlays(plannedRoute = route?.points.orEmpty()),
                location = state.location,
                contentPadding = PaddingValues(bottom = Spacing.xs),
            )
        } else {
            IndoorBackdrop(Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(Modifier.fillMaxWidth()) {
                MapIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.record_close), actions.onClose, Modifier.align(Alignment.CenterStart))
                GpsStatusPill(state.gps, onFix = actions.onFixLocation, modifier = Modifier.align(Alignment.Center))
                MapIconButton(
                    Icons.Rounded.Settings,
                    stringResource(R.string.record_settings),
                    { actions.onShowSheet(RecordSheet.Settings) },
                    Modifier.align(Alignment.CenterEnd),
                )
            }
            if (state.showBatteryPrompt) {
                BatteryPrompt(actions.onAllowBackground, actions.onDismissBatteryPrompt, Modifier.fillMaxWidth())
            }
            if (state.showSlowFixHint && state.gps.indicator == GpsIndicator.Acquiring && state.phase != RecordPhase.WaitingForGps) {
                RecordBanner(stringResource(R.string.record_slow_fix_hint), Modifier.fillMaxWidth(), icon = Icons.Rounded.GpsNotFixed)
            }
        }
        if (state.sport.usesGps) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = Spacing.gutter, bottom = Spacing.x3),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                MapLayersButton(
                    available = MapStyleKind.entries,
                    selected = styleKind,
                    onSelect = { chosenStyle = it },
                )
                if (state.location != null) RecenterButton(mapState)
            }
        }
    }
}

@Composable
private fun IndoorBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(RidgelineTheme.colors.surfaceSunken), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("0:00", style = RidgelineTheme.text.display, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.record_time),
                style = RidgelineTheme.text.labelRecord,
                color = RidgelineTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(Spacing.l))
            Text(
                stringResource(R.string.record_indoor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.x3),
            )
        }
    }
}

private const val ROUTE_FIT_PADDING_DP = 56f
private val LANDSCAPE_PANEL_WIDTH = 360.dp
