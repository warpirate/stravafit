// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.data.SampleCommunityRepository
import org.openactivity.app.data.SampleRouteRepository
import org.openactivity.app.feature.explore.ExploreFilter
import org.openactivity.app.feature.explore.ExploreViewModel
import org.openactivity.app.feature.groups.GroupsTab
import org.openactivity.app.feature.groups.GroupsViewModel
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceholderTabsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `explore filters routes by sport group`() = runTest(main.dispatcher) {
        val vm = ExploreViewModel(SampleRouteRepository(), FakePreferencesRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.routes.size)
        assertEquals("8.20 km · 45 m · ~48m", vm.uiState.value.routes.first().details)

        vm.selectFilter(ExploreFilter.Ride)
        advanceUntilIdle()
        assertEquals(listOf("Lake Spin"), vm.uiState.value.routes.map { it.name })
    }

    @Test
    fun `groups splits joined and suggested clubs`() = runTest(main.dispatcher) {
        val vm = GroupsViewModel(SampleCommunityRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.myClubs.all { it.joined })
        assertTrue(state.suggestedClubs.none { it.joined })
        assertEquals(4, state.myClubs.size + state.suggestedClubs.size)

        vm.selectTab(GroupsTab.Challenges)
        advanceUntilIdle()
        assertEquals(GroupsTab.Challenges, vm.uiState.value.tab)
    }
}
