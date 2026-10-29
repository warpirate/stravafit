// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import org.openactivity.app.feature.record.RecordScreen
import org.openactivity.app.feature.route.RouteDetailScreen
import org.openactivity.app.feature.settings.LicensesScreen
import org.openactivity.app.feature.settings.SettingsScreen
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.core.designsystem.theme.RidgelineTheme

/** The app navigation graph. */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val motion = RidgelineTheme.motion
    val messenger = LocalMessenger.current
    val openActivity: (Long) -> Unit = { id ->
        if (!navController.openActivityDetail(id)) messenger.show("Activity details could not be opened")
    }
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
        composable<RecordRoute>(
            enterTransition = { slideInVertically(tween(motion.enter(motion.long))) { it } },
            exitTransition = { fadeOut(tween(motion.exit(motion.short))) },
            popExitTransition = { slideOutVertically(tween(motion.exit(motion.long))) { it } },
        ) {
            // dropUnlessResumed: a double tap during the exit transition must not pop the tab root too.
            RecordScreen(onClose = dropUnlessResumed { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onEditProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } },
                onOpenLicenses = { navController.navigate(LicensesRoute) { launchSingleTop = true } },
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
    }
}

/** Opens the full-screen recorder once, however many times the entry point is tapped. */
private fun NavHostController.openRecorder(routeId: Long? = null) {
    navigate(RecordRoute(routeId)) { launchSingleTop = true }
}

/** Switches to another tab root, keeping each tab's saved back stack. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens the activity detail registered by the detail feature under [ActivityDetailContract.ROUTE].
 * Returns false when no such destination is in the graph.
 */
fun NavHostController.openActivityDetail(id: Long): Boolean = try {
    navigate(ActivityDetailContract.route(id)) { launchSingleTop = true }
    true
} catch (e: IllegalArgumentException) {
    false
}
