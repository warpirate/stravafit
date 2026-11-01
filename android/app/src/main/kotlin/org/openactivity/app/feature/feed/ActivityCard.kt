// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import org.openactivity.core.activityextras.ui.PhotoThumbnail
import org.openactivity.core.designsystem.component.Badge
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.graphics.RectangleShape
import org.openactivity.core.designsystem.component.StatValue
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.rounded.MoreHoriz
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
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.thumbnail.RouteMapThumbnail

/** Callbacks for an [ActivityCard]. */
data class ActivityCardActions(
    val onOpen: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onShare: (Long) -> Unit = {},
    val onHighFive: (Long) -> Unit = {},
)

/**
 * Feed activity card (strava direction): edge to edge, top to bottom: avatar header with
 * "Today at 6:12 AM · place", the title, an optional description, up to three stats with Title Case
 * labels and no dividers, the full-bleed route map (or a sport tile without GPS), the high-five count,
 * and a high-five / comment / share action row.
 */
@Composable
fun ActivityCard(card: ActivityCardUi, actions: ActivityCardActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Open activity") { actions.onOpen(card.id) },
    ) {
        Column(Modifier.padding(start = Spacing.cardPadding, end = Spacing.xs, top = Spacing.l)) {
            CardHeader(card, actions)
        }
        Column(Modifier.padding(horizontal = Spacing.cardPadding)) {
            Spacer(Modifier.height(Spacing.s))
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
            Row(horizontalArrangement = Arrangement.spacedBy(STAT_SPACING)) {
                card.stats.forEach { StatValue(it) }
            }
            card.achievements?.let {
                Spacer(Modifier.height(Spacing.m))
                AchievementsLine(it)
            }
            Spacer(Modifier.height(Spacing.l))
        }
        CardMedia(card)
        Column(Modifier.padding(horizontal = Spacing.cardPadding)) {
            Spacer(Modifier.height(Spacing.m))
            HighFiveCount(card.highFiveCount)
        }
        ActionRow(
            onHighFive = { actions.onHighFive(card.id) },
            onComment = { actions.onOpen(card.id) },
            onShare = { actions.onShare(card.id) },
        )
    }
}

/**
 * The card's media (home-feed 2.5, 2.6): the route map first, then the attached photos, as a
 * swipeable carousel with page dots underneath. One page shows no dots; an activity without GPS
 * or photos shows its sport tile instead of an empty map.
 */
@Composable
private fun CardMedia(card: ActivityCardUi) {
    val hasMap = card.route != null
    val pageCount = (if (hasMap) 1 else 0) + card.photos.size
    when {
        pageCount == 0 -> SportTile(card.sport, Modifier.padding(horizontal = Spacing.cardPadding))
        pageCount == 1 && hasMap -> MapPage(card)
        else -> {
            val pager = rememberPagerState { pageCount }
            Column {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().aspectRatio(MAP_ASPECT),
                    key = { page -> if (hasMap && page == 0) "map" else card.photos[if (hasMap) page - 1 else page].id },
                ) { page ->
                    if (hasMap && page == 0) {
                        MapPage(card)
                    } else {
                        val index = if (hasMap) page - 1 else page
                        PhotoThumbnail(
                            photo = card.photos[index],
                            contentDescription = "Photo ${index + 1} of ${card.photos.size} from ${card.title}",
                            targetSize = PHOTO_TARGET,
                            placeholder = RidgelineTheme.colors.surfaceSunken,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (pageCount > 1) PageDots(pageCount, pager.currentPage, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun MapPage(card: ActivityCardUi) {
    RouteMapThumbnail(
        card.route.orEmpty(),
        contentDescription = "Route map of ${card.title}",
        aspectRatio = MAP_ASPECT,
        shape = RectangleShape,
        cacheId = "activity-${card.id}",
    )
}

/** Carousel position: one dot per page, the current one filled in the text colour. */
@Composable
internal fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(top = Spacing.s)
            .clearAndSetSemantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.onSurface else RidgelineTheme.colors.borderStrong,
                        CircleShape,
                    ),
            )
        }
    }
}

private val DOT_SIZE = 6.dp
private val DOT_GAP = 6.dp
private val PHOTO_TARGET = 360.dp

/** High-five, comment and share, evenly spaced, with no hairline above. */
@Composable
private fun ActionRow(onHighFive: () -> Unit, onComment: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(ACTION_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple(Icons.Outlined.PanTool, "High-five", onHighFive),
            Triple(Icons.AutoMirrored.Outlined.Comment, "Comment", onComment),
            Triple(Icons.Outlined.Share, "Share", onShare),
        ).forEach { (icon, label, onClick) ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onClick) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private val STAT_SPACING = 34.dp
private val ACTION_ROW_HEIGHT = 48.dp
private const val MAP_ASPECT = 390f / 236f

@Composable
private fun CardHeader(card: ActivityCardUi, actions: ActivityCardActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(card.athleteName, card.athletePhotoPath, size = 40.dp)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.athleteName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (card.isNew) {
                    Spacer(Modifier.width(Spacing.s))
                    Badge("New")
                }
            }
            Spacer(Modifier.height(2.dp))
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
        Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options", tint = RidgelineTheme.colors.textSecondary)
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

/** "🏆 3 achievements, including a PR for 1K" with a gold trophy. */
@Composable
private fun AchievementsLine(achievements: Achievements) {
    val noun = if (achievements.count == 1) "achievement" else "achievements"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = RidgelineTheme.colors.medalGold,
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${achievements.count} $noun") }
                append(", including a PR for ${achievements.highlight}")
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HighFiveCount(count: Int) {
    val text = when (count) {
        0 -> "No high-fives yet"
        1 -> "1 gave a high-five"
        else -> "$count gave high-fives"
    }
    Row(
        Modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.PanTool, contentDescription = null, modifier = Modifier.size(16.dp), tint = RidgelineTheme.colors.textMuted)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
    }
}
