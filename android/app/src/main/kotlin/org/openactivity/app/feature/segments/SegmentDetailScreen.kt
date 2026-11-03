// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.RankMedal
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.StatValue
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** A segment's detail, connected to [SegmentDetailViewModel]. */
@Composable
fun SegmentDetailScreen(
    onNavigateUp: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    viewModel: SegmentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    LaunchedEffect(state.deleted) { if (state.deleted) onNavigateUp() }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    SegmentDetailContent(
        state = state,
        actions = SegmentDetailActions(
            onNavigateUp = onNavigateUp,
            onToggleStar = viewModel::toggleStar,
            onRename = viewModel::rename,
            onRematch = viewModel::rematch,
            onDelete = viewModel::delete,
            onOpenActivity = onOpenActivity,
        ),
    )
}

/** Callbacks of [SegmentDetailContent]. */
data class SegmentDetailActions(
    val onNavigateUp: () -> Unit = {},
    val onToggleStar: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onRematch: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
)

/**
 * Stateless segment detail (strava direction): the segment on a map, its name, length and grade, the
 * stats row and elevation profile, your PR, a chart of your times over the dates, your top three and every
 * effort. Star in the top bar; rename, re-match and delete in the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentDetailContent(state: SegmentDetailUiState, actions: SegmentDetailActions, modifier: Modifier = Modifier) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    FrostedScaffold(
        modifier = modifier,
        containerColor = RidgelineTheme.colors.gap,
        topBar = {
            RidgelineTopAppBar(title = state.name.ifEmpty { "Segment" }, onNavigateUp = actions.onNavigateUp) {
                if (!state.isLoading && !state.notFound) {
                    TopBarAction(
                        if (state.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        if (state.starred) "Unstar segment" else "Star segment",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            actions.onToggleStar()
                        },
                    )
                    Menu(onRename = { renaming = true }, onRematch = actions.onRematch, onDelete = { confirmDelete = true })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> Unit
                state.notFound -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Segment not found",
                    body = "It may have been deleted.",
                    primaryActionLabel = "Go back",
                    onPrimaryAction = actions.onNavigateUp,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Body(state, actions, PaddingValues(top = padding.calculateTopPadding()))
            }
        }
    }
    if (renaming) RenameDialog(state.name, onDone = { renaming = false; actions.onRename(it) }, onDismiss = { renaming = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete segment?") },
            text = { Text("${state.name} and its ${state.recent.size} efforts are removed from this phone. Your activities stay.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Body(state: SegmentDetailUiState, actions: SegmentDetailActions, padding: PaddingValues) {
    LazyColumn(
        Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = Spacing.xxl),
    ) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.gutter)) {
                SegmentMap(state.points, contentDescription = "Map of the segment ${state.name}")
                Spacer(Modifier.height(Spacing.m))
                Text(state.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(
                    "${state.sport.label} · ${state.subtitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Spacing.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    state.stats.forEach { StatValue(it, valueStyle = RidgelineTheme.text.statM) }
                }
                if (state.profile.hasElevation) {
                    Spacer(Modifier.height(Spacing.l))
                    SegmentProfile(state.profile, description = "Elevation profile, low ${state.profile.minLabel}, high ${state.profile.maxLabel}")
                }
            }
        }
        item(key = "pr") {
            Section("Your results") {
                if (state.prTime == null) {
                    Text(
                        "No efforts yet. Every activity you save is checked against this segment, and older ones are being checked now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RidgelineTheme.colors.textSecondary,
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Open the activity") { state.prActivityId?.let(actions.onOpenActivity) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RankMedal(1, size = 32.dp, description = "Personal record")
                        Spacer(Modifier.width(Spacing.m))
                        Column(Modifier.weight(1f)) {
                            Text("Personal record", style = MaterialTheme.typography.titleSmall)
                            state.prDate?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary) }
                        }
                        Text(state.prTime, style = RidgelineTheme.text.statL)
                    }
                }
            }
        }
        if (state.chart.size >= 2) {
            item(key = "chart") {
                Section("Your times") { TimesChart(state.chart) }
            }
        }
        if (state.best.isNotEmpty()) {
            item(key = "best") {
                Section("Your top results") {
                    state.best.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = RidgelineTheme.colors.hairline)
                        EffortLine(row, place = i + 1, onClick = { actions.onOpenActivity(row.activityId) })
                    }
                }
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent-title") {
                Section("All efforts · ${state.recent.size}") {}
            }
            items(state.recent, key = { it.id }) { row ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = Spacing.gutter)) {
                    EffortLine(row, place = null, onClick = { actions.onOpenActivity(row.activityId) })
                    HorizontalDivider(color = RidgelineTheme.colors.hairline)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.s))
        content()
    }
}

/** One effort: medal (or the place among your top three), date and activity, pace, PR label and time. */
@Composable
internal fun EffortLine(row: EffortHistoryRow, place: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Open the activity", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(row.date, row.activityTitle, row.time, row.medalLabel, row.pace).joinToString(", ")
                role = Role.Button
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            val rank = row.rank
            when {
                rank != null -> RankMedal(rank, yearly = row.yearly)
                place != null -> Text("$place", style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
                else -> Icon(Icons.Rounded.Timeline, contentDescription = null, tint = RidgelineTheme.colors.textMuted, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
            Text(row.date, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(row.activityTitle, row.pace, row.heartRate).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.time, style = RidgelineTheme.text.statS)
            row.medalLabel?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = if (row.isPersonalRecord) RidgelineTheme.colors.signalText else RidgelineTheme.colors.textSecondary)
            }
        }
    }
}

