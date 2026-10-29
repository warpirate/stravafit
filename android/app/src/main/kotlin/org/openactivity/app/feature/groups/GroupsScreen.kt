// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.data.Challenge
import org.openactivity.app.data.Club
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.notInThisBuild
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Groups tab, connected to [GroupsViewModel]. */
@Composable
fun GroupsScreen(viewModel: GroupsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    GroupsContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onAction = { messenger.show(notInThisBuild(it)) },
    )
}

/**
 * Stateless Groups placeholder (secondary-screens 3.1). [onAction] receives the name of the
 * feature an entry point leads to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsContent(
    state: GroupsUiState,
    onSelectTab: (GroupsTab) -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(title = "Groups") {
                TopBarAction(Icons.Rounded.Search, "Search clubs and challenges", onClick = { onAction("Club search") })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                item(key = "tabs") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        GroupsTab.entries.forEach { tab ->
                            RidgelineFilterChip(tab.label, selected = tab == state.tab, onClick = { onSelectTab(tab) })
                        }
                    }
                }
                when (state.tab) {
                    GroupsTab.Clubs -> clubItems(state, onAction)
                    GroupsTab.Challenges -> challengeItems(state, onAction)
                }
            }
        }
    }
}

private fun LazyListScope.clubItems(state: GroupsUiState, onAction: (String) -> Unit) {
    item(key = "mine") { SectionHeader("Your clubs", actionLabel = "See all", onAction = { onAction("Club list") }) }
    item(key = "mine-card") {
        RidgelineCard(contentPadding = Spacing.none) {
            state.myClubs.forEach { club -> ClubRow(club, trailing = null, onClick = { onAction("Club pages") }) }
        }
    }
    item(key = "suggested") { SectionHeader("Suggested clubs") }
    items(state.suggestedClubs, key = { it.id }) { club ->
        RidgelineCard(contentPadding = Spacing.none) {
            ClubRow(club, trailing = { SecondaryButton("Join", onClick = { onAction("Joining clubs") }) }, onClick = { onAction("Club pages") })
        }
    }
    item(key = "create") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RidgelineTextButton("Create a club", onClick = { onAction("Club creation") }, leadingIcon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun ClubRow(club: Club, trailing: (@Composable () -> Unit)?, onClick: () -> Unit) {
    ClickableRow(onClick) {
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(club.sport.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(club.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (club.hasNewPosts) {
                    Spacer(Modifier.width(Spacing.s))
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, PillShape))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("New posts", style = RidgelineTheme.text.caption, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "%,d members · %s".format(club.memberCount, club.city),
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textSecondary,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ClickableRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

private fun LazyListScope.challengeItems(state: GroupsUiState, onAction: (String) -> Unit) {
    item(key = "challenges") { SectionHeader("Challenges") }
    items(state.challenges, key = { it.id }) { challenge -> ChallengeCard(challenge, onAction) }
}

@Composable
private fun ChallengeCard(challenge: Challenge, onAction: (String) -> Unit) {
    RidgelineCard(onClick = { onAction("Challenge details") }) {
        Text(challenge.title, style = MaterialTheme.typography.titleMedium)
        Text(challenge.goal, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.m))
        val progress = challenge.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(PillShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = RidgelineTheme.colors.hairline,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                "${(progress * PERCENT).toInt()}% done · ${challenge.daysLeft} days left",
                style = RidgelineTheme.text.caption,
                color = RidgelineTheme.colors.textMuted,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%,d taking part · %d days left".format(challenge.participants, challenge.daysLeft),
                    style = RidgelineTheme.text.caption,
                    color = RidgelineTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton("Join", onClick = { onAction("Joining challenges") })
            }
        }
    }
}

private const val PERCENT = 100
