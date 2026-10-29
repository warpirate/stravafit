// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import org.openactivity.app.feature.settings.SettingsScreen
import org.openactivity.feature.record.navigation.navigateToRecord
import org.openactivity.feature.record.navigation.recordScreen
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.feature.activitydetail.ActivityDetailNavigation
import org.openactivity.feature.activitydetail.ImportActivitiesRoute
import org.openactivity.feature.activitydetail.activityDetailScreen
import org.openactivity.feature.activitydetail.importActivitiesScreen
import org.openactivity.feature.activitydetail.navigateToActivityDetail
import org.openactivity.feature.activitydetail.navigateToImportActivities
import org.openactivity.feature.activitydetail.rememberExportActivityProvider
import org.openactivity.feature.saveedit.SaveEditNavigation
import org.openactivity.feature.saveedit.SaveEditRoute
import org.openactivity.feature.saveedit.navigateToEditActivity
import org.openactivity.feature.saveedit.saveEditScreen
import org.openactivity.feature.shareexport.navigateToShareExport
import org.openactivity.feature.shareexport.shareExportScreen

/** The app navigation graph. */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val motion = RidgelineTheme.motion
    val exportProvider = rememberExportActivityProvider()
    NavHost(
        navController = navController,
        startDestination = FeedRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(motion.enter(motion.short))) },
        exitTransition = { fadeOut(tween(motion.exit(motion.short))) },
    ) {
        composable<FeedRoute> {
            FeedScreen(onRecord = { navController.openRecorder() })
        }
        composable<ExploreRoute> { ExploreScreen() }
        composable<GroupsRoute> { GroupsScreen() }
        composable<MeRoute> {
            MeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                onRecord = { navController.openRecorder() },
            )
        }
        recordScreen(
            onClose = { navController.popBackStack() },
            onActivitySaved = {
                // Saved activities show under You until the save/edit screen lands; Record is left either way.
                navController.popBackStack()
                navController.navigate(MeRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            durationMillis = motion.enter(motion.long),
        )
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onImportActivities = dropUnlessResumed { navController.navigateToImportActivities() },
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
private fun NavHostController.openRecorder() {
    navigateToRecord()
}
