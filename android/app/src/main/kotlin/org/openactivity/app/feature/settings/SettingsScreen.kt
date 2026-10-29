// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.label
import org.openactivity.app.model.toThemeMode
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.NO_FILE_PICKER_MESSAGE
import org.openactivity.app.ui.common.label
import org.openactivity.app.ui.common.launchOrElse
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.text.NumberFormat
import java.util.Locale

/** Settings screen, connected to [SettingsViewModel]. */
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenLicenses: () -> Unit,
    onImportActivities: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val uriHandler = LocalUriHandler.current
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportAll)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    SettingsContent(
        state = state,
        actions = SettingsActions(
            onNavigateUp = onNavigateUp,
            onEditProfile = onEditProfile,
            onUnits = viewModel::setUnits,
            onTheme = viewModel::setTheme,
            onReduceMotion = viewModel::setReduceMotion,
            onDefaultSport = viewModel::setDefaultSport,
            onAutoPause = viewModel::setAutoPause,
            onAudioCues = viewModel::setAudioCuesEnabled,
            onCueInterval = viewModel::setCueInterval,
            onAnnounceAutoPause = viewModel::setAnnounceAutoPause,
            onVisibility = viewModel::setDefaultVisibility,
            onHideStart = viewModel::setHideStart,
            onHideEnd = viewModel::setHideEnd,
            onExportAll = { createZip.launchOrElse(state.exportFileName) { messenger.show(NO_FILE_PICKER_MESSAGE) } },
            onOpenLicenses = onOpenLicenses,
            onImportActivities = onImportActivities,
            onOpenUrl = { runCatching { uriHandler.openUri(it) } },
        ),
    )
}

/** Callbacks for [SettingsContent]. */
data class SettingsActions(
    val onNavigateUp: () -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onUnits: (UnitSystem) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onDefaultSport: (SportType) -> Unit = {},
    val onAutoPause: (SportType, Boolean) -> Unit = { _, _ -> },
    val onAudioCues: (Boolean) -> Unit = {},
    val onCueInterval: (Double) -> Unit = {},
    val onAnnounceAutoPause: (Boolean) -> Unit = {},
    val onVisibility: (Visibility) -> Unit = {},
    val onHideStart: (Double) -> Unit = {},
    val onHideEnd: (Double) -> Unit = {},
    val onExportAll: () -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onImportActivities: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
)

