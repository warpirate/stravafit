// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import org.openactivity.core.designsystem.component.FrostedSurface
import org.openactivity.core.designsystem.component.LocalFrost
import org.openactivity.core.designsystem.component.rememberFrost
import org.openactivity.core.designsystem.component.frostSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.verticalScroll
import org.openactivity.app.feature.suggest.IntroCard
import org.openactivity.app.feature.segments.SegmentListRow
import org.openactivity.app.feature.segments.SegmentRowUi
import androidx.compose.material.icons.rounded.Timeline
import org.openactivity.app.navigation.LocalBottomBarPadding
import org.openactivity.app.feature.suggest.RefreshButton
import org.openactivity.app.feature.suggest.RouteSuggestionsViewModel
import org.openactivity.app.feature.suggest.SuggestionActions
import org.openactivity.app.feature.suggest.SuggestionCardUi
import org.openactivity.app.feature.suggest.SuggestionFilterRow
import org.openactivity.app.feature.suggest.SuggestionPager
import org.openactivity.app.feature.suggest.SuggestionsBody
import org.openactivity.app.feature.suggest.SuggestionsStatus
import org.openactivity.app.feature.suggest.SuggestionsUiState
import org.openactivity.app.feature.suggest.rememberSuggestionActions
import org.openactivity.app.feature.suggest.subtitle
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
import org.openactivity.geo.PlanarGeometry
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import kotlin.math.cos
import kotlin.math.pow
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
import androidx.compose.ui.unit.Dp
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
fun ExploreScreen(
    onOpenRoute: (Long) -> Unit,
    onOpenSuggestion: (String) -> Unit = {},
    onOpenSegment: (Long) -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel(),
    suggestionsViewModel: RouteSuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by suggestionsViewModel.uiState.collectAsStateWithLifecycle()
    val suggestionActions = rememberSuggestionActions(suggestionsViewModel, onOpenSuggestion)
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
            onOpenSegment = onOpenSegment,
            onToggleSegmentStar = viewModel::toggleSegmentStar,
        ),
        suggestions = suggestions,
        suggestionActions = suggestionActions,
    )
}

/** Callbacks for [ExploreContent]. */
data class ExploreActions(
    val onImport: () -> Unit = {},
    val onOpenRoute: (Long) -> Unit = {},
    val onShowOnMap: (Long) -> Unit = {},
    /** The heatmap needs a server; this explains that. */
    val onNotAvailable: (String) -> Unit = {},
    val onOpenSegment: (Long) -> Unit = {},
    val onToggleSegmentStar: (Long, Boolean) -> Unit = { _, _ -> },
)

/**
 * Stateless Maps tab (strava direction): a full-screen map with a search pill and layers button, a chip
 * row (sport, Heatmap, Routes, Segments), 3D and locate buttons, a dark "Create route" pill, and a
 * bottom sheet with suggested loops near you (sport and distance chips) above your saved routes.
 */
