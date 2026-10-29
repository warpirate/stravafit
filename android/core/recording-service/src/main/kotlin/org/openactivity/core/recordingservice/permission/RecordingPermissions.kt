// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.openactivity.core.location.LocationPermissions
import org.openactivity.core.recordingservice.R

/**
 * Drives [RecordingPermissionFlow] from Compose. Call [request] when the athlete taps Start; `onReady`
 * runs once precise location is granted (the notification permission is asked but never blocks).
 * Render [RecordingPermissionSheet] with it to show the rationale and settings sheets.
 */
@Stable
class RecordingPermissionRequester internal constructor(
    private val snapshot: () -> PermissionSnapshot,
    private val launchLocation: () -> Unit,
    private val launchNotifications: () -> Unit,
    private val openSettings: () -> Unit,
    private val onReady: () -> Unit,
) {
    /** The sheet being shown. */
    var step: PermissionStep by mutableStateOf(PermissionStep.IDLE)
        internal set

    internal var notificationsAsked = false

    /** True when precise location is already granted. */
    val hasLocation: Boolean get() = snapshot().preciseLocation

    /** Starts the flow; calls `onReady` directly when nothing needs asking. */
    fun request() = advance(RecordingPermissionFlow.onStart(snapshot()))

    internal fun advance(next: PermissionStep?) {
        if (next == null) {
            step = PermissionStep.IDLE
            onReady()
        } else {
            step = next
        }
    }

    internal fun confirm() = when (step) {
        PermissionStep.LOCATION_RATIONALE -> launchLocation()
        PermissionStep.NOTIFICATION_RATIONALE -> {
            notificationsAsked = true
            launchNotifications()
        }
        PermissionStep.LOCATION_SETTINGS -> {
            step = PermissionStep.IDLE
            openSettings()
        }
        PermissionStep.IDLE -> Unit
    }

    internal fun dismiss() {
        val wasNotifications = step == PermissionStep.NOTIFICATION_RATIONALE
        step = PermissionStep.IDLE
        if (wasNotifications) {
            notificationsAsked = true
            onReady()
        }
    }
}

/** Remembers a [RecordingPermissionRequester] bound to the current activity. */
@Composable
fun rememberRecordingPermissionRequester(onReady: () -> Unit): RecordingPermissionRequester {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val currentOnReady by rememberUpdatedState(onReady)
    var requester: RecordingPermissionRequester? = null

    fun snapshot(asked: Boolean) = PermissionSnapshot(
        preciseLocation = LocationPermissions.hasPrecise(context),
        notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        canAskLocation = activity == null || !locationAskedBefore(context) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION),
        shouldAskNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked,
    )

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        markLocationAsked(context)
        requester?.let { r -> r.advance(RecordingPermissionFlow.afterLocationResult(snapshot(r.notificationsAsked))) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requester?.advance(null)
    }
    requester = remember(context) {
        lateinit var self: RecordingPermissionRequester
        self = RecordingPermissionRequester(
            snapshot = { snapshot(self.notificationsAsked) },
            launchLocation = { locationLauncher.launch(LocationPermissions.REQUIRED) },
            launchNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    self.advance(null)
                }
            },
            openSettings = { context.startActivity(appSettingsIntent(context)) },
            onReady = { currentOnReady() },
        )
        self
    }
    return requester
}

/** The rationale and settings sheets of [requester]; renders nothing while idle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingPermissionSheet(requester: RecordingPermissionRequester) {
    val step = requester.step
    if (step == PermissionStep.IDLE) return
    val (title, body, confirm) = when (step) {
        PermissionStep.LOCATION_RATIONALE -> Triple(
            R.string.permission_location_title, R.string.permission_location_rationale, R.string.permission_continue,
        )
        PermissionStep.LOCATION_SETTINGS -> Triple(
            R.string.permission_location_title, R.string.permission_location_denied, R.string.permission_open_settings,
        )
        else -> Triple(
            R.string.permission_notifications_title, R.string.permission_notifications_rationale, R.string.permission_continue,
        )
    }
    ModalBottomSheet(onDismissRequest = requester::dismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = requester::dismiss) { Text(stringResource(R.string.permission_not_now)) }
                Button(onClick = requester::confirm) { Text(stringResource(confirm)) }
            }
        }
    }
}

/** Intent for this app's system settings page (permissions live there). */
fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private const val PREFS = "recording_permissions"
private const val KEY_LOCATION_ASKED = "location_asked"

// Before the first request shouldShowRequestPermissionRationale is false too, so remember that we asked.
private fun locationAskedBefore(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCATION_ASKED, false)

private fun markLocationAsked(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCATION_ASKED, true).apply()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
