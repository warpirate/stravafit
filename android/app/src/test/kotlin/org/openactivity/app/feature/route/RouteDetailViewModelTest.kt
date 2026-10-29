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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.route.RouteExporter
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeRouteRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.routeDetail
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RouteDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val routes = FakeRouteRepository(listOf(routeDetail(5, "Hill loop")))
    private val exported = mutableListOf<Pair<Long, Uri>>()
    private var failExport = false
    private val exporter = object : RouteExporter {
        override suspend fun exportGpx(routeId: Long, destination: Uri) {
            if (failExport) throw IOException("disk full")
            exported += routeId to destination
        }
    }

    private fun viewModel(id: Long = 5) =
        RouteDetailViewModel(SavedStateHandle(mapOf("routeId" to id)), routes, FakePreferencesRepository(), exporter)

    private fun TestScope.collect(vm: RouteDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `shows the route with stats, map points and elevation profile`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertEquals("Hill loop", state.name)
        assertEquals(listOf("Distance", "Elev gain", "Elev loss", "Est. time"), state.stats.map { it.label })
        assertEquals("~51m", state.stats.last().value)
        assertEquals(7, state.points.size)
        assertNotNull(state.elevation)
        val profile = state.elevation!!
        assertEquals(0f, profile.points.first().first)
        assertEquals(1f, profile.points.last().first)
        assertEquals("Hill_loop.gpx", state.fileName)
    }

    @Test
    fun `missing route shows not found`() = runTest(main.dispatcher) {
        val vm = viewModel(99)
        collect(vm)
        assertTrue(vm.uiState.value.notFound)
    }

    @Test
    fun `export writes GPX and reports the result`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        val uri = Uri.parse("content://docs/hill-loop.gpx")
        vm.exportGpx(uri)
        advanceUntilIdle()
        assertEquals(listOf(5L to uri), exported)
        assertEquals("Route exported as GPX", vm.uiState.value.message)
        failExport = true
        vm.exportGpx(uri)
        advanceUntilIdle()
        assertEquals("Export failed: disk full", vm.uiState.value.message)
    }

    @Test
    fun `delete removes the route and closes the screen`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deleted)
        assertTrue(routes.routes.value.isEmpty())
        assertTrue(!vm.uiState.value.notFound)
    }
}
