// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.suggest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.component.SkeletonBlock
import org.openactivity.core.designsystem.component.StatValue
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.designsystem.theme.ThumbnailShape
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.map.thumbnail.RouteMapThumbnail

/** Callbacks of the suggestions section. */
data class SuggestionActions(
    val onOpen: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** Asks for the location permission. */
    val onAllowLocation: () -> Unit = {},
    /** Opens the system location settings. */
    val onLocationSettings: () -> Unit = {},
    val onDismissIntro: () -> Unit = {},
    val onSetEnabled: (Boolean) -> Unit = {},
    val onSelectSport: (org.openactivity.core.routing.SuggestionSport) -> Unit = {},
    val onSetLength: (ClosedFloatingPointRange<Double>?) -> Unit = {},
    val onSetElevation: (ElevationFilter) -> Unit = {},
    val onSetSurface: (SurfaceFilter) -> Unit = {},
    val onClearFilters: () -> Unit = {},
)

/**
 * Home: "Suggested routes near you", a horizontally scrolling row of loop cards that start where you
 * are. Hidden entirely when suggestions are turned off.
 */
@Composable
fun SuggestedRoutesSection(state: SuggestionsUiState, actions: SuggestionActions, modifier: Modifier = Modifier) {
    if (state.status == SuggestionsStatus.Disabled) return
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(top = Spacing.m, bottom = Spacing.l)) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Suggested routes near you", style = MaterialTheme.typography.titleMedium)
                    Text(subtitle(state), style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
                }
                RefreshButton(state, actions.onRefresh)
            }
            if (state.showIntro) IntroCard(actions, Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
            SuggestionsBody(state, actions)
        }
    }
}

/** "Run · 3–10 km loops from where you are", plus the elevation and surface filters when set. */
fun subtitle(state: SuggestionsUiState): String {
    val parts = listOf(state.sport.label, "${lengthLabel(state.lengthRange, state.units)} loops from where you are") +
        listOfNotNull(
            state.filters.elevation.takeIf { it != ElevationFilter.ANY }?.label,
            state.filters.surface.takeIf { it != SurfaceFilter.ANY }?.label,
        )
    return parts.joinToString(" · ")
}

/** The refresh icon, a spinner while routing. */
@Composable
fun RefreshButton(state: SuggestionsUiState, onRefresh: () -> Unit) {
    if (state.isRefreshing) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = RidgelineTheme.colors.accent)
        }
    } else if (state.status != SuggestionsStatus.Disabled && state.status != SuggestionsStatus.AwaitingConsent &&
        state.status !is SuggestionsStatus.NoLocation
    ) {
        IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "New suggestions", tint = RidgelineTheme.colors.textSecondary) }
    }
}

/**
 * Cards, or one quiet line explaining why there are none. Shared by Home and the Maps sheet; Maps passes
 * its own [carousel] (synced with the map) in place of the scrolling row.
 */
@Composable
fun SuggestionsBody(state: SuggestionsUiState, actions: SuggestionActions, carousel: (@Composable () -> Unit)? = null) {
    @Composable
    fun Cards() = if (carousel != null) carousel() else Cards(state, actions)
    when (val status = state.status) {
        SuggestionsStatus.Disabled -> Notice(
            Icons.Rounded.Route,
            "Online route suggestions are off.",
            "Turn on",
        ) { actions.onSetEnabled(true) }
        // The intro card above asks first; nothing to show until it is accepted.
        SuggestionsStatus.AwaitingConsent -> Unit
        is SuggestionsStatus.NoLocation -> when (status.reason) {
            LocationAvailability.NO_PERMISSION -> Notice(Icons.Rounded.LocationOff, "Allow location to see loops that start where you are.", "Allow", actions.onAllowLocation)
            LocationAvailability.DISABLED -> Notice(Icons.Rounded.LocationOff, "Turn on location to see loops that start where you are.", "Settings", actions.onLocationSettings)
            else -> Notice(Icons.Rounded.LocationOff, "Couldn't find your position. Try again outdoors.", "Retry", actions.onRetry)
        }
        SuggestionsStatus.Loading -> if (state.cards.isEmpty()) LoadingCards(state) else Cards()
        SuggestionsStatus.NoneFound -> if (state.hiddenByFilters > 0) {
            val loops = if (state.hiddenByFilters == 1) "the loop found" else "the ${state.hiddenByFilters} loops found"
            Notice(Icons.Rounded.FilterListOff, "None of $loops match your filters.", "Clear filters", actions.onClearFilters)
        } else {
            Notice(Icons.Rounded.Route, "No good loops found around here. Try another length or new suggestions.", "Retry", actions.onRefresh)
        }
        SuggestionsStatus.Offline -> if (state.cards.isEmpty()) {
            Notice(Icons.Rounded.CloudOff, "Can't reach the route service right now.", "Retry", actions.onRetry)
        } else {
            Column {
                Text(
                    "Offline · showing earlier suggestions",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                )
                Cards()
            }
        }
        SuggestionsStatus.Ready -> Cards()
    }
}

