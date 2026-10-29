// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Hexagon
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.app.ui.common.RouteThumbnail
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.component.Avatar
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.component.Facepile
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.StatRow
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Callbacks for an [ActivityCard]. */
data class ActivityCardActions(
    val onOpen: (String) -> Unit = {},
    val onOpenAthlete: (String) -> Unit = {},
    val onHighFive: (String) -> Unit = {},
    val onComment: (String) -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onMore: (String) -> Unit = {},
)

/**
 * Feed activity card (design-system 4.5): header, title, inline stats, achievements, route map
 * (or a sport tile for indoor activities), high-five facepile and the action bar. Own cards have
 * no high-five button (home-feed 3.5).
 */
@Composable
fun ActivityCard(card: ActivityCardUi, actions: ActivityCardActions, modifier: Modifier = Modifier) {
    RidgelineCard(modifier = modifier.fillMaxWidth(), onClick = { actions.onOpen(card.id) }, contentPadding = 0.dp) {
        Column(Modifier.padding(start = Spacing.cardPadding, end = Spacing.cardPadding, top = Spacing.cardPadding)) {
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
            card.achievementText?.let {
                Spacer(Modifier.height(Spacing.m))
                AchievementPill(it)
            }
            Spacer(Modifier.height(Spacing.m))
            if (card.route != null) {
                RouteThumbnail(card.route, contentDescription = "Route map of ${card.title}")
            } else {
                IndoorTile(card)
            }
            if (card.highFiveCount > 0 || card.commentCount > 0) {
                Spacer(Modifier.height(Spacing.m))
                SocialProof(card)
            }
        }
        Spacer(Modifier.height(Spacing.s))
        HorizontalDivider(color = RidgelineTheme.colors.hairline)
        ActionBar(card, actions)
    }
}

@Composable
private fun CardHeader(card: ActivityCardUi, actions: ActivityCardActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(card.athleteName, size = AvatarSize.m, modifier = Modifier.clickable { actions.onOpenAthlete(card.id) })
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(card.athleteName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    card.sport.icon,
                    contentDescription = card.sport.label,
                    modifier = Modifier.size(14.dp),
                    tint = RidgelineTheme.colors.textMuted,
                )
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
            card.withLine?.let {
                Text(
                    it,
                    style = RidgelineTheme.text.caption,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { actions.onOpen(card.id) },
                )
            }
        }
        IconButton(onClick = { actions.onMore(card.id) }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = RidgelineTheme.colors.textSecondary)
        }
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
private fun AchievementPill(text: String) {
    Row(
        Modifier
            .background(RidgelineTheme.colors.signal, PillShape)
            .padding(start = Spacing.s, end = Spacing.m, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Hexagon, contentDescription = null, modifier = Modifier.size(14.dp), tint = RidgelineTheme.colors.onSignal)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = RidgelineTheme.colors.onSignal)
    }
}

/** Shown instead of an empty map for activities without GPS (home-feed 2.5). */
@Composable
private fun IndoorTile(card: ActivityCardUi) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(INDOOR_TILE_RATIO)
            .background(RidgelineTheme.colors.surfaceSunken, MaterialTheme.shapes.small)
            .semantics { contentDescription = "${card.sport.label}, no map" },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(card.sport.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(card.sport.label, style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
        }
    }
}

private const val INDOOR_TILE_RATIO = 4f

@Composable
private fun SocialProof(card: ActivityCardUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (card.highFiverNames.isNotEmpty()) {
            Facepile(card.highFiverNames, totalCount = card.highFiveCount)
            Spacer(Modifier.width(Spacing.xs))
        }
        val parts = buildList {
            if (card.highFiveCount > 0) add(if (card.highFiveCount == 1) "1 high-five" else "${card.highFiveCount} high-fives")
            if (card.commentCount > 0) add(if (card.commentCount == 1) "1 comment" else "${card.commentCount} comments")
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
    }
}

@Composable
private fun ActionBar(card: ActivityCardUi, actions: ActivityCardActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xxs)) {
        if (!card.isOwn) {
            HighFiveButton(card.highFiveGiven, card.highFiveCount) { actions.onHighFive(card.id) }
        }
        ActionBarButton(onClick = { actions.onComment(card.id) }) {
            Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null, modifier = Modifier.size(20.dp), tint = RidgelineTheme.colors.textSecondary)
            Text("Comment", style = MaterialTheme.typography.labelMedium, color = RidgelineTheme.colors.textSecondary)
        }
        ActionBarButton(onClick = { actions.onShare(card.id) }) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp), tint = RidgelineTheme.colors.textSecondary)
            Text("Share", style = MaterialTheme.typography.labelMedium, color = RidgelineTheme.colors.textSecondary)
        }
    }
}