@Composable
fun ExploreContent(
    state: ExploreUiState,
    actions: ExploreActions,
    modifier: Modifier = Modifier,
    suggestions: SuggestionsUiState = SuggestionsUiState(status = SuggestionsStatus.Disabled),
    suggestionActions: SuggestionActions = SuggestionActions(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var styleKind by rememberSaveable { mutableStateOf<MapStyleKind?>(null) }
    var tilted by rememberSaveable { mutableStateOf(false) }
    var sportFilter by rememberSaveable { mutableStateOf<SportType?>(null) }
    // The map shows the suggestions (strava) until a saved route is picked; swiping a card goes back to them.
    var showSaved by rememberSaveable { mutableStateOf(false) }
    var suggestionId by rememberSaveable { mutableStateOf<String?>(null) }
    // The Segments layer: your segments on the map and in the sheet, in place of the routes.
    var showSegments by rememberSaveable { mutableStateOf(false) }
    var segmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    val segments = state.segments.filter { sportFilter == null || it.sport == sportFilter }
    val segment = segments.firstOrNull { it.id == segmentId } ?: segments.firstOrNull()
    val mapState = rememberMapState()
    val routes = state.routes
        .filter { sportFilter == null || it.sport == sportFilter }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val selected = state.selectedRoute?.takeIf { it in routes } ?: routes.firstOrNull()
    val cards = suggestions.cards
    val suggestion = cards.firstOrNull { it.id == suggestionId } ?: cards.firstOrNull()
    val onMap = if (showSegments) {
        OnMap(segment?.points.orEmpty(), segment?.let { "g:${it.id}" }, segments.filter { it.id != segment?.id }.map { it.points })
    } else if (!showSaved && suggestion != null) {
        OnMap(suggestion.points, "s:${suggestion.id}", cards.filter { it.id != suggestion.id }.map { it.points })
    } else {
        OnMap(selected?.points.orEmpty(), selected?.id, emptyList())
    }
    val selectSuggestion: (String) -> Unit = { suggestionId = it; showSaved = false }
    val sheetActions = actions.copy(onShowOnMap = { showSaved = true; actions.onShowOnMap(it) })
    val carousel: @Composable () -> Unit = {
        SuggestionPager(cards, suggestion?.id, onSelect = selectSuggestion, onOpen = suggestionActions.onOpen)
    }
    val onMapClick: (LatLng) -> Unit = { tap ->
        val zoom = mapState.camera?.zoom
        if (showSegments) {
            nearestLine(segments.map { it.id to it.points }, tap, zoom)?.let { segmentId = it }
        } else {
            nearestLine(cards.map { it.id to it.points }, tap, zoom)?.let(selectSuggestion)
        }
    }
    val layer = MapLayer(showSegments) { showSegments = it }
    val segmentsSheet: @Composable (Modifier, Boolean) -> Unit = { m, side ->
        SegmentsSheet(segments, segment?.id, state.isLoading, actions, onSelect = { segmentId = it }, modifier = m, side = side)
    }
    val dark = isMapThemeDark()
    // Landscape: the routes sit in a side panel so the map keeps the full height (a bottom sheet hid it).
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val landscape = maxWidth > maxHeight
        val sheetMax = maxHeight * SHEET_MAX_FRACTION
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                ExploreMap(state, onMap, onMapClick, actions, mapState, query, { query = it }, styleKind, { styleKind = it }, tilted, { tilted = !tilted }, sportFilter, { sportFilter = it }, dark, Modifier.weight(1f).fillMaxHeight(), bottomInset = LocalBottomBarPadding.current, layer = layer)
                if (showSegments) {
                    segmentsSheet(Modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight(), true)
                } else {
                    RoutesSheet(routes, selected.takeIf { showSaved }, state, sheetActions, suggestions, suggestionActions, carousel, Modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight(), side = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ExploreMap(state, onMap, onMapClick, actions, mapState, query, { query = it }, styleKind, { styleKind = it }, tilted, { tilted = !tilted }, sportFilter, { sportFilter = it }, dark, Modifier.weight(1f).fillMaxWidth(), layer = layer)
                if (showSegments) {
                    segmentsSheet(Modifier.heightIn(max = sheetMax), false)
                } else {
                    RoutesSheet(routes, selected.takeIf { showSaved }, state, sheetActions, suggestions, suggestionActions, carousel, Modifier.heightIn(max = sheetMax))
                }
            }
        }
    }
}

/**
 * What the map draws: the [highlight] route in orange (refitted when [key] changes) over the muted
 * [alternates] (strava: the other suggestions).
 */
private data class OnMap(val highlight: List<LatLng>, val key: Any?, val alternates: List<List<LatLng>>)

/** Which layer the chips show: your routes and suggestions, or your segments. */
private class MapLayer(val segments: Boolean, val onSegments: (Boolean) -> Unit)

