// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.suggest

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.openactivity.core.location.LocationPermissions

/**
 * Wires [SuggestionActions] to [viewModel]: the location permission request, the system location
 * settings, and a retry when the screen comes back (the athlete may have changed a setting meanwhile).
 */
@Composable
fun rememberSuggestionActions(viewModel: RouteSuggestionsViewModel, onOpen: (String) -> Unit): SuggestionActions {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.retry() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    return SuggestionActions(
        onOpen = onOpen,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onAllowLocation = { permission.launch(LocationPermissions.REQUIRED) },
        onLocationSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                viewModel.retry()
            }
        },
        onDismissIntro = viewModel::dismissIntro,
        onSetEnabled = viewModel::setEnabled,
        onSelectSport = viewModel::selectSport,
        onSelectDistance = viewModel::selectDistance,
    )
}
