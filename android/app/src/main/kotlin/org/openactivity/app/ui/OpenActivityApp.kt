// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import kotlinx.coroutines.launch
import org.openactivity.app.LaunchRequest
import org.openactivity.core.datastore.LaunchTab
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.navigation.ActiveRecordingBar
import org.openactivity.app.navigation.AppBottomBar
import org.openactivity.app.feature.notifications.InboxLink
import org.openactivity.app.feature.notifications.InboxLinkViewModel
import org.openactivity.app.navigation.AppNavHost
import org.openactivity.app.navigation.NotificationsRoute
import org.openactivity.app.navigation.openInboxDestination
import org.openactivity.app.navigation.TopLevelDestination
import org.openactivity.app.navigation.RouteDetailRoute
import org.openactivity.feature.activitydetail.navigateToActivityDetail
import org.openactivity.feature.activitydetail.navigateToImportActivities
import org.openactivity.feature.record.navigation.navigateToRecord

/**
 * App shell: one scaffold with the bottom bar and a snackbar host around the nav host.
 * The bottom bar shows only on tab roots; Record and Settings are full screen.
 *
 * @param launchRequest a pending request from a notification tap, a shared file or a link.
 * @param onLaunchRequestHandled called once [launchRequest] has been navigated to.
 * @param startTab the tab the app opens on (settings, "Open app on").
 */
@Composable
fun OpenActivityApp(
    navController: NavHostController = rememberNavController(),
    launchRequest: LaunchRequest? = null,
    onLaunchRequestHandled: () -> Unit = {},
    startTab: LaunchTab = LaunchTab.HOME,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember(snackbar, scope) {
        Messenger { message ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }
    // Files another app opened with us or shared to us wait here until the user says what they are.
    var sharedFiles by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val sharedFilesViewModel: SharedFilesViewModel = hiltViewModel()
    // The recording notifications ask for the recorder or for the activity just saved; inbox notifications
    // (records, goals, the weekly summary) for their entry's destination; links name a screen.
    val inboxLinks: InboxLinkViewModel = hiltViewModel()
    LaunchedEffect(launchRequest) {
        when (launchRequest) {
            null -> return@LaunchedEffect
            LaunchRequest.OpenRecorder -> navController.navigateToRecord()
            is LaunchRequest.OpenActivity -> navController.navigateToActivityDetail(launchRequest.id)
            // Copied into the app first: the sharing app's grant may be gone by the time the user answers.
            is LaunchRequest.ImportFiles -> sharedFilesViewModel.stage(launchRequest.uris)
            is LaunchRequest.DeepLink -> try {
                // A link or shortcut to the screen already on top (Record, say) does not stack a second copy.
                navController.navigate(launchRequest.uri, navOptions { launchSingleTop = true })
            } catch (e: IllegalArgumentException) {
                messenger.show("That link does not lead anywhere in the app.")
            }
            is LaunchRequest.OpenInboxEntry -> when (val link = inboxLinks.open(launchRequest.id)) {
                InboxLink.Inbox -> navController.navigate(NotificationsRoute) { launchSingleTop = true }
                is InboxLink.To -> navController.openInboxDestination(link.destination)
            }
        }
        onLaunchRequestHandled()
    }
    LaunchedEffect(sharedFilesViewModel) {
        sharedFilesViewModel.staged.collect { staged ->
            when {
                staged.files.isNotEmpty() -> {
                    sharedFiles = staged.files.map { it.toString() }
                    if (staged.rejected > 0) messenger.show("Skipped ${staged.rejected} file(s) that are not GPX, TCX or FIT.")
                }
                else -> messenger.show("That is not a GPX, TCX or FIT file, so there is nothing to import.")
            }
        }
    }
    LaunchedEffect(sharedFilesViewModel) {
        sharedFilesViewModel.routeResults.collect { result ->
            when (result) {
                is SharedRouteImportResult.Done -> {
                    result.openRouteId?.let { navController.navigate(RouteDetailRoute(it)) { launchSingleTop = true } }
                    messenger.show(result.message)
                }
            }
        }
    }
    sharedFiles?.let { uris ->
        SharedFilesDialog(
            count = uris.size,
            onChoose = { target ->
                sharedFiles = null
                when (target) {
                    SharedFilesTarget.Activity -> navController.navigateToImportActivities(uris)
                    SharedFilesTarget.Route -> sharedFilesViewModel.importRoutes(uris.map(Uri::parse))
                }
            },
            onDismiss = { sharedFiles = null },
        )
    }
    val recordingStatus: RecordingStatusViewModel = hiltViewModel()
    val active by recordingStatus.active.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.currentTab()

    val athleteViewModel: AthleteViewModel = hiltViewModel()
    val athlete by athleteViewModel.athlete.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalMessenger provides messenger,
        org.openactivity.core.designsystem.component.LocalAthlete provides athlete,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                // With the bottom bar hidden (Record, Settings) nothing else keeps the snackbar
                // clear of the gesture/navigation bar, because content insets are zero.
                val insets = if (currentTab == null) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier
                SnackbarHost(snackbar, insets)
            },
            bottomBar = {
                if (currentTab != null) {
                    Column {
                        // A recording running behind the tabs (after Back or minimise) stays one tap away.
                        active?.let { rec ->
                            ActiveRecordingBar(rec.time, rec.paused, rec.sport, onClick = { navController.navigateToRecord() })
                        }
                        AppBottomBar(selected = currentTab, onSelect = { navController.navigateTo(it) })
                    }
                }
            },
        ) { padding ->
            AppNavHost(navController, startTab, Modifier.padding(padding))
        }
    }
}

private fun NavDestination.currentTab(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { tab -> !tab.isAction && hierarchy.any { it.hasRoute(tab.routeClass) } }

/** Tab switches keep one saved back stack per tab; Record is pushed on top as a modal flow. */
private fun NavHostController.navigateTo(destination: TopLevelDestination) {
    if (destination.isAction) {
        navigate(destination.route) { launchSingleTop = true }
        return
    }
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
