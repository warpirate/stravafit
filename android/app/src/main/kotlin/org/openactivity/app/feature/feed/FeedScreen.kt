// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import org.openactivity.app.navigation.LocalBottomBarPadding
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.RecordingStatusViewModel
import org.openactivity.app.feature.suggest.RouteSuggestionsViewModel
import org.openactivity.app.feature.suggest.SuggestedRoutesSection
import org.openactivity.app.feature.suggest.SuggestionActions
import org.openactivity.app.feature.suggest.SuggestionsStatus
import org.openactivity.app.feature.suggest.SuggestionsUiState
import org.openactivity.app.feature.suggest.rememberSuggestionActions
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.SecondaryButton
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
fun FeedScreen(
    onRecord: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onShareActivity: (Long) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenSuggestion: (String) -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel(),
    suggestionsViewModel: RouteSuggestionsViewModel = hiltViewModel(),
) {
    val suggestions by suggestionsViewModel.uiState.collectAsStateWithLifecycle()
    val suggestionActions = rememberSuggestionActions(suggestionsViewModel, onOpenSuggestion)
    val feed by viewModel.uiState.collectAsStateWithLifecycle()
    val recordingStatus: RecordingStatusViewModel = hiltViewModel()
    val recording by recordingStatus.active.collectAsStateWithLifecycle()
    val state = feed.copy(recordingInProgress = recording != null)
    val messenger = LocalMessenger.current
    // Leaving the feed (another tab, an activity, the home screen) counts as having seen it all.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.markAllSeen() }
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
            onShareActivity = onShareActivity,
            onNotAvailable = messenger::show,
            onOpenNotifications = onOpenNotifications,
        ),
        suggestions = suggestions,
        suggestionActions = suggestionActions,
    )
}

/** Callbacks for [FeedContent]. */
data class FeedActions(
    val onSelectScope: (FeedScope) -> Unit = {},
    val onSelectSport: (SportType?) -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
    val onDeleteActivity: (Long) -> Unit = {},
    val onRecord: () -> Unit = {},
    val onShareActivity: (Long) -> Unit = {},
    /** Search, messages, notifications and high-fives need a server account; this explains that. */
    val onNotAvailable: (String) -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
)

/**
 * Stateless Home screen (strava direction): "Home" with the "Following ▾" feed switcher and search,
 * messages and notifications on the right over a hairline; then "Suggested routes near you" (when
 * [suggestions] is given) and edge-to-edge activity cards separated by the grey `gap` band. Pulling down
 * routes new suggestions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedContent(
    state: FeedUiState,
    actions: FeedActions,
    modifier: Modifier = Modifier,
    suggestions: SuggestionsUiState? = null,
    suggestionActions: SuggestionActions = SuggestionActions(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = RidgelineTheme.colors.gap,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(
                title = "Home",
                scrollBehavior = scrollBehavior,
                titleTrailing = { ScopeSwitch(state.scope, actions.onSelectScope) },
            ) {
                TopBarAction(Icons.Rounded.Search, "Search", onClick = { actions.onNotAvailable(SERVER_NEEDED) })
                TopBarAction(Icons.AutoMirrored.Outlined.Chat, "Messages", onClick = { actions.onNotAvailable(SERVER_NEEDED) })
                NotificationsAction(state.hasUnreadNotifications, actions.onOpenNotifications)
            }
        },
    ) { padding ->
        // The pull indicator answers a pull only. Loops still arriving in the background are shown by the
        // section's own refresh spinner; the big indicator would float over the section's text instead.
        val loading = suggestions?.isRefreshing == true
        var pulled by remember { mutableStateOf(false) }
        LaunchedEffect(loading) { if (!loading) pulled = false }
        PullToRefreshBox(
            isRefreshing = pulled && loading,
            onRefresh = {
                pulled = true
                suggestionActions.onRefresh()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.l + LocalBottomBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                if (state.sports.isNotEmpty()) {
                    item(key = "sports") { SportFilter(state.sports, state.sportFilter, actions.onSelectSport) }
                }
                if (suggestions != null && suggestions.status != SuggestionsStatus.Disabled) {
                    item(key = "suggestions") { SuggestedRoutesSection(suggestions, suggestionActions) }
                }
                when {
                    state.isLoading -> items(SKELETON_COUNT) { ActivityCardSkeleton(Modifier.background(MaterialTheme.colorScheme.surface)) }
                    state.isEmpty -> item(key = "empty") { FeedEmpty(state, actions) }
                    else -> state.cards.forEachIndexed { index, card ->
                        if (state.showCaughtUpDivider && index == state.unseenCount) {
                            item(key = "caught-up") { CaughtUpDivider() }
                        }
                        item(key = card.id) {
                            ActivityCard(
                                card,
                                ActivityCardActions(
                                    onOpen = actions.onOpenActivity,
                                    onDelete = { pendingDelete = it },
                                    onShare = actions.onShareActivity,
                                    onHighFive = { actions.onNotAvailable(OWN_HIGH_FIVE) },
                                ),
                            )
                        }
                    }
                }
                if (!state.isLoading && !state.isEmpty) {
                    item(key = "follow") { FollowSuggestion(actions, caughtUpShownAbove = state.showCaughtUpDivider) }
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
private const val SERVER_NEEDED = "Needs a server account. That is coming soon."
private const val OWN_HIGH_FIVE = "High-fives come from the people who follow you."

/** "Following ▾": the feed switcher next to the title. */
@Composable
private fun ScopeSwitch(selected: FeedScope, onSelect: (FeedScope) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clickable(onClickLabel = "Switch feed") { open = true }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, style = RidgelineTheme.text.tab, color = RidgelineTheme.colors.textSecondary)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = RidgelineTheme.colors.textSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FeedScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scope.label) },
                    onClick = {
                        open = false
                        onSelect(scope)
                    },
                )
            }
        }
    }
}

