// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.core.data.model.PrivacyZones
import org.openactivity.core.data.model.SavedPrivacyZone
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.location.LocationPermissions
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.overlay.ActivityRouteOverlay
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.thumbnail.RouteMapThumbnail
import org.openactivity.model.LatLng
import org.openactivity.model.UnitSystem

/** The privacy zones list, connected to [PrivacyZonesViewModel]. */
@Composable
fun PrivacyZonesScreen(
    onNavigateUp: () -> Unit,
    onAddZone: () -> Unit,
    onEditZone: (Long) -> Unit,
    viewModel: PrivacyZonesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PrivacyZonesContent(state, onNavigateUp, onAddZone, onEditZone)
}

/** Stateless zones list: each zone on a small map with its name and size, plus Add (up to ten). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyZonesContent(
    state: PrivacyZonesUiState,
    onNavigateUp: () -> Unit = {},
    onAddZone: () -> Unit = {},
    onEditZone: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RidgelineTopAppBar(title = "Privacy zones", onNavigateUp = onNavigateUp) {
                if (!state.isLoading && state.canAdd) TopBarAction(Icons.Rounded.Add, "Add privacy zone", onAddZone)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.isLoading) return@Box
            LazyColumn(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(start = Spacing.gutter, top = Spacing.s + padding.calculateTopPadding(), end = Spacing.gutter, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item(key = "intro") {
                    Text(
                        "An activity that starts or finishes inside a zone is trimmed until it leaves the circle, in " +
                            "everything you share: images, stickers and files. You always see the full route.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RidgelineTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = Spacing.s),
                    )
                }
                if (state.zones.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Rounded.ShieldMoon,
                                title = "No privacy zones yet",
                                body = "Add one around your home, work or anywhere else you start and finish often.",
                                primaryActionLabel = "Add a privacy zone",
                                onPrimaryAction = onAddZone,
                            )
                        }
                    }
                } else {
                    item(key = "header") { OverlineHeader("Your zones · ${state.zones.size} of ${PrivacyZones.MAX_ZONES}") }
                    itemsIndexed(state.zones, key = { _, z -> z.id }) { index, zone ->
                        ZoneCard(zone, PrivacyZoneGeometry.displayName(zone, index), state.units) { onEditZone(zone.id) }
                    }
                    if (!state.canAdd) {
                        item(key = "limit") {
                            Text(
                                "That is the most zones there can be. Delete one to add another.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RidgelineTheme.colors.textMuted,
                                modifier = Modifier.padding(vertical = Spacing.s),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneCard(zone: SavedPrivacyZone, name: String, units: UnitSystem, onClick: () -> Unit) {
    val radius = PrivacyZoneGeometry.radiusLabel(zone.radiusMeters, units)
    val ring = remember(zone.center, zone.radiusMeters) { PrivacyZoneGeometry.ring(zone.center, zone.radiusMeters.toDouble()) }
    RidgelineCard(onClick = onClick, contentPadding = Spacing.none, modifier = Modifier.semantics { contentDescription = "$name, $radius around it. Edit" }) {
        RouteMapThumbnail(
            points = ring,
            contentDescription = "Map of $name",
            aspectRatio = 2.6f,
            // The card clips the corners.
            shape = RectangleShape,
            cacheId = "privacy-zone-${zone.id}",
            showEndpoints = false,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$radius around it", style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
        }
    }
}

/** Callbacks for [PrivacyZoneEditorContent]. */
data class PrivacyZoneEditorActions(
    val onNavigateUp: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onRadius: (Int) -> Unit = {},
    val onMapLongPress: (LatLng) -> Unit = {},
    val onUseMyLocation: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

/** The zone editor, connected to [PrivacyZoneEditorViewModel]. */
@Composable
fun PrivacyZoneEditorScreen(onDone: () -> Unit, viewModel: PrivacyZoneEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.useCurrentLocation() }
    LaunchedEffect(viewModel) { viewModel.effects.collect { onDone() } }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    PrivacyZoneEditorContent(
        state = state,
        actions = PrivacyZoneEditorActions(
            onNavigateUp = onDone,
            onName = viewModel::setName,
            onRadius = viewModel::setRadius,
            onMapLongPress = viewModel::setCenter,
            onUseMyLocation = {
                if (viewModel.hasLocationPermission()) viewModel.useCurrentLocation() else permission.launch(LocationPermissions.REQUIRED)
            },
            onSave = viewModel::save,
            onDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
        ),
    )
}

