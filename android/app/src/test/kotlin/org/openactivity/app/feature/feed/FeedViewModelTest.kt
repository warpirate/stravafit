// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.model.UserProfile
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeProfileRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.savedActivity
import org.openactivity.app.testing.testTimeFormatter
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val activities = FakeActivityRepository()
    private val profile = FakeProfileRepository(UserProfile(name = "Asha Rao", location = "Pune"))
    private val preferences = FakePreferencesRepository()

    private fun viewModel() = FeedViewModel(activities, profile, preferences, testTimeFormatter())

    private fun kotlinx.coroutines.test.TestScope.collect(vm: FeedViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `starts loading, then shows an empty You feed when there are no activities`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)
        collect(vm)
        val state = vm.uiState.value
        assertEquals(FeedScope.You, state.scope)
        assertTrue(state.isEmpty)
        assertFalse(state.hasAnyActivity)
    }

    @Test
    fun `cards are your real activities newest first with profile name and stats`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(
            savedActivity(1, "Older run", start = TEST_CLOCK.instant().minusSeconds(86_400 * 3)),
            savedActivity(2, "Fresh run", start = TEST_CLOCK.instant().minusSeconds(600), visibility = Visibility.ONLY_ME),
        )
        val vm = viewModel()
        collect(vm)
        val cards = vm.uiState.value.cards
        assertEquals(listOf(2L, 1L), cards.map { it.id })
        val first = cards.first()
        assertEquals("Asha Rao", first.athleteName)
        assertEquals("10 min ago · Pune", first.meta)
        assertEquals("Only you", first.visibilityLabel)
        assertEquals(listOf("8.02", "5:12", "41:40"), first.stats.map { it.value })
        assertNotNull(first.route)
        assertEquals(0, first.highFiveCount)
    }

    @Test
    fun `indoor activities have no route and show time-based stats`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1, "Gym", sport = SportType.WEIGHT_TRAINING, distanceMeters = 0.0, track = null))
        val vm = viewModel()
        collect(vm)
        val card = vm.uiState.value.cards.single()
        assertNull(card.route)
        assertEquals(listOf("Time", "Avg HR", "Elapsed"), card.stats.map { it.label })
    }

    @Test
    fun `units follow the preference`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1))
        val vm = viewModel()
        collect(vm)
        preferences.state.value = UserPreferences(unitSystem = UnitSystem.IMPERIAL)
        advanceUntilIdle()
        assertEquals("mi", vm.uiState.value.cards.single().stats.first().unit)
    }

    @Test
    fun `following feed is empty without a server account`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1))
        val vm = viewModel()
        collect(vm)
        vm.selectScope(FeedScope.Following)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
        assertTrue(vm.uiState.value.hasAnyActivity)
    }

    @Test
    fun `sport filter narrows the cards and offers only sports you did`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1), savedActivity(2, "Commute", sport = SportType.RIDE))
        val vm = viewModel()
        collect(vm)
        assertEquals(listOf(SportType.RUN, SportType.RIDE), vm.uiState.value.sports)
        vm.selectSport(SportType.RIDE)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.uiState.value.cards.map { it.id })
        assertEquals(SportType.RIDE, vm.uiState.value.sportFilter)
    }

    @Test
    fun `delete removes the activity and reports it`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1), savedActivity(2))
        val vm = viewModel()
        collect(vm)
        vm.delete(1)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.uiState.value.cards.map { it.id })
        assertEquals("Activity deleted", vm.uiState.value.message)
        vm.messageShown()
        advanceUntilIdle()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `profile without a name shows You`() = runTest(main.dispatcher) {
        profile.state.value = UserProfile()
        activities.activities.value = listOf(savedActivity(1))
        val vm = viewModel()
        collect(vm)
        assertEquals("You", vm.uiState.value.cards.single().athleteName)
        assertEquals("Today at 14:00", vm.uiState.value.cards.single().meta)
    }
}
