// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.StatValue
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.SheetTopShape
import org.openactivity.core.designsystem.theme.ThumbnailShape
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.core.map.compose.MapLayersButton
import org.openactivity.core.map.compose.MapRoundButton
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.isMapThemeDark
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.overlay.ActivityRouteOverlay
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.model.SportType
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.NO_FILE_PICKER_MESSAGE
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.launchOrElse
import org.openactivity.core.designsystem.component.EmptyState
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
            onImport = { picker.launchOrElse(ImportDocumentsContract.ROUTE_MIME_TYPES) { messenger.show(NO_FILE_PICKER_MESSAGE) } },
            onOpenRoute = onOpenRoute,
            onShowOnMap = viewModel::selectRoute,
            onNotAvailable = messenger::show,
        ),
    )
}

/** Callbacks for [ExploreContent]. */
data class ExploreActions(
    val onImport: () -> Unit = {},
    val onOpenRoute: (Long) -> Unit = {},
    val onShowOnMap: (Long) -> Unit = {},
    /** Heatmap and segments need a server; this explains that. */
    val onNotAvailable: (String) -> Unit = {},
)

/**
 * Stateless Maps tab (strava direction): a full-screen map with a search pill and layers button, a chip
 * row (sport, Heatmap, Routes, Segments), 3D and locate buttons, a dark "Import route" pill, and a
 * bottom sheet of your routes as horizontally scrolling cards.
 */
@Composable
fun ExploreContent(state: ExploreUiState, actions: ExploreActions, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var styleKind by rememberSaveable { mutableStateOf<MapStyleKind?>(null) }
    var tilted by rememberSaveable { mutableStateOf(false) }
    var sportFilter by rememberSaveable { mutableStateOf<SportType?>(null) }
    val mapState = rememberMapState()
    val routes = state.routes
        .filter { sportFilter == null || it.sport == sportFilter }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val selected = state.selectedRoute?.takeIf { it in routes } ?: routes.firstOrNull()
    val dark = isMapThemeDark()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val points = selected?.points.orEmpty()
            LaunchedEffect(selected?.id) { if (points.size >= 2) mapState.fitTo(points, MapPadding.all(72f)) }
            LaunchedEffect(tilted) {
                mapState.camera?.let { mapState.moveTo(it.copy(tilt = if (tilted) TILT_3D else 0.0)) }
            }
            RidgelineMap(
                modifier = Modifier.fillMaxSize(),
                state = mapState,
                styleKind = styleKind ?: MapStyleKind.default(dark),
                overlays = MapOverlays(activityRoute = if (points.size >= 2) ActivityRouteOverlay(points, colorByPace = false) else null),
            )
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Spacing.s)) {
                Row(Modifier.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
                    SearchPill(query, { query = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.m))
                    MapLayersButton(MapStyleKind.entries, styleKind ?: MapStyleKind.default(dark), { styleKind = it })
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    SportChip(sportFilter, state.routes.mapNotNull { it.sport }.distinct()) { sportFilter = it }
                    MapChip("Heatmap", selected = false) { actions.onNotAvailable(SERVER_NEEDED) }
                    MapChip("Routes", selected = true) {}
                    MapChip("Segments", selected = false) { actions.onNotAvailable(SERVER_NEEDED) }
                }
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                MapRoundButton(onClick = { tilted = !tilted }, modifier = Modifier.semantics { contentDescription = "3D view" }) {
                    Text("3D", style = MaterialTheme.typography.titleSmall, color = if (tilted) RidgelineTheme.colors.accent else RidgelineTheme.colors.onFloat)
                }
                MapRoundButton(
                    onClick = { if (points.size >= 2) mapState.fitTo(points, MapPadding.all(72f)) },
                    modifier = Modifier.semantics { contentDescription = "Show the route" },
                ) { Icon(Icons.Rounded.NearMe, contentDescription = null) }
            }
            ImportPill(state.isImporting, actions.onImport, Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter))
        }
        RoutesSheet(routes, selected, state, actions)
    }
}

