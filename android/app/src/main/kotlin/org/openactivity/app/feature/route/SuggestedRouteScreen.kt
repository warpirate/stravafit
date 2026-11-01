// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.route

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.NO_FILE_PICKER_MESSAGE
import org.openactivity.app.ui.common.launchOrElse

/** A suggested loop on the route detail layout: save it, record it, or export it as GPX. */
@Composable
fun SuggestedRouteScreen(
    onNavigateUp: () -> Unit,
    onRecordRoute: (Long) -> Unit,
    viewModel: SuggestedRouteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        uri?.let(viewModel::exportGpx)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            messenger.show(it)
            viewModel.messageShown()
        }
    }
    LaunchedEffect(state.recordRouteId) {
        state.recordRouteId?.let {
            viewModel.recordHandled()
            onRecordRoute(it)
        }
    }
    RouteDetailContent(
        state = state,
        actions = RouteDetailActions(
            onNavigateUp = onNavigateUp,
            onRecord = viewModel::record,
            onExportGpx = { saveGpx.launchOrElse(state.fileName) { messenger.show(NO_FILE_PICKER_MESSAGE) } },
            onSave = viewModel::save,
        ),
    )
}
