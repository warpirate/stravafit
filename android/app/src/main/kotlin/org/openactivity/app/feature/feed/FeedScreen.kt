// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.notInThisBuild
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.Spacing

/** Feed tab, connected to [FeedViewModel]. */
@Composable
fun FeedScreen(onRecord: () -> Unit, viewModel: FeedViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    FeedContent(
        state = state,
        onSelectScope = viewModel::selectScope,
        cardActions = ActivityCardActions(
            onOpen = { messenger.show(notInThisBuild("Activity details")) },
            onOpenAthlete = { messenger.show(notInThisBuild("Profiles")) },
            onHighFive = viewModel::toggleHighFive,
            onComment = { messenger.show(notInThisBuild("Comments")) },
            onShare = { messenger.show(notInThisBuild("Sharing")) },
            onMore = { messenger.show(notInThisBuild("Activity options")) },
        ),
        onSearch = { messenger.show(notInThisBuild("Search")) },
        onNotifications = { messenger.show(notInThisBuild("Notifications")) },
        onFindPeople = { messenger.show(notInThisBuild("Find people")) },
        onRecord = onRecord,
    )
}

/** Stateless Feed screen: loading skeletons, empty state, or the chronological card list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedContent(
    state: FeedUiState,
    onSelectScope: (FeedScope) -> Unit,
    cardActions: ActivityCardActions,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onFindPeople: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(title = "Feed", scrollBehavior = scrollBehavior) {
                TopBarAction(Icons.Rounded.Search, "Search people and clubs", onSearch)
                TopBarAction(Icons.Rounded.Notifications, "Notifications", onNotifications, badgeCount = state.unreadNotifications)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item(key = "scope") { ScopeSwitch(state.scope, onSelectScope) }
                when {
                    state.isLoading -> items(SKELETON_COUNT) { ActivityCardSkeleton() }
                    state.isEmpty -> item(key = "empty") { FeedEmpty(state.scope, onFindPeople, onRecord) }
                    else -> items(state.cards, key = { it.id }) { ActivityCard(it, cardActions) }
                }
            }
        }
    }
}

private const val SKELETON_COUNT = 3

@Composable
private fun ScopeSwitch(selected: FeedScope, onSelect: (FeedScope) -> Unit) {
    Row(Modifier.padding(bottom = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FeedScope.entries.forEach { scope ->
            RidgelineFilterChip(scope.label, selected = scope == selected, onClick = { onSelect(scope) })
        }
    }
}

@Composable
private fun FeedEmpty(scope: FeedScope, onFindPeople: () -> Unit, onRecord: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (scope) {
            FeedScope.Following -> EmptyState(
                icon = Icons.Rounded.DynamicFeed,
                title = "Your feed is quiet",
                body = "Follow people or record your first activity to see it here.",
                primaryActionLabel = "Find people",
                onPrimaryAction = onFindPeople,
                secondaryActionLabel = "Record an activity",
                onSecondaryAction = onRecord,
            )
            FeedScope.You -> EmptyState(
                icon = Icons.Rounded.DynamicFeed,
                title = "No activities yet",
                body = "Your recorded and imported activities will show up here.",
                primaryActionLabel = "Record an activity",
                onPrimaryAction = onRecord,
            )
        }
    }
}
