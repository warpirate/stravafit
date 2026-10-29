// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.route.RouteImportSummary
import org.openactivity.app.data.route.RouteImporter
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeRouteRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.routeDetail
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExploreViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val routes = FakeRouteRepository()

    /** Imports a two-point route per uri, failing uris whose path contains "bad". */
    private val importer = object : RouteImporter {
        override suspend fun import(uris: List<Uri>): RouteImportSummary {
            val ids = uris.filterNot { "bad" in it.toString() }.map {
                routes.importRoute(it.lastPathSegment!!, listOf(RoutePointInput(18.52, 73.85), RoutePointInput(18.53, 73.86)))
            }
            val failures = uris.filter { "bad" in it.toString() }.map { "${it.lastPathSegment}: Not a GPX, TCX or FIT file" }
            return RouteImportSummary(ids, failures)
        }
    }

    private fun viewModel() = ExploreViewModel(routes, FakePreferencesRepository(), importer)

    private fun TestScope.collect(vm: ExploreViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `no routes gives an empty list and nothing on the map`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.routes.isEmpty())
        assertEquals(null, vm.uiState.value.selectedRoute)
    }

    @Test
    fun `saved routes become cards with distance, climb and estimated time`() = runTest(main.dispatcher) {
        routes.routes.value = listOf(routeDetail(1), routeDetail(2, "Lake spin", sport = SportType.RIDE, distanceMeters = 32_000.0, elevationGain = 210.0))
        val vm = viewModel()
        collect(vm)
        val cards = vm.uiState.value.routes
        assertEquals("8.20 km · 45 m · ~51m", cards[0].details)
        assertEquals("32.00 km · 210 m · ~1h 35m", cards[1].details)
        assertEquals(1L, vm.uiState.value.selectedRoute?.id)
        vm.selectRoute(2)
        advanceUntilIdle()
        assertEquals(2L, vm.uiState.value.selectedRoute?.id)
    }

    @Test
    fun `import saves routes, highlights the first and reports failures`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.importRoutes(listOf(Uri.parse("content://docs/Loop.gpx"), Uri.parse("content://docs/bad.txt")))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.routes.size)
        assertEquals(state.routes.single().id, state.selectedRouteId)
        assertEquals("Imported 1 route. bad.txt: Not a GPX, TCX or FIT file", state.message)
        assertFalse(state.isImporting)
        vm.messageShown()
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.message)
    }

    @Test
    fun `route estimates depend on the sport`() {
        assertEquals(2_952L + 135L, RouteEstimates.estimatedSeconds(8_200.0, 45.0, SportType.RUN))
        assertEquals(3_600L, RouteEstimates.estimatedSeconds(5_000.0, 0.0, SportType.WALK))
        assertEquals(0L, RouteEstimates.estimatedSeconds(0.0, 10.0, null))
    }
}
