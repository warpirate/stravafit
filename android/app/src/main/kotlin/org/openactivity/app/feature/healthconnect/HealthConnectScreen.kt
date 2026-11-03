// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.healthconnect

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.healthconnect.AndroidHealthConnectGateway
import org.openactivity.core.healthconnect.HealthConnectAvailability
import org.openactivity.core.healthconnect.SyncDirection
import org.openactivity.core.healthconnect.SyncSwitchState
import java.text.DateFormat
import java.util.Date

/** Settings, Health Connect. */
@Composable
fun HealthConnectScreen(onNavigateUp: () -> Unit, onOpenPrivacy: () -> Unit, viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    var asking by remember { mutableStateOf<SyncDirection?>(null) }
    val permissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        asking?.let { viewModel.onPermissionsResult(it, granted) }
        asking = null
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HealthConnectEffect.RequestPermissions -> {
                    asking = effect.direction
                    try {
                        permissions.launch(effect.permissions)
                    } catch (e: ActivityNotFoundException) {
                        asking = null
                        messenger.show("Health Connect can't ask for permission on this phone. Open Health Connect to allow StravaFit.")
                    }
                }
            }
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    // Permissions are changed in Health Connect itself: look again whenever the screen comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    HealthConnectContent(
        state = state,
        actions = HealthConnectActions(
            onNavigateUp = onNavigateUp,
            onInstall = { if (!openStore(context)) messenger.show("No app store is available to install Health Connect.") },
            onOpenHealthConnect = { if (!openHealthConnect(context)) messenger.show("Health Connect could not be opened.") },
            onWrite = viewModel::setWriteEnabled,
            onRead = viewModel::setReadEnabled,
            onAllowAgain = viewModel::requestAgain,
            onSyncNow = viewModel::syncNow,
            onWritePast = viewModel::writePast,
            onOpenPrivacy = onOpenPrivacy,
        ),
    )
}

/** Callbacks of [HealthConnectContent]. */
data class HealthConnectActions(
    val onNavigateUp: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onOpenHealthConnect: () -> Unit = {},
    val onWrite: (Boolean) -> Unit = {},
    val onRead: (Boolean) -> Unit = {},
    val onAllowAgain: (SyncDirection) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onWritePast: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
)