/** Stateless zone editor: the map (long-press to place), current location, name, size, Save and Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyZoneEditorContent(state: PrivacyZoneEditorUiState, actions: PrivacyZoneEditorActions, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RidgelineTopAppBar(title = if (state.isNew) "New privacy zone" else "Edit privacy zone", onNavigateUp = actions.onNavigateUp) {
                if (!state.isNew && !state.notFound) TopBarAction(Icons.Rounded.Delete, "Delete zone", actions.onDelete)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.isLoading) return@Box
            if (state.notFound) {
                EmptyState(
                    icon = Icons.Rounded.ShieldMoon,
                    title = "Zone not found",
                    body = "It may have been deleted.",
                    primaryActionLabel = "Back to zones",
                    onPrimaryAction = actions.onNavigateUp,
                    modifier = Modifier.padding(top = padding.calculateTopPadding() + Spacing.xl),
                )
                return@Box
            }
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ZoneMap(
                    center = state.center,
                    radiusMeters = state.radiusMeters,
                    onLongPress = { point ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onMapLongPress(point)
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = RidgelineTheme.colors.textMuted, modifier = Modifier.size(18.dp))
                    Text(
                        if (state.center == null) "Long-press the map to put the zone there, or use where you are now." else "Long-press the map to move the zone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RidgelineTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
                SecondaryButton(
                    "Use my location",
                    onClick = actions.onUseMyLocation,
                    loading = state.locating,
                    leadingIcon = Icons.Rounded.MyLocation,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.locationProblem?.let {
                    Text(it.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions.onName,
                    label = { Text("Name") },
                    placeholder = { Text("Home") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    supportingText = { Text("Only you see this name.") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrivacyZones.RADII_METERS.forEach { meters ->
                        RidgelineFilterChip(
                            PrivacyZoneGeometry.radiusLabel(meters, state.units),
                            selected = state.radiusMeters == meters,
                            onClick = { actions.onRadius(meters) },
                        )
                    }
                }
                Text(
                    "A bigger zone makes the place harder to guess, but hides more of every route that starts or ends there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                )
                Spacer(Modifier.height(Spacing.s))
                PrimaryButton(
                    if (state.isNew) "Add zone" else "Save",
                    onClick = actions.onSave,
                    enabled = state.canSave,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text("Delete this zone?") },
            text = { Text("Routes that start or finish here will no longer be trimmed when you share them.") },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = actions.onDismissDelete) { Text("Cancel") } },
        )
    }
}

/** The editor map: the zone's circle in orange, framed to it; long-press places the centre. */
@Composable
private fun ZoneMap(center: LatLng?, radiusMeters: Int, onLongPress: (LatLng) -> Unit) {
    val ring = remember(center, radiusMeters) { center?.let { PrivacyZoneGeometry.ring(it, radiusMeters.toDouble()) }.orEmpty() }
    val shape = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
    val description = if (center == null) "Map. Long-press to place the zone" else "Map with the zone. Long-press to move it"
    if (LocalInspectionMode.current) {
        // Previews and screenshot tests cannot load the native renderer: draw the circle on the offline map.
        if (ring.isEmpty()) {
            Box(shape.aspectRatio(1.2f).semantics { contentDescription = description }) {
                RidgelineMap(Modifier.fillMaxSize())
            }
        } else {
            RouteMapThumbnail(ring, description, shape, aspectRatio = 1.2f, loader = null, showEndpoints = false)
        }
        return
    }
    val mapState = rememberMapState()
    LaunchedEffect(ring) { if (ring.size >= 2) mapState.fitTo(ring, maxZoom = 16.0) }
    RidgelineMap(
        modifier = shape.aspectRatio(1.2f).semantics { contentDescription = description },
        state = mapState,
        overlays = MapOverlays(activityRoute = if (ring.size >= 2) ActivityRouteOverlay(ring, colorByPace = false, showEndpoints = false) else null),
        textureMode = true,
        onMapLongClick = onLongPress,
    )
}
