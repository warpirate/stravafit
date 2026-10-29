// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.core.map.compose.CameraTracking
import org.openactivity.core.map.compose.MapState
import org.openactivity.core.map.compose.RecenterButton
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.feature.record.GpsIndicator
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordSheet
import org.openactivity.feature.record.RecordUiState

/**
 * Pre-start screen (core-screens 3.1): the live map centred on the user (or a timer for indoor sports), the
 * GPS pill, and the start panel with sport, quick settings, Start and Route.
 */
@Composable
internal fun PreStartContent(state: RecordUiState, actions: RecordActions, mapState: MapState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.sport.usesGps) {
                val route = state.route
                LaunchedEffect(route?.id) {
                    // Show the whole route once when it is chosen; Recenter brings the user back.
                    if (route != null) mapState.fitTo(route.points, MapPadding.all(ROUTE_FIT_PADDING_DP))
                }
                RidgelineMap(
                    modifier = Modifier.fillMaxSize(),
                    state = mapState,
                    overlays = MapOverlays(plannedRoute = route?.points.orEmpty()),
                    location = state.location,
                    contentPadding = PaddingValues(bottom = Spacing.xs),
                )
            } else {
                IndoorBackdrop(Modifier.fillMaxSize())
            }
            TopControls(actions, Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Clear of the OSM attribution line at the bottom of the map.
                    .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.x4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.sport.usesGps && mapState.tracking != CameraTracking.Follow && state.location != null) {
                    RecenterButton(mapState, Modifier.align(Alignment.End))
                    Spacer(Modifier.height(Spacing.s))
                }
                GpsStatusPill(state.gps, onFix = actions.onFixLocation)
                if (state.showSlowFixHint && state.gps.indicator == GpsIndicator.Acquiring) {
                    Spacer(Modifier.height(Spacing.s))
                    RecordBanner(stringResource(R.string.record_slow_fix_hint), Modifier.fillMaxWidth(), icon = Icons.Rounded.GpsNotFixed)
                }
            }
        }
        StartPanel(state, actions)
    }
}

@Composable
private fun TopControls(actions: RecordActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(Spacing.m)) {
        MapIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.record_close), actions.onClose)
        Spacer(Modifier.weight(1f))
        MapIconButton(Icons.Rounded.Settings, stringResource(R.string.record_settings), { actions.onShowSheet(RecordSheet.Settings) })
    }
}

@Composable
private fun IndoorBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(RidgelineTheme.colors.surfaceSunken), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("0:00", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.record_time).uppercase(),
                style = RidgelineTheme.text.overline,
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