/** Stateless settings: profile, units, appearance, recording, audio cues, privacy, your data, about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val settings = state.settings
    val formatter = UnitFormatter(settings.units, Locale.getDefault())
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { RidgelineTopAppBar(title = "Settings", onNavigateUp = actions.onNavigateUp) },
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
                OverlineHeader("Profile")
                SettingsGroup {
                    ActionRow("Edit profile", "Name, location and photo, stored on this device", actions.onEditProfile, Icons.Rounded.Person)
                }

                OverlineHeader("Units of measure")
                SettingsGroup {
                    UnitSystem.entries.forEachIndexed { index, units ->
                        if (index > 0) RowDivider()
                        RadioRow(units.label, selected = settings.units == units) { actions.onUnits(units) }
                    }
                }

                OverlineHeader("Appearance")
                SettingsGroup {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) RowDivider()
                        RadioRow(mode.label, selected = settings.theme.toThemeMode() == mode) { actions.onTheme(mode) }
                    }
                    RowDivider()
                    SwitchRow("Reduce motion", "Turns off celebrations, shimmer and transitions", state.reduceMotion, actions.onReduceMotion)
                }

                RecordingSettings(state, actions)
                PrivacySettingsSection(state, actions, formatter)

                OverlineHeader("Your data")
                SettingsGroup {
                    ActionRow(
                        "Import activity file",
                        "GPX, TCX or FIT from a watch, bike computer or another app",
                        actions.onImportActivities,
                        Icons.Rounded.UploadFile,
                    )
                    RowDivider()
                    ActionRow(
                        "Export all activities",
                        "A ZIP with one GPX file per activity, written straight to a folder you choose",
                        actions.onExportAll,
                        Icons.Rounded.Archive,
                        busy = state.isExporting,
                    )
                }

                OverlineHeader("About")
                SettingsGroup {
                    InfoRow("Version", "${state.versionName} · ${state.distribution.displayName}")
                    RowDivider()
                    InfoRow("Location", state.distribution.locationBackend)
                    RowDivider()
                    InfoRow("Licence", "Free and open source (AGPL-3.0). No ads, no subscriptions, no tracking.")
                    RowDivider()
                    ActionRow(
                        "Map data",
                        "© OpenStreetMap contributors (ODbL). Map tiles by OpenFreeMap.",
                        { actions.onOpenUrl(OSM_COPYRIGHT_URL) },
                        Icons.Rounded.Map,
                    )
                    RowDivider()
                    ActionRow("Open-source licences", "Libraries, fonts and data this app is built on", actions.onOpenLicenses, Icons.Rounded.Description)
                }
            }
        }
    }
}

@Composable
private fun RecordingSettings(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    val unit = if (settings.units == UnitSystem.METRIC) "km" else "mi"
    OverlineHeader("Recording")
    SettingsGroup {
        ChipChoiceRow(
            title = "Default sport",
            subtitle = "Preselected on the Record screen",
            options = SettingsUiState.SPORTS,
            selected = settings.defaultSport,
            label = { it.label },
            onSelect = actions.onDefaultSport,
        )
        RowDivider()
        Text(
            "Auto-pause",
            style = MaterialTheme.typography.titleSmall,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
        )
        SettingsUiState.AUTO_PAUSE_SPORTS.forEach { sport ->
            SwitchRow(sport.label, null, settings.autoPauseEnabled(sport), { actions.onAutoPause(sport, it) })
        }
    }

    OverlineHeader("Audio cues")
    SettingsGroup {
        val cues = settings.audioCues
        SwitchRow("Spoken updates", "Distance, time and pace read out while recording", cues.enabled, actions.onAudioCues)
        RowDivider()
        ChipChoiceRow(
            title = "Announce every",
            subtitle = null,
            options = SettingsUiState.CUE_INTERVALS,
            selected = cues.distanceInterval,
            label = { "${NumberFormat.getNumberInstance().format(it)} $unit" },
            onSelect = actions.onCueInterval,
        )
        RowDivider()
        SwitchRow("Announce auto-pause", "Say when the timer stops and restarts", cues.announceAutoPause, actions.onAnnounceAutoPause, enabled = cues.enabled)
    }
}

@Composable
private fun PrivacySettingsSection(state: SettingsUiState, actions: SettingsActions, formatter: UnitFormatter) {
    val settings = state.settings
    val radius: (Double) -> String = { if (it == 0.0) "Off" else formatter.shortDistance(it).let { m -> "${m.value} ${m.unit}" } }
    OverlineHeader("Privacy")
    SettingsGroup {
        Text(
            "Who can see new activities",
            style = MaterialTheme.typography.titleSmall,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
        )
        Visibility.entries.forEach { visibility ->
            RadioRow(visibilityLabel(visibility), selected = settings.defaultVisibility == visibility) { actions.onVisibility(visibility) }
        }
        RowDivider()
        ChipChoiceRow("Hide the start", "Hidden from others on maps of new activities", SettingsUiState.HIDE_RADII, settings.privacy.hideStartMeters, radius, actions.onHideStart)
        RowDivider()
        ChipChoiceRow("Hide the end", null, SettingsUiState.HIDE_RADII, settings.privacy.hideEndMeters, radius, actions.onHideEnd)
        Text(
            "Hiding part of the map does not hide the activity: anyone who can see it still sees its stats.",
            style = MaterialTheme.typography.bodySmall,
            color = RidgelineTheme.colors.textMuted,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m),
        )
    }
}

/** Readable visibility choice. */
fun visibilityLabel(visibility: Visibility): String = when (visibility) {
    Visibility.EVERYONE -> "Everyone"
    Visibility.FOLLOWERS -> "Followers"
    Visibility.ONLY_ME -> "Only you"
}

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