/** Stateless Health Connect settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectContent(state: HealthConnectUiState, actions: HealthConnectActions, modifier: Modifier = Modifier) {
    FrostedScaffold(
        modifier = modifier,
        topBar = { RidgelineTopAppBar(title = "Health Connect", onNavigateUp = actions.onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    "Share workouts with the apps you use for health: your watch's app, Samsung Health, Fitbit and others. " +
                        "Health Connect keeps the data on this phone.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val status = state.status
                when {
                    status == null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    !status.isAvailable -> Unavailable(status.availability, actions)
                    else -> Available(state, actions)
                }
                OverlineHeader("Your data")
                Text(
                    "StravaFit writes your activities (sport, time, distance, route and heart rate) only when you turn writing " +
                        "on, and reads other apps' workouts only when you turn reading on. Nothing is sent anywhere else. " +
                        "You can change or withdraw access at any time in Health Connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textSecondary,
                )
                RidgelineTextButton(text = "Privacy", onClick = actions.onOpenPrivacy)
            }
        }
    }
}

@Composable
private fun Unavailable(availability: HealthConnectAvailability, actions: HealthConnectActions) {
    val (title, body) = when (availability) {
        HealthConnectAvailability.NOT_INSTALLED ->
            "Health Connect isn't installed" to "Install Health Connect from Google to share workouts with other apps. It is free."
        HealthConnectAvailability.UPDATE_REQUIRED ->
            "Health Connect needs an update" to "This version of Health Connect is too old for StravaFit. Update it, then come back here."
        else ->
            "Health Connect isn't available" to "This phone does not support Health Connect (it needs Android 9 or later)."
    }
    RidgelineCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = RidgelineTheme.colors.warning, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = Spacing.m)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
            }
        }
    }
    when (availability) {
        HealthConnectAvailability.NOT_INSTALLED ->
            PrimaryButton(text = "Install Health Connect", onClick = actions.onInstall, leadingIcon = Icons.Rounded.OpenInNew, modifier = Modifier.fillMaxWidth())
        HealthConnectAvailability.UPDATE_REQUIRED ->
            PrimaryButton(text = "Update Health Connect", onClick = actions.onInstall, leadingIcon = Icons.Rounded.OpenInNew, modifier = Modifier.fillMaxWidth())
        else -> Unit
    }
}

@Composable
private fun Available(state: HealthConnectUiState, actions: HealthConnectActions) {
    val status = state.status ?: return
    val write = status.switchState(SyncDirection.WRITE, state.writeEnabled)
    val read = status.switchState(SyncDirection.READ, state.readEnabled)
    val busy = state.busy
    RidgelineCard(contentPadding = Spacing.none) {
        StatusRow(Icons.Rounded.CheckCircle, "Connected to Health Connect", null, RidgelineTheme.colors.success)
        HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
        ActionRowSimple(Icons.Rounded.OpenInNew, "Open Health Connect", "See your data and choose what StravaFit may use", actions.onOpenHealthConnect)
    }

    OverlineHeader("Write")
    RidgelineCard(contentPadding = Spacing.none) {
        SyncSwitch(
            title = "Write activities to Health Connect",
            subtitle = "Each activity you save: sport, time, distance, route and heart rate",
            state = write,
            onChange = actions.onWrite,
        )
        if (write == SyncSwitchState.NEEDS_PERMISSION) {
            Revoked("Health Connect no longer allows StravaFit to write.") { actions.onAllowAgain(SyncDirection.WRITE) }
        }
        if (write == SyncSwitchState.ON) {
            val left = status.leftOut(SyncDirection.WRITE)
            if (left.isNotEmpty()) Note("Written without ${left.joinToString(" and ")}: allow it in Health Connect to include it.")
            HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
            val writing = busy as? HealthBusy.WritingPast
            ActionRowSimple(
                Icons.Rounded.History,
                "Write past activities",
                when {
                    writing == null -> "Send everything already on this phone. Activities already there are updated, not doubled."
                    writing.total == 0 -> "Getting ready"
                    else -> "${writing.done} of ${writing.total}"
                },
                actions.onWritePast,
                busy = writing != null,
                enabled = busy == null,
            )
        }
    }

    OverlineHeader("Read")
    RidgelineCard(contentPadding = Spacing.none) {
        SyncSwitch(
            title = "Import workouts from other apps",
            subtitle = "Garmin Connect, Samsung Health and other apps that write to Health Connect. Workouts already here are skipped.",
            state = read,
            onChange = actions.onRead,
        )
        if (read == SyncSwitchState.NEEDS_PERMISSION) {
            Revoked("Health Connect no longer allows StravaFit to read.") { actions.onAllowAgain(SyncDirection.READ) }
        }
        if (read == SyncSwitchState.ON) {
            val left = status.leftOut(SyncDirection.READ)
            if (left.isNotEmpty()) Note("Imported without ${left.joinToString(" and ")}: allow it in Health Connect to include it.")
            Note(
                if (status.canReadInBackground) {
                    "Checks for new workouts once a day, and whenever you open StravaFit."
                } else {
                    "Checks for new workouts whenever you open StravaFit. This version of Health Connect doesn't let apps read in the background."
                },
            )
            HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
            ActionRowSimple(
                Icons.Rounded.Sync,
                "Sync now",
                state.lastSyncMillis?.let { "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" }
                    ?: "Not checked yet",
                actions.onSyncNow,
                busy = busy == HealthBusy.Syncing,
                enabled = busy == null,
            )
        }
    }
}

@Composable
private fun SyncSwitch(title: String, subtitle: String, state: SyncSwitchState, onChange: (Boolean) -> Unit) {
    val checked = state == SyncSwitchState.ON || state == SyncSwitchState.NEEDS_PERMISSION
    val enabled = state != SyncSwitchState.UNAVAILABLE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun Revoked(text: String, onAllow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = Spacing.s))
        RidgelineTextButton(text = "Allow", onClick = onAllow)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = RidgelineTheme.colors.textMuted,
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m),
    )
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, subtitle: String?, tint: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.listRowMin).padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary) }
        }
    }
}

@Composable
private fun ActionRowSimple(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = RidgelineTheme.colors.textSecondary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled || busy) MaterialTheme.colorScheme.onSurface else RidgelineTheme.colors.textMuted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** Opens Health Connect's own screen: its settings (Android 14+) or the Health Connect app. */
private fun openHealthConnect(context: Context): Boolean {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(HealthConnectClient.getHealthConnectManageDataIntent(context))
        add(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }
    return intents.any { start(context, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The Health Connect page of the Play Store, or of the web store when there is no store app. */
private fun openStore(context: Context): Boolean {
    val pkg = AndroidHealthConnectGateway.PROVIDER
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg&url=healthconnect%3A%2F%2Fonboarding"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
    return start(context, market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) || start(context, web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
