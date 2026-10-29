// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.app.ui.common.ProfileAvatar
import org.openactivity.app.ui.common.SportTile
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.StatRow
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.thumbnail.RouteMapThumbnail

/** Callbacks for an [ActivityCard]. */
data class ActivityCardActions(
    val onOpen: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
)

/**
 * Feed activity card (design-system 4.5): header with avatar, time and visibility, title,
 * sport-specific stats, the route map (a map snapshot when online or cached, else the offline
 * drawing) or a sport tile for activities without GPS, and the high-five count.
 * Own cards have no high-five button (home-feed 3.5).
 */
@Composable
fun ActivityCard(card: ActivityCardUi, actions: ActivityCardActions, modifier: Modifier = Modifier) {
    RidgelineCard(modifier = modifier.fillMaxWidth(), onClick = { actions.onOpen(card.id) }) {
        CardHeader(card, actions)
        Spacer(Modifier.height(Spacing.m))
        Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        card.description?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Spacing.m))
        StatRow(card.stats)
        Spacer(Modifier.height(Spacing.m))
        if (card.route != null) {
            RouteMapThumbnail(card.route, contentDescription = "Route map of ${card.title}")
        } else {
            SportTile(card.sport)
        }
        Spacer(Modifier.height(Spacing.m))
        HighFiveCount(card.highFiveCount)
    }
}

@Composable
private fun CardHeader(card: ActivityCardUi, actions: ActivityCardActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(card.athleteName, card.athletePhotoPath, size = AvatarSize.m)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(card.athleteName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(card.sport.icon, contentDescription = card.sport.label, modifier = Modifier.size(14.dp), tint = RidgelineTheme.colors.textMuted)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    card.meta,
                    style = RidgelineTheme.text.caption,
                    color = RidgelineTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                card.visibilityLabel?.let { VisibilityTag(it) }
            }
        }
        CardMenu(onOpen = { actions.onOpen(card.id) }, onDelete = { actions.onDelete(card.id) })
    }
}

/** Overflow menu for own cards (home-feed 7.1). */
@Composable
private fun CardMenu(onOpen: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = RidgelineTheme.colors.textSecondary)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Open") }, onClick = {
            open = false
            onOpen()
        })
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            onClick = {
                open = false
                onDelete()
            },
        )
    }
}

/** Lock plus label: visibility only, never a paywall. */
@Composable
private fun VisibilityTag(label: String) {
    Row(Modifier.padding(start = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(" · ", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = RidgelineTheme.colors.textMuted)
        Spacer(Modifier.width(2.dp))
        Text(label, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun HighFiveCount(count: Int) {
    val text = if (count == 1) "1 high-five" else "$count high-fives"
    Row(
        Modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.PanTool, contentDescription = null, modifier = Modifier.size(16.dp), tint = RidgelineTheme.colors.textMuted)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
    }
}
