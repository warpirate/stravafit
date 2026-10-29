// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.feature.route.RouteDetailViewModel
import org.robolectric.RobolectricTestRunner

/**
 * The navigation contracts other features rely on: the recorder and route detail read their ids
 * from a [SavedStateHandle] under fixed keys.
 *
 * The handles come from a view model on each destination, the same way the Hilt view models of the
 * screens receive their navigation arguments.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationContractTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val handles = mutableMapOf<String, SavedStateHandle>()

    private fun setUpGraph() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = FeedRoute) {
                composable<FeedRoute> {}
                composable<RecordRoute> { handles["record"] = viewModel<HandleViewModel>().handle }
                composable<RouteDetailRoute> { handles["route"] = viewModel<HandleViewModel>().handle }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `record this route passes the route id under routeId`() {
        setUpGraph()
        compose.runOnIdle { nav.navigate(RecordRoute(routeId = 42L)) }
        compose.waitForIdle()
        assertEquals(42L, handles.getValue("record").get<Long>("routeId"))
    }

    @Test
    fun `plain recording has no route id`() {
        setUpGraph()
        compose.runOnIdle { nav.navigate(RecordRoute()) }
        compose.waitForIdle()
        assertNull(handles.getValue("record").get<Long>("routeId"))
    }

    @Test
    fun `route detail reads its id under the view model key`() {
        setUpGraph()
        compose.runOnIdle { nav.navigate(RouteDetailRoute(7L)) }
        compose.waitForIdle()
        assertEquals(7L, handles.getValue("route").get<Long>(RouteDetailViewModel.ARG_ROUTE_ID))
    }
}

/** Exposes the [SavedStateHandle] a destination's view model receives. */
class HandleViewModel(val handle: SavedStateHandle) : ViewModel()
