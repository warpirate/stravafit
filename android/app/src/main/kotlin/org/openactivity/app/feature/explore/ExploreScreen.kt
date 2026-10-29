// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.NO_FILE_PICKER_MESSAGE
import org.openactivity.app.ui.common.PlannedRouteMap
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.launchOrElse
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.fileimport.ImportDocumentsContract
import org.openactivity.core.map.thumbnail.RouteMapThumbnail

/** Explore tab, connected to [ExploreViewModel]. */
@Composable
fun ExploreScreen(onOpenRoute: (Long) -> Unit, viewModel: ExploreViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val picker = rememberLauncherForActivityResult(ImportDocumentsContract()) { uris -> viewModel.importRoutes(uris) }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    ExploreContent(
        state = state,
        actions = ExploreActions(
            onImport = { picker.launchOrElse(ImportDocumentsContract.MIME_TYPES) { messenger.show(NO_FILE_PICKER_MESSAGE) } },
            onOpenRoute = onOpenRoute,
            onShowOnMap = viewModel::selectRoute,
        ),
    )
}

/** Callbacks for [ExploreContent]. */
data class ExploreActions(
    val onImport: () -> Unit = {},
    val onOpenRoute: (Long) -> Unit = {},
    val onShowOnMap: (Long) -> Unit = {},
)

/**
 * Stateless Explore tab: the MapLibre map with the highlighted saved route, the import action
 * and the list of saved routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreContent(state: ExploreUiState, actions: ExploreActions, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(title = "Explore", scrollBehavior = scrollBehavior) {
                TopBarAction(Icons.Rounded.FileUpload, "Import GPX route", actions.onImport)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item(key = "map") { ExploreMap(state.selectedRoute) }
                item(key = "header") {
                    SectionHeader(
                        "Saved routes",
                        actionLabel = if (state.routes.isEmpty()) null else "Import GPX",
                        onAction = actions.onImport,
                    )
                }
                if (!state.isLoading && state.routes.isEmpty()) {
                    item(key = "empty") { NoRoutes(state.isImporting, actions.onImport) }
                }
                items(state.routes, key = { it.id }) { route ->
                    RouteCard(
                        route,
                        onShownOnMap = route.id == state.selectedRoute?.id,
                        onClick = { actions.onOpenRoute(route.id) },
                        onShowOnMap = { actions.onShowOnMap(route.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreMap(selected: RouteCardUi?) {
    PlannedRouteMap(
        points = selected?.points.orEmpty(),
        contentDescription = selected?.let { "Map showing ${it.name}" } ?: "Map",
        aspectRatio = MAP_RATIO,
        animateFit = true,
    )
}

private const val MAP_RATIO = 1.25f

@Composable
private fun NoRoutes(importing: Boolean, onImport: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.Route,
            title = if (importing) "Importing…" else "No saved routes yet",
            body = "Import a GPX file from another app or website. Saved routes show on the map, and you can record while following one.",
            primaryActionLabel = "Import GPX route",
            onPrimaryAction = onImport,
        )
    }
}

@Composable
private fun RouteCard(route: RouteCardUi, onShownOnMap: Boolean, onClick: () -> Unit, onShowOnMap: () -> Unit) {
    RidgelineCard(onClick = onClick, contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp)) {
                RouteMapThumbnail(route.points, contentDescription = "Route shape of ${route.name}", aspectRatio = 1.4f)
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    route.sport?.let {
                        Icon(it.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = RidgelineTheme.colors.textMuted)
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(route.details, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
            }
            IconButton(onClick = onShowOnMap) {
                Icon(
                    Icons.Rounded.Map,
                    contentDescription = if (onShownOnMap) "Shown on the map" else "Show on the map",
                    tint = if (onShownOnMap) MaterialTheme.colorScheme.primary else RidgelineTheme.colors.textMuted,
                )
            }
        }
    }
}
