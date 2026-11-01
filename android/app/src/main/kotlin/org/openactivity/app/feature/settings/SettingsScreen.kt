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
import org.openactivity.core.datastore.AutoLapChoice
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToLong
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
            onRouteSuggestions = viewModel::setRouteSuggestions,
            onDefaultSport = viewModel::setDefaultSport,
            onAutoPause = viewModel::setAutoPause,
            onAudioCues = viewModel::setAudioCuesEnabled,
            onCueInterval = viewModel::setCueInterval,
            onAnnounceAutoPause = viewModel::setAnnounceAutoPause,
            onAnnounceStatus = viewModel::setAnnounceStatus,
            onAnnounceSplits = viewModel::setAnnounceSplits,
            onAnnounceLaps = viewModel::setAnnounceLaps,
            onAnnounceRoute = viewModel::setAnnounceRoute,
            onDistanceCues = viewModel::setDistanceCues,
            onCueTimeInterval = viewModel::setCueTimeInterval,
            onAutoLap = viewModel::setAutoLap,
            onOffRouteAlerts = viewModel::setOffRouteAlerts,
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
    val onRouteSuggestions: (Boolean) -> Unit = {},
    val onDefaultSport: (SportType) -> Unit = {},
    val onAutoPause: (SportType, Boolean) -> Unit = { _, _ -> },
    val onAudioCues: (Boolean) -> Unit = {},
    val onCueInterval: (Double) -> Unit = {},
    val onAnnounceAutoPause: (Boolean) -> Unit = {},
    val onAnnounceStatus: (Boolean) -> Unit = {},
    val onAnnounceSplits: (Boolean) -> Unit = {},
    val onAnnounceLaps: (Boolean) -> Unit = {},
    val onAnnounceRoute: (Boolean) -> Unit = {},
    val onDistanceCues: (Boolean) -> Unit = {},
    val onCueTimeInterval: (Int) -> Unit = {},
    val onAutoLap: (Double) -> Unit = {},
    val onOffRouteAlerts: (Boolean) -> Unit = {},
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

                OverlineHeader("Routes")
                SettingsGroup {
                    SwitchRow(
                        "Online route suggestions",
                        "Suggests loops near you on Home and Maps. Your current position is sent to the OpenStreetMap " +
                            "routing service (routing.openstreetmap.de) and to Open-Meteo for elevation.",
                        state.routeSuggestions,
                        actions.onRouteSuggestions,
                    )
                }

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
        RowDivider()
        ChipChoiceRow(
            title = "Auto-lap",
            subtitle = "Close a lap every set distance",
            // A distance stored by an older choice stays selectable rather than silently showing Off.
            options = (SettingsUiState.AUTO_LAP_METERS + settings.autoLapMeters).distinct(),
            selected = settings.autoLapMeters,
            label = { autoLapLabel(it, settings.units) },
            onSelect = actions.onAutoLap,
        )
        RowDivider()
        SwitchRow(
            "Off-route alerts",
            "Vibrate and say when you leave a route you are following",
            settings.offRouteAlerts,
            actions.onOffRouteAlerts,
        )
    }

    OverlineHeader("Audio cues")
    SettingsGroup {
        val cues = settings.audioCues
        val on = cues.enabled
        SwitchRow("Spoken updates", "Read out progress while recording", on, actions.onAudioCues)
        RowDivider()
        SwitchRow("Splits", "Each $unit with your split pace", cues.announceSplits, actions.onAnnounceSplits, enabled = on)
        SwitchRow("Laps", "Each lap with its time and pace", cues.announceLaps, actions.onAnnounceLaps, enabled = on)
        SwitchRow("Start, pause and finish", "Say when the activity starts, pauses, resumes and saves", cues.announceStatus, actions.onAnnounceStatus, enabled = on)
        SwitchRow("Announce auto-pause", "Say when the timer stops and restarts", cues.announceAutoPause, actions.onAnnounceAutoPause, enabled = on)
        SwitchRow("Route", "Off route, back on route and route finished", cues.announceRoute, actions.onAnnounceRoute, enabled = on)
        RowDivider()
        SwitchRow("Custom distance", "Distance, time and pace at your own interval", cues.distanceCues, actions.onDistanceCues, enabled = on)
        if (cues.distanceCues) {
            ChipChoiceRow(
                title = "Announce every",
                subtitle = null,
                options = SettingsUiState.CUE_INTERVALS,
                selected = cues.distanceInterval,
                label = { "${NumberFormat.getNumberInstance().format(it)} $unit" },
                onSelect = actions.onCueInterval,
            )
        }
        RowDivider()
        ChipChoiceRow(
            title = "Time readout",
            subtitle = "Time, distance and average pace every few minutes",
            options = SettingsUiState.CUE_TIME_INTERVALS,
            selected = cues.timeIntervalMinutes,
            label = { if (it == 0) "Off" else "$it min" },
            onSelect = actions.onCueTimeInterval,
        )
    }
}

/** "Off", "400 m", "1 km", "1 mi": whole miles or kilometres by name, other distances in metres or miles. */
internal fun autoLapLabel(meters: Double, units: UnitSystem): String {
    if (meters <= 0.0) return "Off"
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    val km = meters / AutoLapChoice.KILOMETER_METERS
    val mi = meters / AutoLapChoice.MILE_METERS
    fun whole(x: Double) = abs(x - x.roundToLong()) < WHOLE_TOLERANCE
    return when {
        whole(km) -> "${format.format(km.roundToLong())} km"
        whole(mi) -> "${format.format(mi.roundToLong())} mi"
        units == UnitSystem.IMPERIAL -> "${format.format(mi)} mi"
        else -> "${format.format(meters)} m"
    }
}

private const val WHOLE_TOLERANCE = 1e-3

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
