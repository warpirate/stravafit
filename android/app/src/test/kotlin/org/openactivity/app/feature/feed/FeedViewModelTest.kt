// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.data.SampleFeedRepository
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.testTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val repository = SampleFeedRepository(TEST_CLOCK)
    private fun viewModel() = FeedViewModel(repository, preferences, testTimeFormatter())

    @Test
    fun `starts loading then shows newest first`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FeedScope.Following, state.scope)
        assertEquals("act-101", state.cards.first().id)
        assertEquals("48 min ago · Pune", state.cards.first().meta)
    }

    @Test
    fun `you scope shows only own activities`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectScope(FeedScope.You)
        advanceUntilIdle()

        val cards = vm.uiState.value.cards
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { it.isOwn })
        assertEquals(FeedScope.You, vm.uiState.value.scope)
    }

    @Test
    fun `high-five toggles optimistically and can be undone`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        val before = vm.uiState.value.cards.first { it.id == "act-103" }

        vm.toggleHighFive("act-103")
        advanceUntilIdle()
        val given = vm.uiState.value.cards.first { it.id == "act-103" }
        assertTrue(given.highFiveGiven)
        assertEquals(before.highFiveCount + 1, given.highFiveCount)

        vm.toggleHighFive("act-103")
        advanceUntilIdle()
        val undone = vm.uiState.value.cards.first { it.id == "act-103" }
        assertFalse(undone.highFiveGiven)
        assertEquals(before.highFiveCount, undone.highFiveCount)
    }

    @Test
    fun `own activities cannot be high-fived`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        val own = vm.uiState.value.cards.first { it.isOwn }

        vm.toggleHighFive(own.id)
        advanceUntilIdle()
        assertEquals(own, vm.uiState.value.cards.first { it.id == own.id })
    }

    @Test
    fun `stats follow the unit preference`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("km", vm.uiState.value.cards.first().stats.first().unit)

        preferences.state.value = UserPreferences(unitSystem = UnitSystem.Imperial)
        advanceUntilIdle()
        val distance = vm.uiState.value.cards.first().stats.first()
        assertEquals("mi", distance.unit)
        assertEquals("4.98", distance.value)
    }

    @Test
    fun `indoor activities have no route and show time first`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        val strength = vm.uiState.value.cards.first { it.id == "act-104" }
        assertNull(strength.route)
        assertEquals(listOf("Time", "Avg HR", "Calories"), strength.stats.map { it.label })
    }

    @Test
    fun `own non-public activities carry a visibility label`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("Followers", vm.uiState.value.cards.first { it.id == "act-102" }.visibilityLabel)
        assertNull(vm.uiState.value.cards.first { it.id == "act-101" }.visibilityLabel)
    }

    @Test
    fun `with line names two companions then counts the rest`() {
        assertNull(FeedViewModel.withLine(emptyList()))
        assertEquals("with Sam", FeedViewModel.withLine(listOf("Sam Okafor")))
        assertEquals("with Sam, Li +1 other", FeedViewModel.withLine(listOf("Sam Okafor", "Li Wei", "Mira Holm")))
        assertEquals("with Sam, Li +2 others", FeedViewModel.withLine(listOf("Sam Okafor", "Li Wei", "Mira Holm", "Ana Ruiz")))
    }

    @Test
    fun `achievement text summarises count and top effort`() {
        assertNull(FeedViewModel.achievementText(0, "anything"))
        assertEquals("2nd best 5K", FeedViewModel.achievementText(1, "2nd best 5K"))
        assertEquals("3 achievements · Longest run", FeedViewModel.achievementText(3, "Longest run"))
        assertEquals("2 achievements", FeedViewModel.achievementText(2, null))
    }
}
