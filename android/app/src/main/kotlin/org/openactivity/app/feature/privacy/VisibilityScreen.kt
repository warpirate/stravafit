// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.feature.settings.ChipChoiceRow
import org.openactivity.app.feature.settings.RadioRow
import org.openactivity.app.feature.settings.RowDivider
import org.openactivity.app.feature.settings.SettingsGroup
import org.openactivity.app.feature.settings.SettingsUiState
import org.openactivity.app.feature.settings.SettingsViewModel
import org.openactivity.app.feature.settings.visibilityLabel
import org.openactivity.app.ui.common.label
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType
import org.openactivity.model.Visibility

/** Who sees new activities, overall and per sport; connected to [SettingsViewModel]. */
@Composable
fun VisibilityScreen(onNavigateUp: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VisibilityContent(
        settings = state.settings,
        onNavigateUp = onNavigateUp,
        onDefault = viewModel::setDefaultVisibility,
        onOverride = viewModel::setVisibilityOverride,
    )
}

/** Stateless audience settings: the default for new activities, then an optional override per sport. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisibilityContent(
    settings: AppSettings,
    onNavigateUp: () -> Unit = {},
    onDefault: (Visibility) -> Unit = {},
    onOverride: (SportType, Visibility?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RidgelineTopAppBar(title = "Who can see activities", onNavigateUp = onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            ) {
                Text(
                    "This is the audience new activities start with. You can change it for each activity when you " +
                        "save it. Until you sign in and sync, only you can see anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = Spacing.s),
                )
                OverlineHeader("New activities")
                SettingsGroup {
                    Visibility.entries.forEach { visibility ->
                        RadioRow(visibilityLabel(visibility), selected = settings.defaultVisibility == visibility) { onDefault(visibility) }
                    }
                }
                OverlineHeader("By sport")
                SettingsGroup {
                    val default = visibilityLabel(settings.defaultVisibility)
                    SettingsUiState.SPORTS.forEachIndexed { index, sport ->
                        if (index > 0) RowDivider()
                        ChipChoiceRow(
                            title = sport.label,
                            subtitle = null,
                            options = OVERRIDE_CHOICES,
                            selected = settings.visibilityOverrides[sport],
                            label = { choice -> if (choice == null) "Default ($default)" else visibilityLabel(choice) },
                            onSelect = { choice -> onOverride(sport, choice) },
                        )
                    }
                }
            }
        }
    }
}

/** No override (follow the default), then each audience. */
private val OVERRIDE_CHOICES: List<Visibility?> = listOf(null) + Visibility.entries