@Composable
private fun Cards(state: SuggestionsUiState, actions: SuggestionActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(state.cards, key = { it.id }) { SuggestionCard(it, onClick = { actions.onOpen(it.id) }) }
        if (state.isLoadingMore) item(key = "more") { CardSkeleton() }
    }
}

@Composable
private fun LoadingCards(state: SuggestionsUiState) {
    // With a surface filter, loops are found first and held back until their surface is known.
    val checking = state.filters.surface != SurfaceFilter.ANY && state.hiddenByFilters > 0
    Column {
        if (checking) {
            Text(
                "Checking road surfaces…",
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
        }
        Row(
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m).semantics { contentDescription = "Finding loops near you" },
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CardSkeleton()
            CardSkeleton()
        }
    }
}

@Composable
private fun CardSkeleton() {
    Surface(shape = ThumbnailShape, border = BorderStroke(1.dp, RidgelineTheme.colors.hairline), color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(CARD_WIDTH)) {
        Column {
            SkeletonBlock(height = MAP_HEIGHT, shape = RectangleShape)
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SkeletonBlock(width = 160.dp, height = 16.dp)
                SkeletonBlock(width = 100.dp, height = 12.dp)
                SkeletonBlock(width = 200.dp, height = 22.dp)
            }
        }
    }
}

/**
 * A loop card: route map, name, sport line, Distance / Elev Gain / Est. Time and the surface. [wide] fills
 * the given width (the Maps carousel) and leaves the map out, since the loop is drawn on the big map.
 */
@Composable
fun SuggestionCard(card: SuggestionCardUi, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ThumbnailShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RidgelineTheme.colors.hairline),
        modifier = (if (wide) modifier else modifier.width(CARD_WIDTH))
            .semantics { contentDescription = listOfNotNull(card.name, card.subtitle, card.surface).joinToString(", ") },
    ) {
        Column {
            if (!wide) {
                RouteMapThumbnail(card.points, contentDescription = "Route shape of ${card.name}", aspectRatio = CARD_WIDTH / MAP_HEIGHT, shape = RectangleShape)
            }
            Column(Modifier.padding(Spacing.m)) {
                Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(card.sport.icon, contentDescription = null, tint = RidgelineTheme.colors.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted, maxLines = 1)
                }
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    card.stats.forEach { StatValue(it, valueStyle = RidgelineTheme.text.statS) }
                }
                card.surface?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted, maxLines = 1)
                }
            }
        }
    }
}

/** One-time: what leaves the phone, with "Turn off" right there. */
@Composable
fun IntroCard(actions: SuggestionActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = ThumbnailShape, color = RidgelineTheme.colors.gap) {
        Column(Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m)) {
            Row {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = RidgelineTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "To suggest loops, your current position is sent to the OpenStreetMap routing service " +
                        "(routing.openstreetmap.de) and to Open-Meteo for elevation. Nothing else is shared, and " +
                        "suggestions are never added to your activities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textSecondary,
                )
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { actions.onSetEnabled(false) }) { Text("Turn off", color = RidgelineTheme.colors.textSecondary) }
                TextButton(onClick = actions.onDismissIntro) { Text("Show loops", color = RidgelineTheme.colors.accentText) }
            }
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
        Spacer(Modifier.width(Spacing.m))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = RidgelineTheme.colors.accentText) }
    }
}

internal val CARD_WIDTH = 252.dp
private val MAP_HEIGHT = 112.dp