/** The line (suggestion or segment) within a finger's width of [tap] at [zoom], nearest first. */
private fun <K> nearestLine(lines: List<Pair<K, List<LatLng>>>, tap: LatLng, zoom: Double?): K? {
    if (zoom == null) return null
    // A 512-dp world at zoom 0: metres per dp at this latitude and zoom.
    val metersPerDp = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(tap.latitude)) / (TILE_DP * 2.0.pow(zoom))
    val reach = TAP_REACH_DP * metersPerDp
    return lines
        .map { (id, points) -> id to points.zipWithNext().minOfOrNull { (a, b) -> PlanarGeometry.pointToSegmentDistance(tap, a, b) } }
        .filter { (_, d) -> d != null && d <= reach }
        .minByOrNull { (_, d) -> d!! }
        ?.first
}

private const val EARTH_CIRCUMFERENCE_M = 40_075_016.7
private const val TILE_DP = 512.0
private const val TAP_REACH_DP = 24.0

@Composable
private fun ExploreMap(
    state: ExploreUiState,
    onMap: OnMap,
    onMapClick: (LatLng) -> Unit,
    actions: ExploreActions,
    mapState: org.openactivity.core.map.compose.MapState,
    query: String,
    onQuery: (String) -> Unit,
    styleKind: MapStyleKind?,
    onStyle: (MapStyleKind) -> Unit,
    tilted: Boolean,
    onTilt: () -> Unit,
    sportFilter: SportType?,
    onSport: (SportType?) -> Unit,
    dark: Boolean,
    modifier: Modifier,
    // Landscape only: the map runs down behind the floating bar there.
    bottomInset: Dp = 0.dp,
    layer: MapLayer = MapLayer(false) {},
) {
        val frost = rememberFrost()
        Box(modifier) {
            CompositionLocalProvider(LocalFrost provides frost) {
                val points = onMap.highlight
                // Frame the route clear of the search pill and chips above and the buttons on the right.
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val fitPadding = MapPadding(FIT_SIDE_DP, statusBar.value + FIT_TOP_DP, FIT_RIGHT_DP, FIT_BOTTOM_DP + bottomInset.value)
                LaunchedEffect(onMap.key) { if (points.size >= 2) mapState.fitTo(points, fitPadding) }
                // Without a route to show, open on the athlete (once), as Record does.
                val location = state.userLocation
                LaunchedEffect(location != null, onMap.key == null) {
                    if (location != null && onMap.key == null && mapState.camera?.let { it.zoom < 10.0 } != false) mapState.followUser()
                }
                LaunchedEffect(tilted) {
                    mapState.camera?.let { mapState.moveTo(it.copy(tilt = if (tilted) TILT_3D else 0.0)) }
                }
                RidgelineMap(
                    modifier = Modifier.fillMaxSize().frostSource(frost),
                    state = mapState,
                    styleKind = styleKind ?: MapStyleKind.default(dark),
                    overlays = MapOverlays(
                        activityRoute = if (points.size >= 2) ActivityRouteOverlay(points, colorByPace = false) else null,
                        alternateRoutes = onMap.alternates,
                    ),
                    location = location,
                    onMapClick = onMapClick.takeIf { onMap.alternates.isNotEmpty() },
                )
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Spacing.s)) {
                    Row(Modifier.padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
                        SearchPill(query, onQuery, Modifier.weight(1f))
                        Spacer(Modifier.width(Spacing.m))
                        MapLayersButton(MapStyleKind.entries, styleKind ?: MapStyleKind.default(dark), onStyle)
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        SportChip(sportFilter, state.routes.mapNotNull { it.sport }.distinct(), onSport)
                        MapChip("Heatmap", selected = false) { actions.onNotAvailable(SERVER_NEEDED) }
                        MapChip("Routes", selected = !layer.segments) { layer.onSegments(false) }
                        MapChip("Segments", selected = layer.segments) { layer.onSegments(!layer.segments) }
                    }
                }
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    MapRoundButton(onClick = onTilt, modifier = Modifier.semantics { contentDescription = "3D view" }) {
                        Text("3D", style = MaterialTheme.typography.titleSmall, color = if (tilted) RidgelineTheme.colors.accent else RidgelineTheme.colors.onFloat)
                    }
                    MapRoundButton(
                        onClick = {
                            when {
                                location != null -> mapState.followUser()
                                points.size >= 2 -> mapState.fitTo(points, fitPadding)
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = if (location != null) "Show my location" else "Show the route" },
                    ) { Icon(Icons.Rounded.NearMe, contentDescription = null) }
                }
                CreateRoutePill(state.isImporting, actions, Modifier.align(Alignment.BottomEnd).padding(bottom = bottomInset).padding(Spacing.gutter))
            }
        }
}

