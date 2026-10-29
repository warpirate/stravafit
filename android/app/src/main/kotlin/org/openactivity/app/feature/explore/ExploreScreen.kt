// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.RouteThumbnail
import org.openactivity.app.ui.notInThisBuild
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Explore tab, connected to [ExploreViewModel]. */
@Composable
fun ExploreScreen(viewModel: ExploreViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    ExploreContent(
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onSearch = { messenger.show(notInThisBuild("Place search")) },
        onOpenRoute = { messenger.show(notInThisBuild("Route details")) },
    )
}

/**
 * Stateless Explore placeholder (secondary-screens 1.1): search field, sport filters, a preview
 * of the highlighted route and nearby route cards. The live map arrives with MapLibre.
 */
@Composable
fun ExploreContent(
    state: ExploreUiState,
    onSelectFilter: (ExploreFilter) -> Unit,
    onSearch: () -> Unit,
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "search") { SearchField(onSearch) }
            item(key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ExploreFilter.entries.forEach { filter ->
                        RidgelineFilterChip(filter.label, selected = filter == state.filter, onClick = { onSelectFilter(filter) })
                    }
                }
            }
            state.routes.firstOrNull()?.let { featured ->
                item(key = "map") {
                    RouteThumbnail(featured.path, aspectRatio = 1.5f, contentDescription = "Map preview of ${featured.name}")
                }
            }
            item(key = "notice") { MapNotice() }
            item(key = "header") { SectionHeader("Routes near you") }
            items(state.routes, key = { it.id }) { route -> RouteCard(route, onClick = { onOpenRoute(route.id) }) }
        }
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(Modifier.padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = RidgelineTheme.colors.textSecondary)
            Spacer(Modifier.width(Spacing.m))
            Text("Search a place or route", style = MaterialTheme.typography.bodyLarge, color = RidgelineTheme.colors.textMuted)
        }
    }
}

@Composable
private fun MapNotice() {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(
                "Interactive maps, offline regions and the route builder are on the way. All free, built on OpenStreetMap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RouteCard(route: RouteCardUi, onClick: () -> Unit) {
    RidgelineCard(onClick = onClick, contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp)) {
                RouteThumbnail(route.path, aspectRatio = 1.4f, contentDescription = "Route shape of ${route.name}")
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.xxs))
                Text(route.details, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
            }
        }
    }
}
