// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/** Feed tab, connected to [FeedViewModel]. */
@Composable
fun FeedScreen(onRecord: () -> Unit, onOpenActivity: (Long) -> Unit, viewModel: FeedViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    FeedContent(
        state = state,
        actions = FeedActions(
            onSelectScope = viewModel::selectScope,
            onSelectSport = viewModel::selectSport,
            onOpenActivity = onOpenActivity,
            onDeleteActivity = viewModel::delete,
            onRecord = onRecord,
        ),
    )
}

/** Callbacks for [FeedContent]. */
data class FeedActions(
    val onSelectScope: (FeedScope) -> Unit = {},
    val onSelectSport: (SportType?) -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
    val onDeleteActivity: (Long) -> Unit = {},
    val onRecord: () -> Unit = {},
)

/** Stateless Feed screen: scope switch, sport filter, then skeletons, an empty state, or the cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedContent(state: FeedUiState, actions: FeedActions, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { RidgelineTopAppBar(title = "Feed", scrollBehavior = scrollBehavior) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item(key = "scope") { ScopeSwitch(state.scope, actions.onSelectScope) }
                if (state.scope == FeedScope.You && state.sports.isNotEmpty()) {
                    item(key = "sports") { SportFilter(state.sports, state.sportFilter, actions.onSelectSport) }
                }
                when {
                    state.isLoading -> items(SKELETON_COUNT) { ActivityCardSkeleton() }
                    state.isEmpty -> item(key = "empty") { FeedEmpty(state, actions) }
                    else -> items(state.cards, key = { it.id }) { card ->
                        ActivityCard(card, ActivityCardActions(onOpen = actions.onOpenActivity, onDelete = { pendingDelete = it }))
                    }
                }
            }
        }
    }
    pendingDelete?.let { id ->
        DeleteActivityDialog(
            onConfirm = {
                pendingDelete = null
                actions.onDeleteActivity(id)
            },
            onDismiss = { pendingDelete = null },
        )
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
private fun SportFilter(sports: List<SportType>, selected: SportType?, onSelect: (SportType?) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RidgelineFilterChip("All sports", selected = selected == null, onClick = { onSelect(null) })
        sports.forEach { sport ->
            RidgelineFilterChip(sport.label, selected = sport == selected, onClick = { onSelect(sport) }, leadingIcon = sport.icon)
        }
    }
}

@Composable
private fun FeedEmpty(state: FeedUiState, actions: FeedActions) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state.scope == FeedScope.Following -> EmptyState(
                icon = Icons.Rounded.CloudOff,
                title = "Sign in to a server to follow people",
                body = "Following athletes, their activities, high-fives and comments need a server account. " +
                    "That is coming soon. Your own activities stay on this device and appear under You.",
                primaryActionLabel = "Show your activities",
                onPrimaryAction = { actions.onSelectScope(FeedScope.You) },
            )
            state.sportFilter != null -> EmptyState(
                icon = Icons.Rounded.FilterAltOff,
                title = "No ${state.sportFilter.label.lowercase()} activities",
                body = "Pick another sport or show them all.",
                primaryActionLabel = "Show all sports",
                onPrimaryAction = { actions.onSelectSport(null) },
            )
            else -> EmptyState(
                icon = Icons.Rounded.DynamicFeed,
                title = "No activities yet",
                body = "Record your first activity and it shows up here, with its map and stats.",
                primaryActionLabel = "Record an activity",
                onPrimaryAction = actions.onRecord,
            )
        }
    }
}

/** Confirmation before deleting: the activity and its GPS streams cannot be recovered. */
@Composable
fun DeleteActivityDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this activity?") },
        text = { Text("Its map, stats and best efforts are removed from this device. This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
