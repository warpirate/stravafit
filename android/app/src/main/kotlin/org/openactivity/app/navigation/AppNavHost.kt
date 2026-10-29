// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.openactivity.app.feature.explore.ExploreScreen
import org.openactivity.app.feature.feed.FeedScreen
import org.openactivity.app.feature.groups.GroupsScreen
import org.openactivity.app.feature.me.MeScreen
import org.openactivity.app.feature.record.RecordScreen
import org.openactivity.app.feature.settings.SettingsScreen
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
        composable<RecordRoute>(
            enterTransition = { slideInVertically(tween(motion.enter(motion.long))) { it } },
            exitTransition = { fadeOut(tween(motion.exit(motion.short))) },
            popExitTransition = { slideOutVertically(tween(motion.exit(motion.long))) { it } },
        ) {
            // dropUnlessResumed: a double tap during the exit transition must not pop the tab root too.
            RecordScreen(onClose = dropUnlessResumed { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
    }
}

/** Opens the full-screen recorder once, however many times the entry point is tapped. */
private fun NavHostController.openRecorder() {
    navigate(RecordRoute) { launchSingleTop = true }
}
