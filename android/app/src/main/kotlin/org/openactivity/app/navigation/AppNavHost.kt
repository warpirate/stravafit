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
import org.openactivity.app.feature.goals.GoalsScreen
import org.openactivity.app.feature.notifications.InboxDestination
import org.openactivity.app.feature.notifications.NotificationsScreen
import org.openactivity.app.feature.groups.GroupsScreen
import org.openactivity.app.feature.me.MeScreen
import org.openactivity.app.feature.privacy.PrivacyScreen
import org.openactivity.app.feature.privacy.PrivacyZoneEditorScreen
import org.openactivity.app.feature.privacy.PrivacyZonesScreen
import org.openactivity.app.feature.privacy.VisibilityScreen
import org.openactivity.app.feature.profile.EditProfileScreen
import org.openactivity.app.feature.route.RouteDetailScreen
import org.openactivity.app.feature.route.SuggestedRouteScreen
import org.openactivity.app.feature.settings.LicensesScreen
import org.openactivity.app.feature.settings.SettingsScreen
import org.openactivity.app.feature.healthconnect.HealthConnectScreen
import org.openactivity.app.feature.stravaimport.StravaImportScreen
import org.openactivity.core.datastore.LaunchTab
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
 * suggested route, file import, the Strava import, Health Connect, edit profile, licences, the
 * notifications inbox, goals, and the privacy page with privacy zones and per-sport audiences. First-run
 * onboarding is shown by `MainActivity` before this graph exists. Activity, route and recorder
 * destinations also answer the [AppLinks].
 */
@Composable
fun AppNavHost(navController: NavHostController, startTab: LaunchTab = LaunchTab.HOME, modifier: Modifier = Modifier) {
    val motion = RidgelineTheme.motion
    val exportProvider = rememberExportActivityProvider()
    val openActivity: (Long) -> Unit = { id -> navController.navigateToActivityDetail(id) }
    val openSuggestion: (String) -> Unit = { id -> navController.navigate(SuggestedRouteRoute(id)) { launchSingleTop = true } }
    NavHost(
        navController = navController,
        // Record is not a tab root: the app opens on Home with the recorder on top (see MainActivity).
        startDestination = when (startTab) {
            LaunchTab.HOME, LaunchTab.RECORD -> FeedRoute
            LaunchTab.MAPS -> ExploreRoute
            LaunchTab.YOU -> MeRoute
        },
        modifier = modifier,
        enterTransition = { fadeIn(tween(motion.enter(motion.short))) },
        exitTransition = { fadeOut(tween(motion.exit(motion.short))) },
    ) {
        composable<FeedRoute> {
            FeedScreen(
                onRecord = { navController.openRecorder() },
                onOpenActivity = openActivity,
                onShareActivity = { id -> navController.navigateToShareExport(id.toString()) },
                onOpenNotifications = dropUnlessResumed { navController.navigate(NotificationsRoute) { launchSingleTop = true } },
                onOpenSuggestion = openSuggestion,
            )
        }
        composable<ExploreRoute> {
            ExploreScreen(
                onOpenRoute = { id -> navController.navigate(RouteDetailRoute(id)) { launchSingleTop = true } },
                onOpenSuggestion = openSuggestion,
            )
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
                onOpenGoals = { newGoal -> navController.navigate(GoalsRoute(newGoal)) { launchSingleTop = true } },
            )
        }
        composable<NotificationsRoute> {
            NotificationsScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onOpen = { destination -> navController.openInboxDestination(destination) },
            )
        }
        composable<GoalsRoute> {
            GoalsScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
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
            deepLinks = AppLinks.record(),
        )
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onEditProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } },
                onOpenLicenses = { navController.navigate(LicensesRoute) { launchSingleTop = true } },
                onImportActivities = dropUnlessResumed { navController.navigateToImportActivities() },
                onImportFromStrava = dropUnlessResumed { navController.navigate(StravaImportRoute()) { launchSingleTop = true } },
                onOpenHealthConnect = dropUnlessResumed { navController.navigate(HealthConnectRoute) { launchSingleTop = true } },
                onOpenPrivacy = dropUnlessResumed { navController.navigate(PrivacyRoute) { launchSingleTop = true } },
                onOpenPrivacyZones = dropUnlessResumed { navController.navigate(PrivacyZonesRoute) { launchSingleTop = true } },
                onOpenVisibility = dropUnlessResumed { navController.navigate(VisibilityRoute) { launchSingleTop = true } },
            )
        }
        composable<StravaImportRoute> {
            StravaImportScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onOpenActivities = { navController.switchTab(MeRoute) },
            )
        }
        composable<HealthConnectRoute> {
            HealthConnectScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onOpenPrivacy = dropUnlessResumed { navController.navigate(PrivacyRoute) { launchSingleTop = true } },
            )
        }
        composable<PrivacyRoute> {
            PrivacyScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onOpenZones = dropUnlessResumed { navController.navigate(PrivacyZonesRoute) { launchSingleTop = true } },
                onOpenVisibility = dropUnlessResumed { navController.navigate(VisibilityRoute) { launchSingleTop = true } },
                // Settings is below the Privacy page; going back there shows the hiding and backup rows.
                onOpenSettings = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<PrivacyZonesRoute> {
            PrivacyZonesScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onAddZone = dropUnlessResumed { navController.navigate(PrivacyZoneEditorRoute()) { launchSingleTop = true } },
                onEditZone = { id -> navController.navigate(PrivacyZoneEditorRoute(id)) { launchSingleTop = true } },
            )
        }
        composable<PrivacyZoneEditorRoute> {
            PrivacyZoneEditorScreen(onDone = dropUnlessResumed { navController.popBackStack() })
        }
        composable<VisibilityRoute> {
            VisibilityScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
        composable<EditProfileRoute> {
            EditProfileScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
        composable<LicensesRoute> {
            LicensesScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
        }
        composable<RouteDetailRoute>(deepLinks = AppLinks.route()) {
            RouteDetailScreen(
                onNavigateUp = dropUnlessResumed { navController.popBackStack() },
                onRecordRoute = { routeId -> navController.openRecorder(routeId) },
            )
        }
        composable<SuggestedRouteRoute> {
            SuggestedRouteScreen(
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
            deepLinks = AppLinks.activity(),
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

/** Opens where an inbox entry leads: the activity, the goals, or the You tab for progress and activities. */
fun NavHostController.openInboxDestination(destination: InboxDestination) {
    when (destination) {
        is InboxDestination.Activity -> navigateToActivityDetail(destination.id)
        InboxDestination.Goals -> navigate(GoalsRoute()) { launchSingleTop = true }
        InboxDestination.Progress, InboxDestination.Activities -> switchTab(MeRoute)
        InboxDestination.Settings -> navigate(SettingsRoute) { launchSingleTop = true }
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
