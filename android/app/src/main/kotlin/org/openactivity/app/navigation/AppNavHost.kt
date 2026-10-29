// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.openactivity.app.feature.explore.ExploreScreen
import org.openactivity.app.feature.feed.FeedScreen
import org.openactivity.app.feature.groups.GroupsScreen
import org.openactivity.app.feature.me.MeScreen
import org.openactivity.app.feature.profile.EditProfileScreen
import org.openactivity.app.feature.route.RouteDetailScreen
import org.openactivity.app.feature.settings.LicensesScreen
import org.openactivity.app.feature.settings.SettingsScreen
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.feature.activitydetail.ActivityDetailNavigation
import org.openactivity.feature.activitydetail.ImportActivitiesRoute
import org.openactivity.feature.activitydetail.activityDetailScreen
import org.openactivity.feature.activitydetail.importActivitiesScreen
import org.openactivity.feature.activitydetail.navigateToActivityDetail
import org.openactivity.feature.activitydetail.navigateToImportActivities
import org.openactivity.feature.activitydetail.rememberExportActivityProvider
import org.openactivity.feature.record.navigation.navigateToRecord
import org.openactivity.feature.record.navigation.recordScreen
import org.openactivity.feature.saveedit.SaveEditNavigation
import org.openactivity.feature.saveedit.SaveEditRoute
import org.openactivity.feature.saveedit.navigateToEditActivity
import org.openactivity.feature.saveedit.navigateToSaveActivity
import org.openactivity.feature.saveedit.saveEditScreen
import org.openactivity.feature.shareexport.navigateToShareExport
import org.openactivity.feature.shareexport.shareExportScreen

/**
 * The app navigation graph. Every destination is registered exactly once: the four tabs (feed,
 * explore, groups, me), record, settings, save/edit, activity detail, share & export, route detail,
 * file import, edit profile and licences. First-run onboarding is shown by `MainActivity` before
 * this graph exists.
 */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val motion = RidgelineTheme.motion
    val exportProvider = rememberExportActivityProvider()
    val openActivity: (Long) -> Unit = { id -> navController.navigateToActivityDetail(id) }
    NavHost(
        navController = navController,
        startDestination = FeedRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(motion.enter(motion.short))) },
        exitTransition = { fadeOut(tween(motion.exit(motion.short))) },
    ) {
        composable<FeedRoute> {
            FeedScreen(onRecord = { navController.openRecorder() }, onOpenActivity = openActivity)
        }
        composable<ExploreRoute> {
            ExploreScreen(onOpenRoute = { id -> navController.navigate(RouteDetailRoute(id)) { launchSingleTop = true } })
        }
        composable<GroupsRoute> {
            GroupsScreen(onShowYourActivities = { navController.switchTab(FeedRoute) })
        }
        composable<MeRoute> {
            MeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                onEditProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } },
                onOpenActivity = openActivity,
                onRecord = { navController.openRecorder() },
            )
        }
        recordScreen(
            onClose = { navController.popBackStack() },
            onActivitySaved = { id ->
                // The recorder already stored the activity; the Save screen replaces Record.
                navController.navigateToSaveActivity(id) {
                    popUpTo<RecordRoute> { inclusive = true }
                    launchSingleTop = true
                }
            },
            durationMillis = motion.enter(motion.long),
        )
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onEditProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } },
                onOpenLicenses = { navController.navigate(LicensesRoute) { launchSingleTop = true } },
                onImportActivities = dropUnlessResumed { navController.navigateToImportActivities() },
            )
        }
        composable<EditProfileRoute> {
            EditProfileScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
        composable<LicensesRoute> {
            LicensesScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
        composable<RouteDetailRoute> {
            RouteDetailScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onRecordRoute = { routeId -> navController.openRecorder(routeId) },
            )
        }
        activityDetailScreen(
            ActivityDetailNavigation(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigateToEditActivity(id) },
                onShare = { id -> navController.navigateToShareExport(id.toString()) },
            ),
        )
        saveEditScreen(
            SaveEditNavigation(
                onSaved = { id, isNew ->
                    if (isNew) {
                        // After the first save, show the activity in place of the save screen.
                        navController.navigateToActivityDetail(id) {
                            popUpTo<SaveEditRoute> { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDiscarded = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
            ),
        )
        importActivitiesScreen(
            onBack = { navController.popBackStack() },
            onOpenActivity = { id ->
                navController.navigateToActivityDetail(id) { popUpTo<ImportActivitiesRoute> { inclusive = true } }
            },
        )
        shareExportScreen(provider = exportProvider, onClose = { navController.popBackStack() })
    }
}

/** Opens the full-screen recorder once, however many times the entry point is tapped. */
private fun NavHostController.openRecorder(routeId: Long? = null) {
    navigateToRecord(routeId)
}

/** Switches to another tab root, keeping each tab's saved back stack. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
