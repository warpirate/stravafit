// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.feature.route.RouteDetailViewModel
import org.openactivity.feature.activitydetail.ActivityDetailRoute
import org.openactivity.feature.activitydetail.ImportActivitiesRoute
import org.openactivity.feature.activitydetail.fileimport.ImportViewModel
import org.robolectric.RobolectricTestRunner

/**
 * The navigation contracts other features rely on: the recorder and route detail read their ids
 * from a [SavedStateHandle] under fixed keys, the [AppLinks] (app links, launcher shortcuts) reach
 * their destinations with those arguments, and shared files reach the import screen.
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
                composable<RecordRoute>(deepLinks = AppLinks.record(HOST)) { handles["record"] = viewModel<HandleViewModel>().handle }
                composable<RouteDetailRoute>(deepLinks = AppLinks.route(HOST)) { handles["route"] = viewModel<HandleViewModel>().handle }
                composable<ActivityDetailRoute>(deepLinks = AppLinks.activity(HOST)) { handles["activity"] = viewModel<HandleViewModel>().handle }
                composable<ImportActivitiesRoute> { handles["import"] = viewModel<HandleViewModel>().handle }
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

    private fun open(link: String) {
        compose.runOnIdle { nav.navigate(Uri.parse(link)) }
        compose.waitForIdle()
    }

    private fun current(): NavDestination = nav.currentDestination!!

    @Test
    fun `activity links open the activity detail with its id`() {
        setUpGraph()
        open("openactivity://activity/12")
        assertTrue(current().hasRoute(ActivityDetailRoute::class))
        assertEquals(12L, handles.getValue("activity").get<Long>("activityId"))

        open("https://$HOST/activities/34")
        assertEquals(34L, handles.getValue("activity").get<Long>("activityId"))
    }

    @Test
    fun `route links open the route detail under the view model key`() {
        setUpGraph()
        open("openactivity://route/7")
        assertTrue(current().hasRoute(RouteDetailRoute::class))
        assertEquals(7L, handles.getValue("route").get<Long>(RouteDetailViewModel.ARG_ROUTE_ID))

        open("https://$HOST/routes/8")
        assertEquals(8L, handles.getValue("route").get<Long>(RouteDetailViewModel.ARG_ROUTE_ID))
    }

    @Test
    fun `record links open the recorder, optionally with a sport or a route`() {
        setUpGraph()
        open("openactivity://record")
        assertTrue(current().hasRoute(RecordRoute::class))
        assertNull(handles.getValue("record").get<String>("sport"))

        // The launcher shortcuts use exactly these links (res/xml/shortcuts.xml).
        open("openactivity://record?sport=RUN")
        assertEquals("RUN", handles.getValue("record").get<String>("sport"))
        open("openactivity://record?sport=RIDE")
        assertEquals("RIDE", handles.getValue("record").get<String>("sport"))

        open("https://$HOST/record?routeId=5")
        assertEquals(5L, handles.getValue("record").get<Long>("routeId"))
    }

    @Test
    fun `a launch intent with a link builds the back stack from the feed`() {
        setUpGraph()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("openactivity://activity/99"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        compose.runOnIdle { assertTrue(nav.handleDeepLink(intent)) }
        compose.waitForIdle()
        assertEquals(99L, handles.getValue("activity").get<Long>("activityId"))
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        assertTrue("Back leads to the feed", current().hasRoute(FeedRoute::class))
    }

    @Test
    fun `unknown links are rejected`() {
        setUpGraph()
        compose.runOnIdle {
            for (link in listOf("openactivity://activity/abc", "openactivity://somewhere", "https://elsewhere.example/activities/1")) {
                try {
                    nav.navigate(Uri.parse(link))
                    fail("$link should not match")
                } catch (expected: IllegalArgumentException) {
                    // no destination
                }
            }
        }
        assertTrue(AppLinks.isAppLink(Uri.parse("openactivity://record"), HOST))
        assertTrue(AppLinks.isAppLink(Uri.parse("https://$HOST/routes/1"), HOST))
        assertFalse(AppLinks.isAppLink(Uri.parse("https://elsewhere.example/routes/1"), HOST))
        assertFalse(AppLinks.isAppLink(Uri.parse("content://downloads/run.gpx"), HOST))
    }

    @Test
    fun `shared files reach the import screen as uri strings`() {
        setUpGraph()
        val uris = listOf("content://a/run.gpx", "content://b/ride.fit")
        compose.runOnIdle { nav.navigate(ImportActivitiesRoute(uris)) }
        compose.waitForIdle()
        assertEquals(uris, ImportViewModel.sharedUris(handles.getValue("import").get<Any>(ImportViewModel.ARG_URIS)))
    }

    private companion object {
        const val HOST = "links.test"
    }
}

/** Exposes the [SavedStateHandle] a destination's view model receives. */
class HandleViewModel(val handle: SavedStateHandle) : ViewModel()