private const val TILT_3D = 60.0
private const val SERVER_NEEDED = "Needs a server account. That is coming soon."

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(52.dp),
        shape = PillShape,
        color = RidgelineTheme.colors.float,
        contentColor = RidgelineTheme.colors.onFloat,
        shadowElevation = Elevation.float,
    ) {
        Row(Modifier.padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Spacer(Modifier.width(Spacing.m))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search your routes", style = MaterialTheme.typography.bodyLarge, color = RidgelineTheme.colors.textMuted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = RidgelineTheme.colors.onFloat),
                    cursorBrush = SolidColor(RidgelineTheme.colors.accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search your routes" },
                )
            }
        }
    }
}

/** Inverted dark chip for the sport filter, with a caret and a menu. */
@Composable
private fun SportChip(selected: SportType?, sports: List<SportType>, onSelect: (SportType?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = PillShape,
            color = RidgelineTheme.colors.pillInverse,
            contentColor = RidgelineTheme.colors.onPillInverse,
            shadowElevation = Elevation.float,
        ) {
            Row(Modifier.height(40.dp).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                selected?.let {
                    Icon(it.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                }
                Text(selected?.label ?: "All sports", style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All sports") }, onClick = { open = false; onSelect(null) })
            sports.forEach { sport ->
                DropdownMenuItem(text = { Text(sport.label) }, leadingIcon = { Icon(sport.icon, null) }, onClick = { open = false; onSelect(sport) })
            }
        }
    }
}

/** A layer chip: dark with an orange check when on, white and floating when off. */
@Composable
private fun MapChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (selected) RidgelineTheme.colors.pillInverse else RidgelineTheme.colors.float,
        contentColor = if (selected) RidgelineTheme.colors.onPillInverse else RidgelineTheme.colors.onFloat,
        shadowElevation = Elevation.float,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Row(Modifier.height(40.dp).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = RidgelineTheme.colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
            }
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** The flat dark pill at the bottom of the map: imports a GPX route (route building needs a server). */
@Composable
private fun ImportPill(importing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = !importing,
        shape = PillShape,
        color = RidgelineTheme.colors.pillInverse,
        contentColor = RidgelineTheme.colors.onPillInverse,
        shadowElevation = Elevation.float,
        modifier = modifier,
    ) {
        Row(Modifier.height(52.dp).padding(horizontal = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
            Text(if (importing) "Importing…" else "Import route", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Bottom sheet: "Your routes" with horizontally scrolling route cards, or the empty state. */
@Composable
private fun RoutesSheet(routes: List<RouteCardUi>, selected: RouteCardUi?, state: ExploreUiState, actions: ExploreActions) {
    Surface(shape = SheetTopShape, color = RidgelineTheme.colors.sheetOverMap, shadowElevation = Elevation.sheet) {
        Column(Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.l)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(RidgelineTheme.colors.borderStrong, PillShape),
            )
            Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Your routes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onImport) { Text("Import GPX", color = RidgelineTheme.colors.accentText) }
            }
            if (!state.isLoading && state.routes.isEmpty()) {
                NoRoutes(state.isImporting, actions.onImport)
                return@Column
            }
            Text(
                "${routes.size} saved · tap a card to see it on the map",
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                items(routes, key = { it.id }) { route ->
                    RouteCard(
                        route,
                        isOnMap = route.id == selected?.id,
                        onClick = { if (route.id == selected?.id) actions.onOpenRoute(route.id) else actions.onShowOnMap(route.id) },
                        onOpen = { actions.onOpenRoute(route.id) },
                    )
                }
            }
        }
    }
}

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
private fun RouteCard(route: RouteCardUi, isOnMap: Boolean, onClick: () -> Unit, onOpen: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ThumbnailShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isOnMap) 2.dp else 1.dp, if (isOnMap) RidgelineTheme.colors.accent else RidgelineTheme.colors.hairline),
        modifier = Modifier.width(CARD_WIDTH).semantics { contentDescription = route.name },
    ) {
        Column {
            RouteMapThumbnail(route.points, contentDescription = "Route shape of ${route.name}", aspectRatio = CARD_WIDTH / 88.dp, shape = RectangleShape)
            Column(Modifier.padding(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${route.name}", tint = RidgelineTheme.colors.textMuted)
                    }
                }
                route.sport?.let {
                    Text(it.label, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
                }
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    route.stats.forEach { StatValue(it, valueStyle = RidgelineTheme.text.statS) }
                }
            }
        }
    }
}

private val CARD_WIDTH = 252.dp
