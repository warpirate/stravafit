// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.route

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.route.RouteExporter
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeRouteRepository
import org.openactivity.app.testing.FakeRouteSuggestions
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.testSuggestion
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SuggestedRouteViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val routes = FakeRouteRepository()
    private val exported = mutableListOf<Long>()
    private val exporter = object : RouteExporter {
        override suspend fun exportGpx(routeId: Long, destination: Uri) {
            exported += routeId
        }
    }
    private val id = "RUN-5000.0"

    private fun viewModel(suggestionId: String = id) = SuggestedRouteViewModel(
        SavedStateHandle(mapOf("suggestionId" to suggestionId)),
        FakeRouteSuggestions(),
        routes,
        FakePreferencesRepository(),
        exporter,
    )

    private fun TestScope.collect(vm: SuggestedRouteViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `a suggestion shows map, stats, profile and the OSM attribution, unsaved`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertEquals("Bund Garden Rd loop", state.name)
        assertEquals(SportType.RUN, state.sport)
        assertTrue(state.isSuggestion)
        assertFalse(state.isSaved)
        assertEquals(listOf("Distance", "Elev gain", "Elev loss", "Est. time"), state.stats.map { it.label })
        assertNotNull(state.elevation)
        assertTrue(state.note.contains("OpenStreetMap"))
        assertTrue(state.fileName.endsWith(".gpx"))
    }

    @Test
    fun `an unknown suggestion is not found`() = runTest(main.dispatcher) {
        val vm = viewModel("gone")
        collect(vm)
        assertTrue(vm.uiState.value.notFound)
    }

    @Test
    fun `save stores the loop once, with its elevations`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.save()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(1, routes.routes.value.size)
        assertTrue(vm.uiState.value.isSaved)
        assertEquals(routes.routes.value.single().route.id, vm.uiState.value.routeId)
        assertEquals("Saved to Your routes", vm.uiState.value.message)
    }

    @Test
    fun `record saves first and hands the saved route to the recorder`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.record()
        advanceUntilIdle()
        val saved = routes.routes.value.single().route.id
        assertEquals(saved, vm.uiState.value.recordRouteId)
        vm.recordHandled()
        vm.record()
        advanceUntilIdle()
        assertEquals(1, routes.routes.value.size)
    }

    @Test
    fun `export saves first and uses the saved-route GPX exporter`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.exportGpx(Uri.parse("content://test/loop.gpx"))
        advanceUntilIdle()
        assertEquals(listOf(routes.routes.value.single().route.id), exported)
        assertEquals("Saved to Your routes and exported as GPX", vm.uiState.value.message)
    }

    @Test
    fun `elevations are interpolated onto every route point`() {
        val route = testSuggestion("x")
        val points = SuggestedRouteViewModel.routePoints(route)
        assertEquals(route.points.size, points.size)
        assertEquals(560.0, points.first().elevationMeters!!, 0.5)
        assertTrue(points.all { it.elevationMeters != null && it.elevationMeters!! in 559.0..588.0 })
        assertTrue(points.maxOf { it.elevationMeters!! } > 570.0)
    }
}