private val SIDE_PANEL_WIDTH = 340.dp
private const val SHEET_MAX_FRACTION = 0.5f
private const val FIT_SIDE_DP = 48f
private const val FIT_RIGHT_DP = 84f
private const val FIT_TOP_DP = 136f
private const val FIT_BOTTOM_DP = 96f

private const val TILT_3D = 60.0
private const val SERVER_NEEDED = "Needs a server account. That is coming soon."

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    FrostedSurface(shape = PillShape, modifier = modifier.height(52.dp)) {
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
    // Off chips frost like the other controls; the on chip stays solid dark, the strava accent.
    FrostedSurface(
        shape = PillShape,
        modifier = Modifier.semantics { this.selected = selected },
        onClick = onClick,
        color = if (selected) RidgelineTheme.colors.pillInverse else RidgelineTheme.colors.float,
        contentColor = if (selected) RidgelineTheme.colors.onPillInverse else RidgelineTheme.colors.onFloat,
        frost = if (selected) null else LocalFrost.current,
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

/**
 * The flat dark "Create route" pill at the bottom of the map. A route is created from a GPX, TCX or FIT
 * file; drawing one on the map is not available yet, and the menu says so.
 */
@Composable
private fun CreateRoutePill(importing: Boolean, actions: ExploreActions, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        CreatePillSurface(importing, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("From a GPX, TCX or FIT file") },
                leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                onClick = { open = false; actions.onImport() },
            )
            DropdownMenuItem(
                text = { Text("Draw on the map (coming soon)") },
                leadingIcon = { Icon(Icons.Rounded.Route, null) },
                onClick = { open = false; actions.onNotAvailable(DRAW_NOT_YET) },
            )
        }
    }
}

private const val DRAW_NOT_YET = "Drawing routes on the map is coming soon. Import a GPX file meanwhile."