/** Your times over the dates: a line through every effort, faster is higher, the PR as a gold dot. */
@Composable
private fun TimesChart(points: List<EffortPoint>) {
    val accent = RidgelineTheme.colors.accent
    val gold = RidgelineTheme.colors.medalGold
    val grid = RidgelineTheme.colors.hairline
    val fastest = points.minOf { it.seconds }
    val slowest = points.maxOf { it.seconds }
    Column(Modifier.clearAndSetSemantics {
        contentDescription = "Chart of ${points.size} efforts, fastest ${SegmentFormat.clock(fastest)}, slowest ${SegmentFormat.clock(slowest)}"
    }) {
        Row(Modifier.fillMaxWidth()) {
            Text("Fastest ${SegmentFormat.clock(fastest)}", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted, modifier = Modifier.weight(1f))
            Text("Slowest ${SegmentFormat.clock(slowest)}", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
        }
        Spacer(Modifier.height(Spacing.xs))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val firstDay = points.first().epochDay
            val days = (points.last().epochDay - firstDay).coerceAtLeast(1)
            val span = (slowest - fastest).coerceAtLeast(1)
            val r = 4.dp.toPx()
            fun at(p: EffortPoint): Offset {
                val x = if (points.last().epochDay == firstDay) {
                    points.indexOf(p).toFloat() / (points.size - 1) * (size.width - 2 * r) + r
                } else {
                    r + (p.epochDay - firstDay).toFloat() / days * (size.width - 2 * r)
                }
                // Faster is higher.
                val y = r + (p.seconds - fastest).toFloat() / span * (size.height - 2 * r)
                return Offset(x, y)
            }
            drawLine(grid, Offset(0f, r), Offset(size.width, r), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, size.height - r), Offset(size.width, size.height - r), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                points.forEachIndexed { i, p -> at(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            }
            drawPath(path, accent, style = Stroke(2.dp.toPx()))
            points.forEach { p -> drawCircle(if (p.isBest) gold else accent, radius = if (p.isBest) r * 1.5f else r, center = at(p)) }
        }
    }
}

@Composable
private fun Menu(onRename: () -> Unit, onRematch: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text("Re-match all activities") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { open = false; onRematch() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename segment") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
