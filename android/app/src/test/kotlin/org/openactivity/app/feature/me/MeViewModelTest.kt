// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.data.SampleFeedRepository
import org.openactivity.app.data.SampleProfileRepository
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.Sport
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.testTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel() = MeViewModel(
        SampleProfileRepository(),
        SampleFeedRepository(TEST_CLOCK),
        FakePreferencesRepository(),
        testTimeFormatter(),
    )

    @Test
    fun `loads header and defaults to the first sport`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Kavya Rao", state.header?.name)
        assertEquals("198", state.header?.followers)
        assertEquals(Sport.Run, state.selectedSport)
        assertEquals(listOf("Distance", "Time", "Elev gain"), state.thisWeek.map { it.label })
        assertEquals("24.10", state.thisWeek.first().value)
        assertEquals(12, state.weeklyChart?.fractions?.size)
    }

    @Test
    fun `switching sport updates totals`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectSport(Sport.Ride)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(Sport.Ride, state.selectedSport)
        assertEquals("38", state.yearToDate.first { it.label == "Activities" }.value)
    }

    @Test
    fun `activities tab lists own activities newest first`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectTab(MeTab.Activities)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MeTab.Activities, state.tab)
        assertEquals(listOf("act-102", "act-106", "act-107"), state.activities.map { it.id })
        assertEquals("10.20 km · 5:12/km · 53:04", state.activities.first().statsLine)
        assertEquals("Only you", state.activities[1].visibilityLabel)
    }

    @Test
    fun `weekly chart normalises to the busiest week`() {
        val chart = MeViewModel.weeklyChart(listOf(0.0, 5_000.0, 10_000.0), UnitFormatter(UnitSystem.Metric, Locale.UK))
        assertEquals(listOf(0f, 0.5f, 1f), chart.fractions)
        assertEquals("10.00 km", chart.maxLabel)
        assertTrue(chart.summary.contains("This week 10.00 kilometres"))
    }

    @Test
    fun `totals show a dash for unknown distance`() {
        val stats = MeViewModel.totalsStats(
            org.openactivity.app.data.SportTotals(count = 4, distanceMeters = null, movingTimeSeconds = 3_600, elevationMeters = null),
            UnitFormatter(UnitSystem.Metric, Locale.UK),
            includeCount = true,
        )
        assertEquals("4", stats.first().value)
        assertEquals(null, stats.first { it.label == "Distance" }.value)
        assertNotNull(stats.first { it.label == "Time" }.value)
    }
}
