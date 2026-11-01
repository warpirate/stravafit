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
import org.openactivity.app.testing.FakeActivityExtrasRepository
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakeFeedSeenRepository
import org.openactivity.app.testing.FakeNotificationRepository
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeProfileRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.savedActivity
import org.openactivity.app.testing.testTimeFormatter
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.activityextras.ActivityPhoto
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.model.SportType
import java.io.File
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val activities = FakeActivityRepository()
    private val profile = FakeProfileRepository(UserProfile(name = "Asha Rao", location = "Pune"))
    private val preferences = FakePreferencesRepository()
    private val extras = FakeActivityExtrasRepository()
    // Everything existing is already seen unless a test says otherwise.
    private val seen = FakeFeedSeenRepository(initial = Long.MAX_VALUE)

    private val inbox = FakeNotificationRepository()

    private fun viewModel() = FeedViewModel(activities, profile, preferences, testTimeFormatter(), extras, seen, inbox)

    private fun kotlinx.coroutines.test.TestScope.collect(vm: FeedViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `starts loading, then shows an empty Following feed when there are no activities`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)
        collect(vm)
        val state = vm.uiState.value
        assertEquals(FeedScope.Following, state.scope)
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
        assertEquals("Today at 14:50 · Pune", first.meta)
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
    fun `following feed shows your own activities until you follow someone`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1))
        val vm = viewModel()
        collect(vm)
        vm.selectScope(FeedScope.Following)
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.uiState.value.cards.map { it.id })
    }

    @Test
    fun `cards carry an achievements line for personal records`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1), savedActivity(2))
        activities.bests.value = listOf(400.0, 804.672, 1000.0).map {
            org.openactivity.core.data.model.PersonalBest(it, it / 3.0, 1L, "Run", SportType.RUN, TEST_CLOCK.instant())
        }
        val vm = viewModel()
        collect(vm)
        val byId = vm.uiState.value.cards.associateBy { it.id }
        assertEquals(Achievements(3, "1K"), byId.getValue(1L).achievements)
        assertNull(byId.getValue(2L).achievements)
    }

    @Test
    fun `the bell has a dot while the inbox has unread entries`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        assertFalse(vm.uiState.value.hasUnreadNotifications)
        val id = inbox.add(NewNotification(NotificationType.GOAL_REACHED, "Goal reached", "", NotificationTarget.GOALS, "k"))!!
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasUnreadNotifications)
        inbox.markRead(id)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasUnreadNotifications)
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

    @Test
    fun `cards carry their photos for the carousel, map first`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1), savedActivity(2))
        val photo = ActivityPhoto("p1", File("p1.jpg"), 1200, 900)
        extras.state.value = mapOf(1L to ActivityExtras(photos = listOf(photo)))
        val vm = viewModel()
        collect(vm)
        val byId = vm.uiState.value.cards.associateBy { it.id }
        assertEquals(listOf(photo), byId.getValue(1L).photos)
        assertNotNull("the map stays", byId.getValue(1L).route)
        assertTrue(byId.getValue(2L).photos.isEmpty())

        // A photo added later shows up without reloading the feed.
        val second = ActivityPhoto("p2", File("p2.jpg"), 900, 1200)
        extras.state.value = mapOf(1L to ActivityExtras(photos = listOf(photo)), 2L to ActivityExtras(photos = listOf(second)))
        advanceUntilIdle()
        assertEquals(listOf(second), vm.uiState.value.cards.single { it.id == 2L }.photos)
    }

    @Test
    fun `first feed visit counts what is already there as seen`() = runTest(main.dispatcher) {
        seen.state.value = null
        activities.activities.value = listOf(savedActivity(3), savedActivity(7))
        val vm = viewModel()
        collect(vm)
        assertEquals(7L, seen.state.value)
        assertEquals(0, vm.uiState.value.unseenCount)
        assertFalse(vm.uiState.value.cards.any { it.isNew })
    }

    @Test
    fun `unseen activities come first with a caught-up divider, then the seen ones`() = runTest(main.dispatcher) {
        val now = TEST_CLOCK.instant()
        seen.state.value = 2L
        activities.activities.value = listOf(
            savedActivity(1, "Old seen", start = now.minusSeconds(86_400 * 2)),
            savedActivity(2, "Newest seen", start = now.minusSeconds(600)),
            // Imported today but recorded a week ago: still unseen, so it goes to the top.
            savedActivity(3, "Late import", start = now.minusSeconds(86_400 * 7)),
            savedActivity(4, "Fresh", start = now.minusSeconds(3_600)),
        )
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertEquals(listOf(4L, 3L, 2L, 1L), state.cards.map { it.id })
        assertEquals(2, state.unseenCount)
        assertTrue(state.showCaughtUpDivider)
        assertEquals(listOf(true, true, false, false), state.cards.map { it.isNew })

        // The You feed is always plain chronological, without badges.
        vm.selectScope(FeedScope.You)
        advanceUntilIdle()
        assertEquals(listOf(2L, 4L, 1L, 3L), vm.uiState.value.cards.map { it.id })
        assertEquals(0, vm.uiState.value.unseenCount)
        assertFalse(vm.uiState.value.showCaughtUpDivider)
    }

    @Test
    fun `leaving the feed marks everything seen`() = runTest(main.dispatcher) {
        seen.state.value = 1L
        activities.activities.value = listOf(savedActivity(1), savedActivity(2), savedActivity(5))
        val vm = viewModel()
        collect(vm)
        assertEquals(2, vm.uiState.value.unseenCount)
        // All unseen and nothing seen: no divider between groups (the footer says it).
        seen.state.value = 0L
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showCaughtUpDivider)
        vm.markAllSeen()
        advanceUntilIdle()
        assertEquals(5L, seen.state.value)
        assertEquals(0, vm.uiState.value.unseenCount)
    }
}
