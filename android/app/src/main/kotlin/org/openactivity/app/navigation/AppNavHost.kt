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

/** The app navigation graph. */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val motion = RidgelineTheme.motion
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
            SettingsScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
    }
}

/** Opens the full-screen recorder once, however many times the entry point is tapped. */
private fun NavHostController.openRecorder() {
    navigateToRecord()
}
