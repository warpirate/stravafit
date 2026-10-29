// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import org.openactivity.feature.activitydetail.fileimport.ImportScreen
import org.openactivity.feature.activitydetail.ui.ActivityDetailScreen
import org.openactivity.feature.shareexport.ExportActivityProvider

/** Detail screen of the saved activity with database id [activityId]. */
@Serializable
data class ActivityDetailRoute(val activityId: Long)

/** The "Import activity file" screen. */
@Serializable
data object ImportActivitiesRoute

/** Opens the detail screen of [activityId]. */
fun NavController.navigateToActivityDetail(activityId: Long, builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) {
    navigate(ActivityDetailRoute(activityId), builder)
}

/** Opens the import screen. */
fun NavController.navigateToImportActivities() {
    navigate(ImportActivitiesRoute) { launchSingleTop = true }
}

/**
 * Actions the detail screen hands back to the app's navigation.
 *
 * @property onBack leave the screen.
 * @property onEdit open the edit screen of the activity.
 * @property onShare open the share & export screen of the activity.
 */
data class ActivityDetailNavigation(
    val onBack: () -> Unit,
    val onEdit: (Long) -> Unit,
    val onShare: (Long) -> Unit,
)

/** Adds the activity detail destination. */
fun NavGraphBuilder.activityDetailScreen(navigation: ActivityDetailNavigation) {
    composable<ActivityDetailRoute> {
        ActivityDetailScreen(navigation = navigation)
    }
}

/**
 * Adds the import destination. [onOpenActivity] opens an imported activity; it should replace the
 * import screen so Back returns to where the import started.
 */
fun NavGraphBuilder.importActivitiesScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    composable<ImportActivitiesRoute> {
        ImportScreen(onBack = onBack, onOpenActivity = onOpenActivity)
    }
}

/** Hilt access to the repository-backed [ExportActivityProvider] for wiring the share screen. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ActivityDetailEntryPoint {
    /** The provider that loads stored activities for sharing and export. */
    fun exportActivityProvider(): ExportActivityProvider
}

/** The app's [ExportActivityProvider], for `shareExportScreen(provider = ...)` in the nav graph. */
@Composable
fun rememberExportActivityProvider(): ExportActivityProvider {
    val context = LocalContext.current.applicationContext
    return remember(context) { EntryPointAccessors.fromApplication(context, ActivityDetailEntryPoint::class.java).exportActivityProvider() }
}
