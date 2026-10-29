// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.openactivity.feature.shareexport.ui.ShareExportScreenHost

/** Type-safe navigation destination of the share screen for the activity with [activityId]. */
@Serializable
public data class ShareExportRoute(val activityId: String)

/** Opens the share screen for [activityId]. */
public fun NavController.navigateToShareExport(activityId: String, builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) {
    navigate(ShareExportRoute(activityId), builder)
}

/**
 * Adds the share screen to the graph. [provider] loads activities from the app's storage; [onClose]
 * leaves the screen (usually `popBackStack`).
 */
public fun NavGraphBuilder.shareExportScreen(provider: ExportActivityProvider, onClose: () -> Unit) {
    composable<ShareExportRoute> { entry ->
        val activityId = entry.toRoute<ShareExportRoute>().activityId
        val context = LocalContext.current
        val viewModel: ShareExportViewModel = viewModel(
            key = "share-export-$activityId",
            factory = ShareExportViewModel.factory(activityId, provider, context),
        )
        ShareExportScreenHost(viewModel = viewModel, onClose = onClose)
    }
}
