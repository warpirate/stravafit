// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
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

/**
 * The "Import activity file" screen.
 *
 * @property uris content URIs of files shared to the app ("Open with", the share sheet), imported as
 *   soon as the screen opens; empty when the user picks files on the screen.
 */
@Serializable
data class ImportActivitiesRoute(val uris: List<String> = emptyList())

/** Opens the detail screen of [activityId]. */
fun NavController.navigateToActivityDetail(activityId: Long, builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) {
    navigate(ActivityDetailRoute(activityId), builder)
}

/** Opens the import screen; [uris] are files shared to the app, imported right away. */
fun NavController.navigateToImportActivities(uris: List<String> = emptyList()) {
    // Shared files always get a fresh screen, so a second share is not swallowed by launchSingleTop.
    navigate(ImportActivitiesRoute(uris)) { launchSingleTop = uris.isEmpty() }
}

/**
 * Actions the detail screen hands back to the app's navigation.
 *
 * @property onBack leave the screen.
 * @property onEdit open the edit screen of the activity.
 * @property onShare open the share & export screen of the activity.
 * @property onCreateSegment open "Create segment" on the activity.
 * @property onOpenSegment open the detail of a segment (from the Segments section).
 */
data class ActivityDetailNavigation(
    val onBack: () -> Unit,
    val onEdit: (Long) -> Unit,
    val onShare: (Long) -> Unit,
    val onCreateSegment: (Long) -> Unit = {},
    val onOpenSegment: (Long) -> Unit = {},
)

/** Adds the activity detail destination, reachable from [deepLinks] too. */
fun NavGraphBuilder.activityDetailScreen(navigation: ActivityDetailNavigation, deepLinks: List<NavDeepLink> = emptyList()) {
    composable<ActivityDetailRoute>(deepLinks = deepLinks) {
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
