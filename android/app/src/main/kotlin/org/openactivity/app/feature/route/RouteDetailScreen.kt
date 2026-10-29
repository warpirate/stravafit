// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.route

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.NO_FILE_PICKER_MESSAGE
import org.openactivity.app.ui.common.label
import org.openactivity.app.ui.common.launchOrElse
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.component.StatGrid
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.overlay.MapOverlays

/** Route detail, connected to [RouteDetailViewModel]. */
@Composable
fun RouteDetailScreen(
    onNavigateUp: () -> Unit,
    onRecordRoute: (Long) -> Unit,
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        uri?.let(viewModel::exportGpx)
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            messenger.show("Route deleted")
            onNavigateUp()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    RouteDetailContent(
        state = state,
        actions = RouteDetailActions(
            onNavigateUp = onNavigateUp,
            onRecord = { onRecordRoute(state.routeId) },
            onExportGpx = { saveGpx.launchOrElse(state.fileName) { messenger.show(NO_FILE_PICKER_MESSAGE) } },
            onDelete = viewModel::delete,
        ),
    )
}

/** Callbacks for [RouteDetailContent]. */
data class RouteDetailActions(
    val onNavigateUp: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onExportGpx: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Stateless route detail: map, stats, elevation profile and the route actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailContent(state: RouteDetailUiState, actions: RouteDetailActions, modifier: Modifier = Modifier) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(title = state.name.ifEmpty { "Route" }, onNavigateUp = actions.onNavigateUp) {
                if (!state.isLoading && !state.notFound) {
                    TopBarAction(Icons.Outlined.Delete, "Delete route", { confirmDelete = true })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> ActivityCardSkeleton(Modifier.padding(Spacing.gutter))
                state.notFound -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Route not found",
                    body = "It may have been deleted.",
                    primaryActionLabel = "Back",
                    onPrimaryAction = actions.onNavigateUp,
                )
                else -> RouteBody(state, actions)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this route?") },
            text = { Text("It is removed from this device. Activities you recorded on it are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RouteBody(state: RouteDetailUiState, actions: RouteDetailActions) {
    Column(
        Modifier
            .widthIn(max = Spacing.feedMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val mapState = rememberMapState()
        LaunchedEffect(state.points) { if (state.points.size >= 2) mapState.fitTo(state.points, animate = false) }
        RidgelineMap(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MAP_RATIO)
                .clip(MaterialTheme.shapes.large),
            state = mapState,
            overlays = MapOverlays(plannedRoute = state.points),
            textureMode = true,
        )
        state.sport?.let {
            Text(it.label, style = MaterialTheme.typography.labelLarge, color = RidgelineTheme.colors.textSecondary)
        }
        RidgelineCard {
            StatGrid(state.stats, columns = 2)
            state.elevation?.let {
                Spacer(Modifier.height(Spacing.l))
                ElevationProfile(it)
            }
        }
        Text(
            "Estimated moving time assumes an easy steady effort and adds time for climbing.",
            style = MaterialTheme.typography.bodySmall,
            color = RidgelineTheme.colors.textMuted,
        )
        PrimaryButton("Record this route", onClick = actions.onRecord, leadingIcon = Icons.Rounded.FiberManualRecord, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SecondaryButton(
                "Export GPX",
                onClick = actions.onExportGpx,
                enabled = !state.isExporting,
                leadingIcon = Icons.Rounded.FileDownload,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val MAP_RATIO = 1.2f
