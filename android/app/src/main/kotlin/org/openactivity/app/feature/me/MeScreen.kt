// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.component.StatGrid
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/** Me tab, connected to [MeViewModel]. */
@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onRecord: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MeContent(
        state = state,
        actions = MeActions(
            onOpenSettings = onOpenSettings,
            onSelectTab = viewModel::selectTab,
            onSelectSport = viewModel::selectSport,
            onSearch = viewModel::search,
            onFilterActivities = viewModel::filterActivities,
            onOpenActivity = onOpenActivity,
            onEditProfile = onEditProfile,
            onRecord = onRecord,
        ),
    )
}

/** Callbacks for [MeContent]. */
data class MeActions(
    val onOpenSettings: () -> Unit = {},
    val onSelectTab: (MeTab) -> Unit = {},
    val onSelectSport: (SportType?) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onFilterActivities: (SportType?) -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onRecord: () -> Unit = {},
)

/** Stateless Me tab: profile header, then the Progress or Activities sub-tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeContent(state: MeUiState, actions: MeActions, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(title = "Me", scrollBehavior = scrollBehavior) {
                TopBarAction(Icons.Rounded.Settings, "Settings", actions.onOpenSettings)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                val header = state.header
                if (state.isLoading || header == null) {
                    item { ActivityCardSkeleton(Modifier.padding(Spacing.gutter)) }
                    return@LazyColumn
                }
                item(key = "header") {
                    ProfileHeader(
                        header,
                        onEditProfile = actions.onEditProfile,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                    )
                }
                item(key = "tabs") { MeTabs(state.tab, actions.onSelectTab) }
                when {
                    !state.hasAnyActivity -> item(key = "empty") { NoActivities(actions.onRecord) }
                    state.tab == MeTab.Progress -> progressItems(state, actions)
                    else -> activityItems(state, actions)
                }
            }
        }
    }
}

@Composable
private fun NoActivities(onRecord: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.DirectionsRun,
            title = "No activities yet",
            body = "Your weekly progress, totals and best efforts build up from the activities you record.",
            primaryActionLabel = "Record an activity",
            onPrimaryAction = onRecord,
        )
    }
}

@Composable
private fun MeTabs(selected: MeTab, onSelect: (MeTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        indicator = { positions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(positions[selected.ordinal]),
                width = 64.dp,
                height = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        divider = { HorizontalDivider(color = RidgelineTheme.colors.hairline) },
        modifier = Modifier.padding(top = Spacing.s),
    ) {
        MeTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(tab.label, style = MaterialTheme.typography.titleSmall) },
                unselectedContentColor = RidgelineTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SportChips(sports: List<SportType>, selected: SportType?, allLabel: String, onSelect: (SportType?) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RidgelineFilterChip(allLabel, selected = selected == null, onClick = { onSelect(null) })
        sports.forEach { sport ->
            RidgelineFilterChip(sport.label, selected = sport == selected, onClick = { onSelect(sport) }, leadingIcon = sport.icon)
        }
    }
}

private fun LazyListScope.progressItems(state: MeUiState, actions: MeActions) {
    item(key = "sports") { SportChips(state.sports, state.selectedSport, "All sports", actions.onSelectSport) }
    item(key = "week") {
        RidgelineCard(Modifier.padding(horizontal = Spacing.gutter)) {
            Text("This week", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.m))
            StatGrid(state.thisWeek, columns = 2)
            state.weeklyChart?.let {
                Spacer(Modifier.height(Spacing.l))
                WeeklyChart(it)
            }
        }
    }
    item(key = "stats-header") {
        SectionHeader(
            "${state.selectedSport?.label ?: "All sports"} totals",
            modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m),
        )
    }
    item(key = "stats") {
        RidgelineCard(Modifier.padding(horizontal = Spacing.gutter)) {
            Text("${state.year}", style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
            Spacer(Modifier.height(Spacing.m))
            StatGrid(state.yearToDate, columns = 2)
            Spacer(Modifier.height(Spacing.l))
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Spacer(Modifier.height(Spacing.l))
            Text("All time", style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
            Spacer(Modifier.height(Spacing.m))
            StatGrid(state.allTime, columns = 2)
        }
    }
    state.bestEffortsTitle?.let { title ->
        item(key = "bests-header") {
            SectionHeader(title, modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m))
        }
        item(key = "bests") { BestEffortsTable(state.bestEfforts, actions.onOpenActivity) }
    }
}

@Composable
private fun BestEffortsTable(efforts: List<BestEffortUi>, onOpenActivity: (Long) -> Unit) {
    RidgelineCard(Modifier.padding(horizontal = Spacing.gutter), contentPadding = Spacing.none) {
        efforts.forEachIndexed { index, effort ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.listRowMin)
                    .clickable { onOpenActivity(effort.activityId) }
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(effort.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(effort.time, style = RidgelineTheme.text.statS)
                    Text(effort.date, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
                }
            }
        }
    }
}

private fun LazyListScope.activityItems(state: MeUiState, actions: MeActions) {
    item(key = "search") {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onSearch,
            placeholder = { Text("Search your activities") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onSearch("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m),
        )
    }
    if (state.sports.size > 1) {
        item(key = "activity-sports") { SportChips(state.sports, state.activitySport, "All", actions.onFilterActivities) }
    }
    if (state.activities.isEmpty()) {
        item(key = "no-match") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No matching activities",
                    body = "Try another word or sport.",
                    primaryActionLabel = "Clear filters",
                    onPrimaryAction = {
                        actions.onSearch("")
                        actions.onFilterActivities(null)
                    },
                )
            }
        }
        return
    }
    items(state.activities, key = { it.id }) { row ->
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            ActivityRow(row, onClick = { actions.onOpenActivity(row.id) })
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
        }
    }
}
