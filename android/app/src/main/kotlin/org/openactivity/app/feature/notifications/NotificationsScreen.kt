// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.notifications

import org.openactivity.core.designsystem.component.FrostedScaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Sizes
import org.openactivity.core.designsystem.theme.Spacing

/** The notifications inbox, connected to [NotificationsViewModel]. */
@Composable
fun NotificationsScreen(
    onNavigateUp: () -> Unit,
    onOpen: (InboxDestination) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.destinations.collect(onOpen) }
    NotificationsContent(state, onNavigateUp = onNavigateUp, onOpen = viewModel::open, onMarkAllRead = viewModel::markAllRead)
}

/** Stateless inbox: a list of rows, newest first, with "Mark all as read" in the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    onNavigateUp: () -> Unit = {},
    onOpen: (InboxItemUi) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RidgelineTopAppBar(title = "Notifications", onNavigateUp = onNavigateUp) {
                if (state.unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) { Text("Mark all as read", color = RidgelineTheme.colors.accentText) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> ActivityCardSkeleton(Modifier.padding(padding).padding(Spacing.gutter))
                state.items.isEmpty() -> EmptyState(
                    modifier = Modifier.padding(padding),
                    icon = Icons.Rounded.NotificationsNone,
                    title = "No notifications yet",
                    body = "New personal records, goals reached and your weekly summary show up here.",
                )
                else -> LazyColumn(
                    Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = Spacing.l),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        InboxRow(item, onClick = { onOpen(item) })
                        HorizontalDivider(color = RidgelineTheme.colors.hairline)
                    }
                }
            }
        }
    }
}

/** Icon disc, title, body and age; unread rows are tinted and their title is bold. */
@Composable
private fun InboxRow(item: InboxItemUi, onClick: () -> Unit) {
    val description = listOfNotNull(if (item.unread) "Unread" else null, item.title, item.body, item.age).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (item.unread) RidgelineTheme.colors.accentArea else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                onClick(label = "Open") { onClick(); true }
            }
            .heightIn(min = Spacing.listRowMin)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(Sizes.avatarFeed).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon(item.type), contentDescription = null, tint = iconTint(item.type), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (item.unread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.body.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.s))
        Text(item.age, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
    }
}

/** Material Symbols glyph per entry type (Apache-2.0). */
private fun icon(type: NotificationType): ImageVector = when (type) {
    NotificationType.PERSONAL_RECORD, NotificationType.BEST_EFFORT, NotificationType.SEGMENT_PR -> Icons.Rounded.WorkspacePremium
    NotificationType.GOAL_REACHED -> Icons.Rounded.TaskAlt
    NotificationType.GOAL_REMINDER -> Icons.Rounded.Flag
    NotificationType.WEEKLY_SUMMARY -> Icons.Rounded.Insights
    NotificationType.IMPORT_FINISHED -> Icons.Rounded.UploadFile
    NotificationType.IMPORT_FAILED -> Icons.Rounded.ErrorOutline
    NotificationType.BACKUP_FAILED -> Icons.Rounded.CloudOff
    NotificationType.RECORDING_RECOVERED -> Icons.Rounded.Restore
}

@Composable
private fun iconTint(type: NotificationType) = when (type) {
    NotificationType.PERSONAL_RECORD, NotificationType.SEGMENT_PR -> RidgelineTheme.colors.medalGold
    NotificationType.BEST_EFFORT -> RidgelineTheme.colors.medalSilver
    NotificationType.IMPORT_FAILED, NotificationType.BACKUP_FAILED -> MaterialTheme.colorScheme.error
    NotificationType.GOAL_REACHED -> RidgelineTheme.colors.success
    else -> RidgelineTheme.colors.accent
}