@Composable
private fun SportFilter(sports: List<SportType>, selected: SportType?, onSelect: (SportType?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
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
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        when {
            state.sportFilter != null -> EmptyState(
                icon = Icons.Rounded.FilterAltOff,
                title = "No ${state.sportFilter.label.lowercase()} activities",
                body = "Pick another sport or show them all.",
                primaryActionLabel = "Show all sports",
                onPrimaryAction = { actions.onSelectSport(null) },
            )
            state.recordingInProgress -> EmptyState(
                icon = Icons.Rounded.DynamicFeed,
                title = "Recording in progress",
                body = "Your activity shows up here, with its map and stats, once you finish and save it.",
                primaryActionLabel = "Back to recording",
                onPrimaryAction = actions.onRecord,
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

/** The bell, with the accent dot while the inbox has unread entries. */
@Composable
private fun NotificationsAction(unread: Boolean, onClick: () -> Unit) {
    Box {
        TopBarAction(Icons.Outlined.Notifications, if (unread) "Notifications, unread" else "Notifications", onClick = onClick)
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(9.dp)
                    .background(RidgelineTheme.colors.accent, CircleShape),
            )
        }
    }
}

/**
 * "You're all caught up" between the unseen and the seen cards (home-feed 1.3): a check in a ring and
 * the line, centred between two hairlines, on the card surface.
 */
@Composable
internal fun CaughtUpDivider(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.l)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = RidgelineTheme.colors.hairline)
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = RidgelineTheme.colors.accent,
                modifier = Modifier.padding(horizontal = Spacing.m).size(28.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = RidgelineTheme.colors.hairline)
        }
        Spacer(Modifier.height(Spacing.s))
        Text(CAUGHT_UP, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text("You've seen all new activities", style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
    }
}

private const val CAUGHT_UP = "You're all caught up"

/**
 * Closes the feed under the cards instead of leaving a bare grey block: following other athletes needs a
 * server account, said once, honestly. With the caught-up divider already above, it does not repeat it.
 */
@Composable
private fun FollowSuggestion(actions: FeedActions, caughtUpShownAbove: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = RidgelineTheme.colors.textMuted, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(Spacing.s))
        Text(if (caughtUpShownAbove) "That's everything for now" else CAUGHT_UP, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Activities from athletes you follow appear here too once following is available. It needs a server account.",
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.m))
        SecondaryButton("Find athletes to follow", onClick = { actions.onNotAvailable(SERVER_NEEDED) })
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
