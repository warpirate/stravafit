// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.playground

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.CameraTracking
import org.openactivity.core.map.compose.LocalMapStyleProvider
import org.openactivity.core.map.compose.MapStyleSwitcher
import org.openactivity.core.map.compose.RecenterButton
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.isMapThemeDark
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.core.map.thumbnail.RouteMapThumbnail

/**
 * Debug-only launcher entry for trying the map module on a device: real location, style switching,
 * follow and recentre, a recorded track that becomes an activity route with a pace gradient,
 * long-press to plan a route, fit-to-bounds and the list-card thumbnail.
 */
class MapPlaygroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RidgelineTheme { MapPlaygroundScreen() } }
    }
}

@Composable
private fun MapPlaygroundScreen(viewModel: MapPlaygroundViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onPermission(result.values.any { it })
    }
    LaunchedEffect(Unit) {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        viewModel.onPermission(granted)
    }
    val mapState = rememberMapState(initialTracking = CameraTracking.Follow)
    val style = state.style ?: MapStyleKind.default(isMapThemeDark())
    val provider = LocalMapStyleProvider.current

    Box(Modifier.fillMaxSize()) {
        RidgelineMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            styleKind = style,
            overlays = state.overlays,
            location = state.location,
            contentPadding = PaddingValues(bottom = 196.dp),
            onMapLongClick = viewModel::addPlannedPoint,
        )
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.gutter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            MapStyleSwitcher(provider.available, style, viewModel::selectStyle)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                Text(
                    statusLine(state),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.location != null) RecenterButton(mapState)
            if (state.overlays.allPoints.size >= 2) {
                SmallFloatingActionButton(
                    onClick = { mapState.fitTo(state.overlays.allPoints) },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { Icon(Icons.Rounded.FitScreen, contentDescription = "Fit to routes") }
            }
        }
        BottomPanel(
            state = state,
            onGrant = {
                permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.gutter),
        )
    }
}

@Composable
private fun BottomPanel(state: PlaygroundUiState, onGrant: () -> Unit, viewModel: MapPlaygroundViewModel, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when {
                !state.hasPermission -> EmptyState(
                    icon = Icons.Rounded.LocationOff,
                    title = "Location is off for this app",
                    body = "Allow location to see yourself on the map and record a track. Long-press the map to plan a route without it.",
                    primaryActionLabel = "Allow location",
                    onPrimaryAction = onGrant,
                )
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        PrimaryButton(
                            text = if (state.recording) "Stop track" else "Record track",
                            onClick = viewModel::toggleRecording,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Finish as activity",
                            onClick = viewModel::finishTrack,
                            enabled = state.track.size >= 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Colour activity by pace", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.colorByPace, onCheckedChange = viewModel::setColorByPace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                RidgelineTextButton(text = "Undo plan point", onClick = viewModel::undoPlannedPoint, enabled = state.planned.isNotEmpty())
                RidgelineTextButton(text = "Clear", onClick = viewModel::clearAll)
                state.finished?.let { finished ->
                    RouteMapThumbnail(
                        points = finished.points,
                        contentDescription = "Finished track thumbnail",
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}

private fun statusLine(state: PlaygroundUiState): String = when {
    !state.hasPermission -> "No location permission. Long-press to plan a route."
    !state.locationAvailable -> "Location services are switched off."
    state.location == null -> "Waiting for a location fix…"
    else -> buildString {
        append("Fix ±").append(state.location.accuracyMeters?.toInt() ?: "?").append(" m")
        if (state.recording) append(" · recording ").append(state.track.size).append(" points")
        if (state.planned.isNotEmpty()) append(" · plan ").append(state.planned.size).append(" points")
    }
}
