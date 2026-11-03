// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.SuggestionSettings
import org.openactivity.app.data.backup.AutoBackupFrequency
import org.openactivity.app.data.backup.AutoBackupSettings
import org.openactivity.app.feature.settings.ActionRow
import org.openactivity.app.feature.settings.RowDivider
import org.openactivity.app.feature.settings.SettingsGroup
import org.openactivity.app.feature.settings.visibilityLabel
import org.openactivity.core.data.repository.PrivacyZoneRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.Visibility
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * What the Privacy page says about this phone's setup.
 *
 * @property hideStartMeters what new activities hide at the start in anything shared (0 when off).
 * @property autoBackup "Weekly to Backups", or null while automatic backup is off.
 */
data class PrivacyUiState(
    val defaultVisibility: Visibility = Visibility.DEFAULT,
    val sportOverrides: Int = 0,
    val zoneCount: Int = 0,
    val hideStartMeters: Double = 0.0,
    val hideEndMeters: Double = 0.0,
    val routeSuggestions: Boolean = true,
    val autoBackup: String? = null,
)

/** Reads the settings the Privacy page explains. */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    settings: SettingsRepository,
    zones: PrivacyZoneRepository,
    suggestions: SuggestionSettings,
    autoBackup: AutoBackupSettings,
) : ViewModel() {
    val uiState: StateFlow<PrivacyUiState> = combine(settings.settings, zones.observeZones(), suggestions.state, autoBackup.state) { s, z, sug, backup ->
        PrivacyUiState(
            defaultVisibility = s.defaultVisibility,
            sportOverrides = s.visibilityOverrides.size,
            zoneCount = z.size,
            hideStartMeters = s.privacy.hideStartMeters,
            hideEndMeters = s.privacy.hideEndMeters,
            routeSuggestions = sug.enabled,
            autoBackup = backup.config.takeIf { it.enabled && it.folderUri != null }?.let { c ->
                val every = if (c.frequency == AutoBackupFrequency.DAILY) "Daily" else "Weekly"
                "$every to ${c.folderName ?: "your chosen folder"}"
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), PrivacyUiState())
}

/** The Privacy page, connected to [PrivacyViewModel]. */
@Composable
fun PrivacyScreen(
    onNavigateUp: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenVisibility: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PrivacyContent(state, onNavigateUp, onOpenZones, onOpenVisibility, onOpenSettings)
}

/** Stateless Privacy page: where data lives, what goes online, who sees what, and backups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyContent(
    state: PrivacyUiState,
    onNavigateUp: () -> Unit = {},
    onOpenZones: () -> Unit = {},
    onOpenVisibility: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RidgelineTopAppBar(title = "Privacy", onNavigateUp = onNavigateUp) },
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
                OverlineHeader("Where your data is")
                SettingsGroup {
                    Fact(
                        Icons.Rounded.PhoneAndroid,
                        "Everything stays on this phone",
                        "Activities, routes, photos, goals, your profile and settings are stored only here. Nothing is " +
                            "uploaded today. There is no account, no ads and no tracking.",
                    )
                    RowDivider()
                    Fact(
                        Icons.Rounded.Cloud,
                        "Signing in will be optional",
                        "A StravaFit server is coming. Signing in will be up to you, and so will syncing: it stays off until " +
                            "you turn it on. Without it, the app keeps working fully on the phone.",
                    )
                    RowDivider()
                    Fact(
                        Icons.Rounded.Public,
                        "What does go online",
                        buildString {
                            append("Map tiles for the area you look at come from OpenFreeMap and OpenStreetMap. ")
                            append(
                                if (state.routeSuggestions) {
                                    "Route suggestions are on: your position is sent to the OpenStreetMap routing service and Open-Meteo to plan loops."
                                } else {
                                    "Route suggestions are off, so your position is never sent anywhere."
                                },
                            )
                        },
                    )
                }

                OverlineHeader("Who sees your activities")
                SettingsGroup {
                    Fact(
                        Icons.Rounded.Group,
                        "Today, only you",
                        "Until you sign in and sync, no one else can see any activity. Each one still keeps the audience it " +
                            "will have then.",
                    )
                    RowDivider()
                    ActionRow(
                        "New activities: ${visibilityLabel(state.defaultVisibility)}",
                        when (state.sportOverrides) {
                            0 -> "The same for every sport. Change it here or for one activity when you save it."
                            1 -> "1 sport has its own audience. You can still change it when you save an activity."
                            else -> "${state.sportOverrides} sports have their own audience. You can still change it when you save an activity."
                        },
                        onOpenVisibility,
                        Icons.Rounded.Tune,
                    )
                    RowDivider()
                    ActionRow(
                        "Privacy zones",
                        when (state.zoneCount) {
                            0 -> "None yet. A zone around your home trims every route that starts or ends there."
                            1 -> "1 zone. Routes that start or end inside it are trimmed in anything you share."
                            else -> "${state.zoneCount} zones. Routes that start or end inside one are trimmed in anything you share."
                        },
                        onOpenZones,
                        Icons.Rounded.ShieldMoon,
                    )
                    RowDivider()
                    ActionRow(
                        "Hide start and end",
                        if (state.hideStartMeters <= 0.0 && state.hideEndMeters <= 0.0) {
                            "Off for new activities. You can switch it on for one activity when you save or edit it."
                        } else {
                            "New activities hide the first ${state.hideStartMeters.roundToInt()} m and last ${state.hideEndMeters.roundToInt()} m. " +
                                "You can switch it off for one activity when you save or edit it."
                        },
                        onOpenSettings,
                        Icons.Rounded.VisibilityOff,
                    )
                    RowDivider()
                    Fact(
                        Icons.Rounded.IosShare,
                        "Images, stickers and files you share",
                        "These leave the phone, so the hidden start, end and zones are cut out of them, unless you turn " +
                            "that off on the share screen. Stats stay those of the whole activity. You always see the full route.",
                    )
                }

                OverlineHeader("Backups")
                SettingsGroup {
                    ActionRow(
                        state.autoBackup?.let { "Automatic backup: $it" } ?: "Automatic backup is off",
                        "A backup holds everything, including full routes and your privacy zones. Keep it in a folder " +
                            "only you can open.",
                        onOpenSettings,
                        Icons.Rounded.Backup,
                    )
                }
            }
        }
    }
}

/** A read-only explanation with an icon. */
@Composable
private fun Fact(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = RidgelineTheme.colors.textSecondary, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column(Modifier.padding(start = Spacing.l)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
        }
    }
}
