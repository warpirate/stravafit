// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.suggest

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.routing.SuggestionSport
import org.openactivity.model.UnitSystem
import kotlin.math.roundToInt

/** Which filter sheet is open. */
private enum class FilterSheet { LENGTH, ELEVATION, SURFACE }

/**
 * The Maps filter row (strava): sport chips, then Length, Elevation and Surface chips that each open a
 * sheet. A chip is highlighted while its filter is set.
 */
@Composable
fun SuggestionFilterRow(state: SuggestionsUiState, actions: SuggestionActions, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    val filters = state.filters
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SuggestionSport.entries.forEach { sport ->
            RidgelineFilterChip(sport.label, selected = sport == state.sport, onClick = { actions.onSelectSport(sport) }, leadingIcon = sport.sportType.icon)
        }
        Box(Modifier.size(width = 1.dp, height = 24.dp).background(RidgelineTheme.colors.hairline))
        RidgelineFilterChip(
            if (filters.lengthMeters == null) "Length" else lengthLabel(filters.lengthMeters, state.units),
            selected = filters.lengthMeters != null,
            onClick = { open = FilterSheet.LENGTH },
        )
        RidgelineFilterChip(
            if (filters.elevation == ElevationFilter.ANY) "Elevation" else filters.elevation.label,
            selected = filters.elevation != ElevationFilter.ANY,
            onClick = { open = FilterSheet.ELEVATION },
        )
        RidgelineFilterChip(
            if (filters.surface == SurfaceFilter.ANY) "Surface" else filters.surface.label,
            selected = filters.surface != SurfaceFilter.ANY,
            onClick = { open = FilterSheet.SURFACE },
        )
    }
    when (open) {
        FilterSheet.LENGTH -> LengthSheet(state, onApply = { actions.onSetLength(it); open = null }, onDismiss = { open = null })
        FilterSheet.ELEVATION -> ChoiceSheet(
            title = "Elevation",
            note = "Flat is up to ${ElevationFilter.FLAT_MAX_M_PER_KM.roundToInt()} m of climb per km, hilly is " +
                "${ElevationFilter.HILLY_MIN_M_PER_KM.roundToInt()} m or more.",
            options = ElevationFilter.entries,
            label = { it.label },
            selected = filters.elevation,
            onSelect = { actions.onSetElevation(it); open = null },
            onDismiss = { open = null },
        )
        FilterSheet.SURFACE -> ChoiceSheet(
            title = "Surface",
            note = "From OpenStreetMap surface tags along each loop, looked up after the loops are found.",
            options = SurfaceFilter.entries,
            label = { it.label },
            selected = filters.surface,
            onSelect = { actions.onSetSurface(it); open = null },
            onDismiss = { open = null },
        )
        null -> Unit
    }
}

/** "2–8 km", "5 km", "1.2–5 mi". */
fun lengthLabel(range: ClosedFloatingPointRange<Double>, units: UnitSystem): String {
    val from = RouteSuggestionsViewModel.formatLength(range.start, units)
    val to = RouteSuggestionsViewModel.formatLength(range.endInclusive, units)
    return if (from == to) from else "${from.substringBefore(' ')}–$to"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LengthSheet(state: SuggestionsUiState, onApply: (ClosedFloatingPointRange<Double>?) -> Unit, onDismiss: () -> Unit) {
    val bounds = state.lengthBounds
    val stepMeters = if (bounds.endInclusive > 50_000) 5_000.0 else 500.0
    var range by remember(state.sport) {
        mutableStateOf(state.lengthRange.start.toFloat()..state.lengthRange.endInclusive.toFloat())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.m)) {
            Text("Length", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                lengthLabel(range.start.toDouble()..range.endInclusive.toDouble(), state.units),
                style = RidgelineTheme.text.statS,
            )
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                valueRange = bounds.start.toFloat()..bounds.endInclusive.toFloat(),
                steps = ((bounds.endInclusive - bounds.start) / stepMeters).roundToInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = RidgelineTheme.colors.accent,
                    activeTrackColor = RidgelineTheme.colors.accent,
                ),
            )
            Text(
                "Loops are routed at both ends of the range and in the middle.",
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textMuted,
            )
            Row(Modifier.fillMaxWidth().padding(top = Spacing.m), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onApply(null) }) { Text("Reset", color = RidgelineTheme.colors.textSecondary) }
                TextButton(onClick = { onApply(range.start.toDouble()..range.endInclusive.toDouble()) }) {
                    Text("Show loops", color = RidgelineTheme.colors.accentText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    note: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.l).selectableGroup()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.gutter))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = RidgelineTheme.colors.accent),
                    )
                    Spacer(Modifier.width(Spacing.m))
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Maps: the suggestion cards as a swipeable carousel (strava). The settled card is the one drawn in orange
 * on the map ([onSelect]); [selectedId] changing from outside (a tap on a line) scrolls to its card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestionPager(
    cards: List<SuggestionCardUi>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    val selectedIndex = cards.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = selectedIndex) { cards.size }
    LaunchedEffect(selectedIndex, cards.size) {
        if (pager.currentPage != selectedIndex) pager.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pager, cards) {
        snapshotFlow { pager.settledPage }.collect { page -> cards.getOrNull(page)?.let { onSelect(it.id) } }
    }
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = Spacing.gutter),
        pageSpacing = Spacing.m,
        modifier = modifier.padding(vertical = Spacing.m),
        key = { cards[it].id },
    ) { page ->
        val card = cards[page]
        SuggestionCard(
            card,
            onClick = { if (page == pager.currentPage) onOpen(card.id) else onSelect(card.id) },
            modifier = Modifier.fillMaxWidth(),
            wide = true,
        )
    }
}
