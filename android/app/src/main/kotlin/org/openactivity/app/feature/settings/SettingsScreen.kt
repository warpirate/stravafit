// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UnitSystem
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Settings screen, connected to [SettingsViewModel]. */
@Composable
fun SettingsScreen(onNavigateUp: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onNavigateUp = onNavigateUp,
        onUnitSystem = viewModel::setUnitSystem,
        onThemeMode = viewModel::setThemeMode,
        onReduceMotion = viewModel::setReduceMotion,
    )
}

/** Stateless settings: preferences, feed order and build information. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onNavigateUp: () -> Unit,
    onUnitSystem: (UnitSystem) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { RidgelineTopAppBar(title = "Settings", onNavigateUp = onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            ) {
                OverlineHeader("Units of measure")
                SettingsGroup {
                    UnitSystem.entries.forEachIndexed { index, units ->
                        if (index > 0) RowDivider()
                        RadioRow(units.label, selected = state.preferences.unitSystem == units) { onUnitSystem(units) }
                    }
                }
                OverlineHeader("Appearance")
                SettingsGroup {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) RowDivider()
                        RadioRow(mode.label, selected = state.preferences.themeMode == mode) { onThemeMode(mode) }
                    }
                    RowDivider()
                    SwitchRow(
                        title = "Reduce motion",
                        subtitle = "Turns off celebrations, shimmer and transitions",
                        checked = state.preferences.reduceMotion,
                        onCheckedChange = onReduceMotion,
                    )
                }
                OverlineHeader("Feed")
                SettingsGroup {
                    InfoRow("Feed order", "Latest first. Your feed is never ranked by an algorithm unless you opt in.")
                }
                OverlineHeader("About")
                SettingsGroup {
                    InfoRow("Version", "${state.versionName} · ${state.distribution.displayName}")
                    RowDivider()
                    InfoRow("Location", state.distribution.locationBackend)
                    RowDivider()
                    InfoRow("Licence", "Free and open source (AGPL-3.0). No ads, no subscriptions.")
                    RowDivider()
                    InfoRow("Fonts", "Manrope and Barlow Semi Condensed, SIL Open Font License 1.1")
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    RidgelineCard(contentPadding = Spacing.none) {
        Column(Modifier.selectableGroup(), content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
        }
        Spacer(Modifier.width(Spacing.m))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
    }
}
