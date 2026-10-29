// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.feature.settings.RadioRow
import org.openactivity.app.feature.settings.SettingsGroup
import org.openactivity.app.feature.settings.SettingsUiState
import org.openactivity.app.feature.settings.visibilityLabel
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.label
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.util.Locale

/** First-run onboarding, connected to [OnboardingViewModel]. Calls [onFinished] once saved. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler(enabled = !state.isFirstStep) { viewModel.back() }
    OnboardingContent(
        state = state,
        actions = OnboardingActions(
            onName = viewModel::setName,
            onUnits = viewModel::setUnits,
            onSport = viewModel::setSport,
            onVisibility = viewModel::setVisibility,
            onHideRadius = viewModel::setHideRadius,
            onNext = viewModel::next,
            onBack = { viewModel.back() },
        ),
    )
}

/** Callbacks for [OnboardingContent]. */
data class OnboardingActions(
    val onName: (String) -> Unit = {},
    val onUnits: (UnitSystem) -> Unit = {},
    val onSport: (SportType) -> Unit = {},
    val onVisibility: (Visibility) -> Unit = {},
    val onHideRadius: (Double) -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Stateless onboarding: one question per step, a progress bar and Back / Next. */
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            ) {
                LinearProgressIndicator(
                    progress = { (state.step.ordinal + 1f) / OnboardingStep.entries.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = Spacing.xxl),
                ) {
                    when (state.step) {
                        OnboardingStep.Name -> NameStep(state, actions)
                        OnboardingStep.Units -> UnitsStep(state, actions)
                        OnboardingStep.Sport -> SportStep(state, actions)
                        OnboardingStep.Privacy -> PrivacyStep(state, actions)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.isFirstStep) RidgelineTextButton("Back", onClick = actions.onBack)
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(if (state.isLastStep) "Start" else "Next", onClick = actions.onNext, loading = state.isSaving)
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(Spacing.s))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun NameStep(state: OnboardingUiState, actions: OnboardingActions) {
    StepTitle(
        "Welcome",
        "Record runs, rides and more, free and open source. Everything stays on this phone unless you choose to share it. What should we call you?",
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = actions.onName,
        label = { Text("Your name (optional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { actions.onNext() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UnitsStep(state: OnboardingUiState, actions: OnboardingActions) {
    StepTitle("Units", "Used for distances, paces and elevation everywhere in the app. You can change this later in Settings.")
    SettingsGroup {
        UnitSystem.entries.forEach { units -> RadioRow(units.label, selected = state.units == units) { actions.onUnits(units) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SportStep(state: OnboardingUiState, actions: OnboardingActions) {
    StepTitle("Your main sport", "It is preselected when you start recording.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SettingsUiState.SPORTS.forEach { sport ->
            RidgelineFilterChip(sport.label, selected = sport == state.sport, onClick = { actions.onSport(sport) }, leadingIcon = sport.icon)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyStep(state: OnboardingUiState, actions: OnboardingActions) {
    val formatter = UnitFormatter(state.units, Locale.getDefault())
    StepTitle("Privacy", "Defaults for new activities. Nothing is shared until you sign in to a server.")
    Text("Who can see your activities", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(Spacing.s))
    SettingsGroup {
        Visibility.entries.forEach { v -> RadioRow(visibilityLabel(v), selected = state.visibility == v) { actions.onVisibility(v) } }
    }
    Spacer(Modifier.height(Spacing.xl))
    Text("Hide the start and end of your maps", style = MaterialTheme.typography.titleSmall)
    Text(
        "Keeps where you live and work off the map others see.",
        style = MaterialTheme.typography.bodySmall,
        color = RidgelineTheme.colors.textMuted,
    )
    Spacer(Modifier.height(Spacing.s))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SettingsUiState.HIDE_RADII.forEach { meters ->
            val label = if (meters == 0.0) "Off" else formatter.shortDistance(meters).let { "${it.value} ${it.unit}" }
            RidgelineFilterChip(label, selected = state.hideRadiusMeters == meters, onClick = { actions.onHideRadius(meters) })
        }
    }
}
