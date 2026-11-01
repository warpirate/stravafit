// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.goals

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.label
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/** Goals screen, connected to [GoalsViewModel]. */
@Composable
fun GoalsScreen(
    onNavigateUp: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    GoalsContent(
        state = state,
        actions = GoalsActions(
            onNavigateUp = onNavigateUp,
            onAddGoal = viewModel::addGoal,
            onEditGoal = viewModel::editGoal,
            onDismissEditor = viewModel::dismissEditor,
            onPeriod = viewModel::setPeriod,
            onMetric = viewModel::setMetric,
            onSport = viewModel::setSport,
            onTarget = viewModel::setTarget,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        ),
    )
}

/** Callbacks for [GoalsContent]. */
data class GoalsActions(
    val onNavigateUp: () -> Unit = {},
    val onAddGoal: () -> Unit = {},
    val onEditGoal: (Long) -> Unit = {},
    val onDismissEditor: () -> Unit = {},
    val onPeriod: (GoalPeriod) -> Unit = {},
    val onMetric: (GoalMetric) -> Unit = {},
    val onSport: (SportType?) -> Unit = {},
    val onTarget: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Stateless goals screen: the weekly streak, every goal with its progress, and the editor sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsContent(state: GoalsUiState, actions: GoalsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(title = "Goals", onNavigateUp = actions.onNavigateUp) {
                if (!state.isLoading) TopBarAction(Icons.Rounded.Add, "Add goal", actions.onAddGoal)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.isLoading) {
                ActivityCardSkeleton(Modifier.padding(Spacing.gutter))
                return@Box
            }
            LazyColumn(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                state.streak?.let { streak ->
                    item(key = "streak") {
                        Column {
                            OverlineHeader("Weekly streak")
                            RidgelineCard(contentPadding = Spacing.none) { StreakRow(streak) }
                        }
                    }
                }
                if (state.goals.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Rounded.Flag,
                                title = "No goals yet",
                                body = "Set a weekly, monthly or yearly target for distance, time, climbing or activities, and track it here.",
                                primaryActionLabel = "Add a goal",
                                onPrimaryAction = actions.onAddGoal,
                            )
                        }
                    }
                } else {
                    item(key = "header") { OverlineHeader("Your goals") }
                    items(state.goals, key = { it.id }) { goal ->
                        RidgelineCard(contentPadding = Spacing.none) {
                            GoalCardContent(goal, onClick = { actions.onEditGoal(goal.id) })
                        }
                    }
                }
            }
        }
    }
    state.editor?.let { GoalEditorSheet(it, actions) }
}

/** New / edit goal: period, measure, sport and target, with Save and (when editing) Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(editor: GoalEditorUi, actions: GoalsActions) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = actions.onDismissEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Spacing.l),
        ) {
            Text(
                if (editor.isNew) "New goal" else "Edit goal",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            EditorLabel("Period")
            ChipRow {
                GoalPeriod.entries.forEach { period ->
                    RidgelineFilterChip(GoalFormat.periodAdjective(period), selected = period == editor.period, onClick = { actions.onPeriod(period) })
                }
            }
            EditorLabel("Measure")
            ChipRow {
                editor.metrics.forEach { metric ->
                    RidgelineFilterChip(GoalFormat.metricLabel(metric), selected = metric == editor.metric, onClick = { actions.onMetric(metric) })
                }
            }
            if (editor.period != GoalPeriod.WEEK) {
                Text(
                    "Activity count goals are weekly only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }
            EditorLabel("Sport")
            ChipRow {
                RidgelineFilterChip("All sports", selected = editor.sport == null, onClick = { actions.onSport(null) })
                editor.sports.forEach { sport ->
                    RidgelineFilterChip(sport.label, selected = sport == editor.sport, onClick = { actions.onSport(sport) })
                }
            }
            if (editor.sport == SportType.RUN || editor.sport == SportType.RIDE) {
                Text(
                    if (editor.sport == SportType.RUN) "Counts every kind of run." else "Counts every kind of ride.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }
            EditorLabel("Target ${GoalFormat.periodPhrase(editor.period)}")
            val error = editor.error
            OutlinedTextField(
                value = editor.targetText,
                onValueChange = actions.onTarget,
                singleLine = true,
                isError = error != null,
                suffix = { Text(editor.unit) },
                placeholder = { Text(placeholder(editor.metric)) },
                supportingText = if (error != null) {
                    { Text(error) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (editor.metric == GoalMetric.COUNT) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (editor.canSave) actions.onSave() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            )
            Spacer(Modifier.height(Spacing.l))
            PrimaryButton(
                if (editor.isNew) "Add goal" else "Save",
                onClick = actions.onSave,
                enabled = editor.canSave,
                loading = editor.isSaving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            )
            if (!editor.isNew) {
                Spacer(Modifier.height(Spacing.s))
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Spacing.s))
                    Text("Delete goal", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this goal?") },
            text = { Text("Your activities are kept; only the goal and its progress bar go.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = RidgelineTheme.colors.textSecondary,
        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.l, bottom = Spacing.xs),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) { content() }
}

private fun placeholder(metric: GoalMetric): String = when (metric) {
    GoalMetric.DISTANCE -> "30"
    GoalMetric.TIME -> "5"
    GoalMetric.ELEVATION -> "1000"
    GoalMetric.COUNT -> "4"
}
