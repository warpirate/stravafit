// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.StatValue
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.share.ShareLauncher
import kotlin.math.abs

/** The weekly recap, connected to [RecapViewModel]. */
@Composable
fun RecapScreen(
    onNavigateUp: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val launcher = remember(context) { ShareLauncher(context.applicationContext) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RecapEffect.Share -> if (!launcher.share(context, effect.file)) messenger.show("No app on this phone can take an image.")
                is RecapEffect.Message -> messenger.show(effect.text)
            }
        }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE
    RecapContent(state, RecapActions(onNavigateUp, onOpenActivity, onShare = { viewModel.share(dark) }))
}

/** Callbacks for [RecapContent]. */
data class RecapActions(
    val onNavigateUp: () -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
    val onShare: () -> Unit = {},
)

/**
 * Stateless weekly recap: the week's totals, each against the week before, the longest activity and the
 * weekly goals, with Share at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapContent(state: RecapUiState, actions: RecapActions, modifier: Modifier = Modifier) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = RidgelineTheme.colors.gap,
        topBar = { RidgelineTopAppBar(title = "Weekly recap", onNavigateUp = actions.onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = Spacing.xl),
            ) {
                when {
                    state.isLoading -> ActivityCardSkeleton(Modifier.padding(Spacing.gutter))
                    state.isEmpty -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.Insights,
                            title = "No activities that week",
                            body = "${state.title}. Recaps sum up the weeks you recorded something.",
                        )
                    }
                    else -> RecapBody(state, actions)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RecapBody(state: RecapUiState, actions: RecapActions) {
    Section {
        Text("Your week", style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
        Text(state.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            state.stats.filter { it.label != "Activities" }.take(3).forEach { StatValue(it, valueStyle = RidgelineTheme.text.statL) }
        }
        if (state.note.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.m))
            Text(state.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Section {
        SectionTitle("Against the week before")
        state.comparisons.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = RidgelineTheme.colors.hairline)
            ComparisonRow(row)
        }
    }
    state.longest?.let { longest ->
        Section(padded = false) {
            Box(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.l)) { SectionTitle("Longest activity") }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open activity") { actions.onOpenActivity(longest.id) }
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(SportBadge).background(RidgelineTheme.colors.accentArea, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(longest.sport.icon, contentDescription = null, tint = RidgelineTheme.colors.accentText) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(longest.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("${longest.day} · ${longest.size}", style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
            }
        }
    }
    if (state.goals.isNotEmpty()) {
        Section {
            SectionTitle("Weekly goals")
            state.goals.forEach { goal ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.s).clearAndSetSemantics {
                        contentDescription = "${goal.title}, ${goal.result}, ${if (goal.reached) "reached" else "not reached"}"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (goal.reached) Icons.Rounded.CheckCircle else Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = if (goal.reached) RidgelineTheme.colors.success else RidgelineTheme.colors.textMuted,
                    )
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(goal.title, style = MaterialTheme.typography.titleSmall)
                        Text(goal.result, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
                    }
                    Text(
                        if (goal.reached) "Reached" else "Missed",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (goal.reached) RidgelineTheme.colors.success else RidgelineTheme.colors.textMuted,
                    )
                }
            }
        }
    }
    PrimaryButton(
        text = "Share recap",
        onClick = actions.onShare,
        loading = state.isSharing,
        enabled = state.card != null,
        leadingIcon = Icons.Rounded.IosShare,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.l),
    )
}

@Composable
private fun ComparisonRow(row: RecapComparison) {
    val change = row.changePercent
    val description = buildString {
        append("${row.label}, ${row.now}")
        when {
            change == null -> append(", nothing the week before")
            change > 0 -> append(", up $change percent on ${row.before}")
            change < 0 -> append(", down ${-change} percent on ${row.before}")
            else -> append(", same as the week before")
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.m).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(row.now, style = MaterialTheme.typography.titleSmall)
            Text("Week before ${row.before}", style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
        }
        Spacer(Modifier.width(Spacing.m))
        ChangeChip(change)
    }
}

/** "▲ 12%" in green, "▼ 8%" muted (less is not bad), "New" when there is nothing to compare with. */
@Composable
private fun ChangeChip(change: Int?) {
    val colors = RidgelineTheme.colors
    Row(Modifier.width(ChangeWidth), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        when {
            change == null -> Text("New", style = MaterialTheme.typography.labelMedium, color = colors.accentText)
            change == 0 -> Text("Same", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            else -> {
                Icon(
                    if (change > 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = if (change > 0) colors.success else colors.textMuted,
                    modifier = Modifier.size(ChangeIcon),
                )
                Text("${abs(change)}%", style = MaterialTheme.typography.labelMedium, color = if (change > 0) colors.success else colors.textMuted)
            }
        }
    }
}

@Composable
private fun Section(padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (padded) Modifier.padding(Spacing.gutter) else Modifier),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
}

private val ChangeWidth = 56.dp
private val ChangeIcon = 16.dp
private val SportBadge = 40.dp
private const val DARK_LUMINANCE = 0.5f
