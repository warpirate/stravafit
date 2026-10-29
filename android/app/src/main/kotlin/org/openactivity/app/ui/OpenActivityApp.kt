// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.openactivity.app.LaunchRequest
import org.openactivity.app.navigation.AppBottomBar
import org.openactivity.app.navigation.AppNavHost
import org.openactivity.app.navigation.TopLevelDestination
import org.openactivity.feature.activitydetail.navigateToActivityDetail
import org.openactivity.feature.record.navigation.navigateToRecord

/**
 * App shell: one scaffold with the bottom bar and a snackbar host around the nav host.
 * The bottom bar shows only on tab roots; Record and Settings are full screen.
 *
 * @param launchRequest a pending request from a notification tap: open the recorder or an activity.
 * @param onLaunchRequestHandled called once [launchRequest] has been navigated to.
 */
@Composable
fun OpenActivityApp(
    navController: NavHostController = rememberNavController(),
    launchRequest: LaunchRequest? = null,
    onLaunchRequestHandled: () -> Unit = {},
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
    // The recording notifications ask for the recorder or for the activity just saved.
    LaunchedEffect(launchRequest) {
        when (launchRequest) {
            null -> return@LaunchedEffect
            LaunchRequest.OpenRecorder -> navController.navigateToRecord()
            is LaunchRequest.OpenActivity -> navController.navigateToActivityDetail(launchRequest.id)
        }
        onLaunchRequestHandled()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.currentTab()

    CompositionLocalProvider(LocalMessenger provides messenger) {
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
                    AppBottomBar(selected = currentTab, onSelect = { navController.navigateTo(it) })
                }
            },
        ) { padding ->
            AppNavHost(navController, Modifier.padding(padding))
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
