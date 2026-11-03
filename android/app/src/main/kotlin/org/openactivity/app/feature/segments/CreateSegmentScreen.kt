// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.StatValue
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/** "Create segment", connected to [CreateSegmentViewModel]. [onCreated] opens the new segment. */
@Composable
fun CreateSegmentScreen(
    onNavigateUp: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: CreateSegmentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CreateSegmentEffect.Created -> onCreated(effect.segmentId)
            }
        }
    }
    CreateSegmentContent(
        state = state,
        actions = CreateSegmentActions(
            onNavigateUp = onNavigateUp,
            onRange = viewModel::setRange,
            onName = viewModel::setName,
            onSport = viewModel::setSport,
            onSave = viewModel::save,
        ),
    )
}

/** Callbacks of [CreateSegmentContent]. */
data class CreateSegmentActions(
    val onNavigateUp: () -> Unit = {},
    val onRange: (Float, Float) -> Unit = { _, _ -> },
    val onName: (String) -> Unit = {},
    val onSport: (SportType) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Stateless create screen: the activity on a map with the chosen stretch in orange, the elevation profile
 * with two handles under it choosing the stretch, its distance, climb and grade, then a name and a sport.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSegmentContent(state: CreateSegmentUiState, actions: CreateSegmentActions, modifier: Modifier = Modifier) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RidgelineTopAppBar(title = "Create segment", onNavigateUp = actions.onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No GPS track",
                    body = "A segment is cut from an activity's GPS track, and this activity has none.",
                    primaryActionLabel = "Go back",
                    onPrimaryAction = actions.onNavigateUp,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Form(state, actions, Modifier.padding(top = padding.calculateTopPadding()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Form(state: CreateSegmentUiState, actions: CreateSegmentActions, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .widthIn(max = Spacing.feedMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SegmentMap(
            highlight = state.highlight,
            context = state.route,
            contentDescription = "Map of ${state.activityTitle} with the segment highlighted",
        )
        Text(
            "Drag the handles to the start and the finish of the stretch you want to race.",
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
        )
        SegmentProfile(
            profile = state.profile,
            description = "Elevation profile of the activity with the segment from ${percent(state.range.start)} to ${percent(state.range.endInclusive)} of the distance",
            range = state.range,
        )
        RangeSlider(
            value = state.range,
            onValueChange = { actions.onRange(it.start, it.endInclusive) },
            onValueChangeFinished = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            colors = SliderDefaults.colors(
                thumbColor = RidgelineTheme.colors.accent,
                activeTrackColor = RidgelineTheme.colors.accent,
                inactiveTrackColor = RidgelineTheme.colors.hairline,
            ),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Segment start and finish"
                stateDescription = "From ${percent(state.range.start)} to ${percent(state.range.endInclusive)} of the activity"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            state.stats.forEach { StatValue(it, valueStyle = RidgelineTheme.text.statM) }
        }
        if (state.tooShort) {
            Text(
                "A segment must be at least ${state.minLength} long. Move the handles apart.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.onName,
            label = { Text("Name") },
            placeholder = { Text("e.g. Riverside sprint") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.sportOptions.size > 1) {
            Text("Sport", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                state.sportOptions.forEach { sport ->
                    RidgelineFilterChip(sport.label, selected = sport == state.sport, onClick = { actions.onSport(sport) }, leadingIcon = sport.icon)
                }
            }
            Text(
                "Activities of the same kind match it too: every run, or every ride.",
                style = RidgelineTheme.text.caption,
                color = RidgelineTheme.colors.textMuted,
            )
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Spacing.s))
        PrimaryButton(
            text = "Save segment",
            onClick = actions.onSave,
            enabled = state.canSave,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Your segments stay on this phone. Every activity you have saved is checked against it in the background.",
            style = RidgelineTheme.text.caption,
            color = RidgelineTheme.colors.textMuted,
        )
    }
}

private fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"