@Composable
private fun CreatePillSurface(importing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            Text(if (importing) "Importing…" else "Create route", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The sheet (strava direction): "Suggested routes" are real loops routed from where you are, with sport
 * and distance chips and a refresh icon; "Your routes" below are the routes saved on this device. The
 * sheet scrolls when both do not fit. [side] is the landscape side panel.
 */
@Composable
private fun RoutesSheet(
    routes: List<RouteCardUi>,
    selected: RouteCardUi?,
    state: ExploreUiState,
    actions: ExploreActions,
    suggestions: SuggestionsUiState,
    suggestionActions: SuggestionActions,
    carousel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    side: Boolean = false,
) {
    Surface(modifier, shape = if (side) RectangleShape else SheetTopShape, color = RidgelineTheme.colors.sheetOverMap, shadowElevation = Elevation.sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .let { if (side) it.statusBarsPadding() else it }
                .verticalScroll(rememberScrollState())
                .padding(top = Spacing.s, bottom = Spacing.l + LocalBottomBarPadding.current),
        ) {
            if (!side) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .background(RidgelineTheme.colors.borderStrong, PillShape),
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Suggested routes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                RefreshButton(suggestions, suggestionActions.onRefresh)
            }
            if (suggestions.status != SuggestionsStatus.Disabled) {
                Text(
                    subtitle(suggestions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
                if (suggestions.showIntro) IntroCard(suggestionActions, Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
                SuggestionFilterRow(suggestions, suggestionActions)
            }
            SuggestionsBody(suggestions, suggestionActions, carousel)
            YourRoutes(routes, selected, state, actions, side)
        }
    }
}

/**
 * The sheet with the Segments layer on: your segments (starred first) with their best time and a star;
 * tapping one shows it on the map, tapping it again opens it.
 */
@Composable
private fun SegmentsSheet(
    segments: List<SegmentRowUi>,
    selectedId: Long?,
    loading: Boolean,
    actions: ExploreActions,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    side: Boolean = false,
) {
    Surface(modifier, shape = if (side) RectangleShape else SheetTopShape, color = RidgelineTheme.colors.sheetOverMap, shadowElevation = Elevation.sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .let { if (side) it.statusBarsPadding() else it }
                .verticalScroll(rememberScrollState())
                .padding(top = Spacing.s, bottom = Spacing.l + LocalBottomBarPadding.current),
        ) {
            if (!side) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .background(RidgelineTheme.colors.borderStrong, PillShape),
                )
            }
            Text(
                "Your segments",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m),
            )
            Text(
                if (segments.isEmpty()) "On this device" else "${segments.size} on this device · starred ones show up while you record",
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            if (!loading && segments.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Timeline, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
                    Spacer(Modifier.width(Spacing.m))
                    Text(
                        "No segments yet. Open an activity, tap More and choose Create segment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RidgelineTheme.colors.textSecondary,
                    )
                }
            }
            segments.forEach { row ->
                Box(Modifier.background(if (row.id == selectedId) RidgelineTheme.colors.gap else androidx.compose.ui.graphics.Color.Transparent)) {
                    SegmentListRow(
                        row,
                        onOpen = { if (row.id == selectedId) actions.onOpenSegment(row.id) else onSelect(row.id) },
                        onToggleStar = { actions.onToggleSegmentStar(row.id, row.starred) },
                    )
                }
            }
        }
    }
}

/** "Your routes": the saved routes, or a one-line import prompt. */
@Composable
private fun YourRoutes(routes: List<RouteCardUi>, selected: RouteCardUi?, state: ExploreUiState, actions: ExploreActions, side: Boolean) {
    Text(
        "Your routes",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m),
    )
    if (!state.isLoading && state.routes.isEmpty()) {
        NoRoutes(state.isImporting, actions.onImport)
        return
    }
    Text(
        savedLine(routes),
        style = MaterialTheme.typography.bodyMedium,
        color = RidgelineTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = Spacing.gutter),
    )
    val card: @Composable (RouteCardUi) -> Unit = { route ->
        RouteCard(
            route,
            isOnMap = route.id == selected?.id,
            onClick = { if (route.id == selected?.id) actions.onOpenRoute(route.id) else actions.onShowOnMap(route.id) },
            onOpen = { actions.onOpenRoute(route.id) },
        )
    }
    if (side) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) { routes.forEach { card(it) } }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) { items(routes, key = { it.id }) { card(it) } }
    }
}

/** "Run · 3 saved routes · on this device" from the routes shown. */
private fun savedLine(routes: List<RouteCardUi>): String {
    val sports = routes.mapNotNull { it.sport }.distinct()
    val sport = sports.singleOrNull()?.label
    val count = if (routes.size == 1) "1 saved route" else "${routes.size} saved routes"
    return listOfNotNull(sport, count, "on this device").joinToString(" · ")
}

/** One compact line instead of a half-screen empty state, so the map stays in view. */
@Composable
private fun NoRoutes(importing: Boolean, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Route, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
        Spacer(Modifier.width(Spacing.m))
        Text(
            if (importing) "Importing…" else "No routes yet. Create one from a GPX file to see it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onImport, enabled = !importing) { Text("Import", color = RidgelineTheme.colors.accentText) }
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
            RouteMapThumbnail(
                route.points,
                contentDescription = "Route shape of ${route.name}",
                aspectRatio = CARD_WIDTH / 88.dp,
                shape = RectangleShape,
                cacheId = "route-${route.id}",
            )
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
