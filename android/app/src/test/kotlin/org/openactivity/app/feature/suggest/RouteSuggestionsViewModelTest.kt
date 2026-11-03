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
import org.openactivity.core.routing.SurfaceMix
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RouteSuggestionsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val suggestions = FakeRouteSuggestions()
    private val location = FakeCurrentLocation()
    private val settings = FakeSuggestionSettings(SuggestionSettingsState(introSeen = true))
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
        settings.value.value = SuggestionSettingsState(introSeen = false)
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
        settings.value.value = SuggestionSettingsState(introSeen = false)
        val vm = viewModel()
        collect(vm)
        vm.dismissIntro()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showIntro)
        assertEquals(SuggestionsStatus.Ready, vm.uiState.value.status)
    }

    @Test
    fun `sport, length and refresh change the request`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.selectSport(SuggestionSport.WALK)
        advanceUntilIdle()
        assertEquals(SuggestionSport.WALK, suggestions.requests.last().first.sport)
        assertEquals(2, vm.uiState.value.cards.size)
        vm.setLength(4_000.0..4_000.0)
        advanceUntilIdle()
        assertEquals(listOf(4_000.0), suggestions.requests.last().first.distancesMeters)
        assertEquals(1, vm.uiState.value.cards.size)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, suggestions.requests.last().second)
    }

    @Test
    fun `nothing is requested before the intro is accepted`() = runTest(main.dispatcher) {
        settings.value.value = SuggestionSettingsState(introSeen = false)
        val vm = viewModel()
        collect(vm)
        assertEquals(SuggestionsStatus.AwaitingConsent, vm.uiState.value.status)
        assertTrue(vm.uiState.value.showIntro)
        assertTrue(suggestions.requests.isEmpty())
        vm.dismissIntro()
        advanceUntilIdle()
        assertEquals(1, suggestions.requests.size)
    }

    @Test
    fun `only the load a refresh starts bypasses the cache`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.refresh()
        advanceUntilIdle()
        vm.setLength(4_000.0..4_000.0)
        advanceUntilIdle()
        vm.retry()
        advanceUntilIdle()
        assertEquals(listOf(false, true, false, false), suggestions.bypassed)
    }

    @Test
    fun `elevation and surface filter what was found without routing again`() = runTest(main.dispatcher) {
        suggestions.routes = { r, d ->
            listOf(
                testSuggestion("flat-$d", r.sport, d, gain = 10.0, surface = SurfaceMix(5_000.0, 0.0, 0.0)),
                testSuggestion("hilly-$d", r.sport, d, gain = 200.0, surface = SurfaceMix(1_000.0, 4_000.0, 0.0)),
            )
        }
        val vm = viewModel()
        collect(vm)
        val requests = suggestions.requests.size
        assertEquals(6, vm.uiState.value.cards.size)

        vm.setElevation(ElevationFilter.HILLY)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.cards.all { it.id.startsWith("hilly") })
        assertEquals(3, vm.uiState.value.hiddenByFilters)

        vm.setSurface(SurfaceFilter.PAVED)
        advanceUntilIdle()
        assertEquals(SuggestionsStatus.NoneFound, vm.uiState.value.status)
        assertEquals(6, vm.uiState.value.hiddenByFilters)

        vm.clearFilters()
        advanceUntilIdle()
        assertEquals(6, vm.uiState.value.cards.size)
        assertEquals("filters alone never route again", requests, suggestions.requests.size)
    }

    @Test
    fun `a loop with no surface yet waits for it, then shows only once known`() {
        val pending = testSuggestion("a")
        val paved = testSuggestion("b", surface = SurfaceMix(4_900.0, 0.0, 100.0))
        val filters = SuggestionFilters(surface = SurfaceFilter.PAVED)
        assertFalse(RouteSuggestionsViewModel.matches(pending, filters))
        assertTrue(RouteSuggestionsViewModel.matches(paved, filters))
        assertTrue(RouteSuggestionsViewModel.matches(pending, SuggestionFilters()))
        val formatter = org.openactivity.app.format.UnitFormatter(org.openactivity.model.UnitSystem.METRIC, java.util.Locale.UK)
        assertEquals("Checking surface…", RouteSuggestionsViewModel.card(pending, formatter, surfacePending = true).surface)
        assertEquals(null, RouteSuggestionsViewModel.card(pending, formatter, surfacePending = false).surface)
        assertEquals("Paved 98%", RouteSuggestionsViewModel.card(paved, formatter).surface)
    }

    @Test
    fun `length range becomes target lengths and a tolerance`() {
        assertEquals(listOf(2_000.0, 5_000.0, 8_000.0), RouteSuggestionsViewModel.targets(2_000.0..8_000.0))
        assertEquals(listOf(5_000.0), RouteSuggestionsViewModel.targets(5_000.0..5_000.0))
        assertEquals(listOf(5_000.0, 5_500.0), RouteSuggestionsViewModel.targets(5_000.0..5_600.0).distinct())
        val within = SuggestionFilters(lengthMeters = 4_000.0..4_000.0)
        assertTrue(RouteSuggestionsViewModel.matches(testSuggestion("a", target = 4_400.0 / 1.04), within))
        assertFalse(RouteSuggestionsViewModel.matches(testSuggestion("b", target = 5_000.0), within))
    }

    @Test
    fun `surface labels`() {
        assertEquals("Paved 90%", RouteSuggestionsViewModel.surfaceLabel(SurfaceMix(900.0, 50.0, 50.0)))
        assertEquals("Mixed · 30% dirt", RouteSuggestionsViewModel.surfaceLabel(SurfaceMix(600.0, 300.0, 100.0)))
        assertEquals("Mostly dirt · 80%", RouteSuggestionsViewModel.surfaceLabel(SurfaceMix(200.0, 800.0, 0.0)))
        assertEquals("Paved 50% · rest unknown", RouteSuggestionsViewModel.surfaceLabel(SurfaceMix(500.0, 0.0, 500.0)))
    }

    @Test
    fun `lengths read in the athlete's units`() {
        assertEquals("5 km", RouteSuggestionsViewModel.formatLength(5_000.0, org.openactivity.model.UnitSystem.METRIC))
        assertEquals("3.1 mi", RouteSuggestionsViewModel.formatLength(5_000.0, org.openactivity.model.UnitSystem.IMPERIAL))
        assertEquals("1.9–6.2 mi", lengthLabel(3_000.0..10_000.0, org.openactivity.model.UnitSystem.IMPERIAL))
        assertEquals("3–10 km", lengthLabel(3_000.0..10_000.0, org.openactivity.model.UnitSystem.METRIC))
    }
}
