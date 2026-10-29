// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.model.Sport
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.notInThisBuild
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.component.StatGrid
import org.openactivity.core.designsystem.component.StatRow
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Me tab, connected to [MeViewModel]. */
@Composable
fun MeScreen(onOpenSettings: () -> Unit, onRecord: () -> Unit, viewModel: MeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    MeContent(
        state = state,
        actions = MeActions(
            onOpenSettings = onOpenSettings,
            onShareProfile = { messenger.show(notInThisBuild("Profile sharing")) },
            onSelectTab = viewModel::selectTab,
            onSelectSport = viewModel::selectSport,
            onOpenActivity = { messenger.show(notInThisBuild("Activity details")) },
            onOpenFollowers = { messenger.show(notInThisBuild("Followers")) },
            onOpenFollowing = { messenger.show(notInThisBuild("Following")) },
            onEditProfile = { messenger.show(notInThisBuild("Profile editing")) },
            onRecord = onRecord,
        ),
    )
}

/** Callbacks for [MeContent]. */
data class MeActions(
    val onOpenSettings: () -> Unit = {},
    val onShareProfile: () -> Unit = {},
    val onSelectTab: (MeTab) -> Unit = {},
    val onSelectSport: (Sport) -> Unit = {},
    val onOpenActivity: (String) -> Unit = {},
    val onOpenFollowers: () -> Unit = {},
    val onOpenFollowing: () -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onRecord: () -> Unit = {},
)

/** Stateless Me tab: profile header, Progress / Activities tabs. */
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
                TopBarAction(Icons.Rounded.Share, "Share profile", actions.onShareProfile)
                TopBarAction(Icons.Rounded.Settings, "Settings", actions.onOpenSettings)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                if (state.isLoading || state.header == null) {
                    item { ActivityCardSkeleton(Modifier.padding(Spacing.gutter)) }
                    return@LazyColumn
                }
                item(key = "header") {
                    ProfileHeader(
                        state.header,
                        onOpenFollowers = actions.onOpenFollowers,
                        onOpenFollowing = actions.onOpenFollowing,
                        onEditProfile = actions.onEditProfile,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                    )
                }
                item(key = "tabs") { MeTabs(state.tab, actions.onSelectTab) }
                when (state.tab) {
                    MeTab.Progress -> progressItems(state, actions)
                    MeTab.Activities -> activityItems(state, actions)
                }
            }
        }
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

private fun LazyListScope.progressItems(state: MeUiState, actions: MeActions) {
    item(key = "sports") {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            state.sports.forEach { sport ->
                RidgelineFilterChip(sport.label, selected = sport == state.selectedSport, onClick = { actions.onSelectSport(sport) }, leadingIcon = sport.icon)
            }
        }
    }
    item(key = "week") {
        RidgelineCard(Modifier.padding(horizontal = Spacing.gutter)) {
            Text("This week", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.m))
            StatRow(state.thisWeek)
            state.weeklyChart?.let {
                Spacer(Modifier.height(Spacing.l))
                WeeklyChart(it)
            }
        }
    }
    item(key = "stats-header") {
        SectionHeader(
            "${state.selectedSport?.label ?: ""} stats",
            modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m),
        )
    }
    item(key = "stats") {
        RidgelineCard(Modifier.padding(horizontal = Spacing.gutter)) {
            Text("This year", style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
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
}

private fun LazyListScope.activityItems(state: MeUiState, actions: MeActions) {
    if (state.activities.isEmpty()) {
        item(key = "empty") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.DirectionsRun,
                    title = "No activities yet",
                    body = "Record your first one or import files from another app.",
                    primaryActionLabel = "Record an activity",
                    onPrimaryAction = actions.onRecord,
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
