// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.suggest

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
import org.openactivity.app.data.SuggestionSettingsState
import org.openactivity.app.model.Sport
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.testing.FakeCurrentLocation
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeRouteSuggestions
import org.openactivity.app.testing.FakeSuggestionSettings
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.PUNE
import org.openactivity.app.testing.testSuggestion
import org.openactivity.core.location.CurrentLocationResult
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.routing.SuggestionSport
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RouteSuggestionsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val suggestions = FakeRouteSuggestions()
    private val location = FakeCurrentLocation()
    private val settings = FakeSuggestionSettings()
    private val preferences = FakePreferencesRepository()

    private fun viewModel() = RouteSuggestionsViewModel(suggestions, location, settings, preferences)

    private fun TestScope.collect(vm: RouteSuggestionsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `loops for the default sport's distances become cards with stats`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertEquals(SuggestionsStatus.Ready, state.status)
        assertEquals(SuggestionSport.RUN, state.sport)
        assertEquals(3, state.cards.size)
        assertFalse(state.isLoadingMore)
        val card = state.cards[1]
        assertEquals("Bund Garden Rd loop", card.name)
        assertEquals("Run · 5 km loop · NE", card.subtitle)
        assertEquals(listOf("Distance", "Elev Gain", "Est. Time"), card.stats.map { it.label })
        assertEquals("5.20", card.stats[0].value)
        assertEquals("38", card.stats[1].value)
        assertEquals(PUNE, suggestions.requests.single().first.start)
        assertEquals(listOf(3_000.0, 5_000.0, 10_000.0), suggestions.requests.single().first.distancesMeters)
    }

    @Test
    fun `the athlete's default sport picks the suggestion sport`() = runTest(main.dispatcher) {
        preferences.state.value = UserPreferences(lastSport = Sport.GravelRide)
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionSport.RIDE, vm.uiState.value.sport)
        assertEquals(listOf(20_000.0, 40_000.0), suggestions.requests.single().first.distancesMeters)
    }

    @Test
    fun `unknown climb shows a dash, not zero`() = runTest(main.dispatcher) {
        suggestions.routes = { r, d -> listOf(testSuggestion("x$d", r.sport, d, gain = null)) }
        val vm = viewModel()
        collect(vm)
        assertEquals(null, vm.uiState.value.cards.first().stats[1].value)
    }

    @Test
    fun `loading while the first loops are routed`() = runTest(main.dispatcher) {
        assertEquals(SuggestionsStatus.Loading, viewModel().uiState.value.status)
    }

    @Test
    fun `without permission the section asks for location and routes nothing`() = runTest(main.dispatcher) {
        location.result = CurrentLocationResult.Unavailable(LocationAvailability.NO_PERMISSION)
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionsStatus.NoLocation(LocationAvailability.NO_PERMISSION), vm.uiState.value.status)
        assertTrue(suggestions.requests.isEmpty())
        // Granted in the system dialog: back on screen, it loads.
        location.result = CurrentLocationResult.Found(PUNE)
        vm.onResume()
        advanceUntilIdle()
        assertEquals(SuggestionsStatus.Ready, vm.uiState.value.status)
    }

    @Test
    fun `no fix and location off are told apart`() = runTest(main.dispatcher) {
        location.result = CurrentLocationResult.NoFix
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionsStatus.NoLocation(null), vm.uiState.value.status)
        location.result = CurrentLocationResult.Unavailable(LocationAvailability.DISABLED)
        vm.retry()
        advanceUntilIdle()
        assertEquals(SuggestionsStatus.NoLocation(LocationAvailability.DISABLED), vm.uiState.value.status)
    }

    @Test
    fun `offline shows a quiet retry state and never invents routes`() = runTest(main.dispatcher) {
        suggestions.failed = true
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionsStatus.Offline, vm.uiState.value.status)
        assertTrue(vm.uiState.value.cards.isEmpty())
        suggestions.failed = false
        vm.retry()
        advanceUntilIdle()
        assertEquals(SuggestionsStatus.Ready, vm.uiState.value.status)
    }

    @Test
    fun `nothing routable around here is its own state`() = runTest(main.dispatcher) {
        suggestions.routes = { _, _ -> emptyList() }
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionsStatus.NoneFound, vm.uiState.value.status)
    }

    @Test
    fun `disabled sends nothing and turning off from the intro disables`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        assertTrue(vm.uiState.value.showIntro)
        vm.setEnabled(false)
        advanceUntilIdle()
        assertEquals(SuggestionsStatus.Disabled, vm.uiState.value.status)
        assertFalse(vm.uiState.value.showIntro)
        val calls = suggestions.requests.size
        vm.refresh()
        advanceUntilIdle()
        assertEquals(calls, suggestions.requests.size)
    }

    @Test
    fun `starting disabled routes nothing at all`() = runTest(main.dispatcher) {
        settings.value.value = SuggestionSettingsState(enabled = false, introSeen = true)
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionsStatus.Disabled, vm.uiState.value.status)
        assertTrue(suggestions.requests.isEmpty())
    }

    @Test
    fun `the intro goes once dismissed`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.dismissIntro()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showIntro)
        assertEquals(SuggestionsStatus.Ready, vm.uiState.value.status)
    }

    @Test
    fun `sport and distance chips and refresh change the request`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.selectSport(SuggestionSport.WALK)
        advanceUntilIdle()
        assertEquals(SuggestionSport.WALK, suggestions.requests.last().first.sport)
        assertEquals(2, vm.uiState.value.cards.size)
        vm.selectDistance(4_000.0)
        advanceUntilIdle()
        assertEquals(listOf(4_000.0), suggestions.requests.last().first.distancesMeters)
        assertEquals(1, vm.uiState.value.cards.size)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, suggestions.requests.last().second)
    }
}
