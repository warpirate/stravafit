// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.UserProfile
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeProfileRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.savedActivity
import org.openactivity.app.testing.testTimeFormatter
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val activities = FakeActivityRepository()
    private val profile = FakeProfileRepository(UserProfile(name = "Asha Rao", location = "Pune"))

    private fun viewModel() = MeViewModel(activities, profile, FakePreferencesRepository(), testTimeFormatter(), TEST_CLOCK)

    private fun TestScope.collect(vm: MeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun totals(count: Int, km: Double, seconds: Long, gain: Double = 0.0) =
        StatTotals(count, km * 1000, seconds.seconds, seconds.seconds, gain)

    @Test
    fun `empty device shows the header and no progress`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasAnyActivity)
        assertEquals("Asha Rao", state.header?.name)
        assertEquals("No activities yet", state.header?.summary)
    }

    @Test
    fun `progress combines weekly, yearly and all-time totals per sport`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1), savedActivity(2, "Ride", sport = SportType.RIDE))
        activities.allTime.value = mapOf(SportType.RUN to totals(3, 24.0, 7_200, 120.0), SportType.RIDE to totals(1, 30.0, 3_600, 300.0))
        activities.year.value = YearTotals(2026, totals(4, 54.0, 10_800), mapOf(SportType.RUN to totals(3, 24.0, 7_200)))
        activities.weekly.value = activities.weekly.value.mapIndexed { i, w -> if (i == 11) w.copy(totals = totals(2, 16.0, 5_000)) else w }
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertEquals(listOf(SportType.RUN, SportType.RIDE), state.sports)
        assertEquals("4 activities · 54.00 km all time", state.header?.summary)
        assertEquals(2026, state.year)
        assertEquals("4", state.yearToDate.first { it.label == "Activities" }.value)
        assertEquals("16.00", state.thisWeek.first { it.label == "Distance" }.value)
        assertEquals(1f, state.weeklyChart!!.fractions.last())
        assertEquals("Distance", state.weeklyChart!!.metric)

        vm.selectSport(SportType.RUN)
        advanceUntilIdle()
        assertEquals(SportType.RUN, vm.uiState.value.selectedSport)
        assertEquals("3", vm.uiState.value.allTime.first { it.label == "Activities" }.value)
        assertEquals(setOf(SportType.RUN), activities.weeklyRequests.last())
    }

    @Test
    fun `indoor sports show time only`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(1, "Yoga", sport = SportType.YOGA, distanceMeters = 0.0, track = null))
        activities.allTime.value = mapOf(SportType.YOGA to totals(1, 0.0, 3_600))
        val vm = viewModel()
        collect(vm)
        vm.selectSport(SportType.YOGA)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(listOf("Activities", "Time"), state.allTime.map { it.label })
        assertEquals("Time", state.weeklyChart?.metric)
        assertNull(state.bestEffortsTitle)
    }

    @Test
    fun `best efforts are labelled and open their activity`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(savedActivity(7))
        activities.allTime.value = mapOf(SportType.RUN to totals(1, 8.0, 2_500))
        activities.bests.value = listOf(
            PersonalBest(5_000.0, 1_495.0, 7, "Morning Run", SportType.RUN, TEST_CLOCK.instant()),
            PersonalBest(21_097.5, 6_300.4, 7, "Morning Run", SportType.RUN, TEST_CLOCK.instant()),
        )
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertEquals("Best efforts", state.bestEffortsTitle)
        assertEquals(listOf("5K" to "24:55", "Half marathon" to "1:45:00"), state.bestEfforts.map { it.label to it.time })
        assertEquals(7L, state.bestEfforts.first().activityId)
        assertTrue(activities.bestRequests.last().contains(SportType.TRAIL_RUN))
    }

    @Test
    fun `activities list filters by title and sport`() = runTest(main.dispatcher) {
        activities.activities.value = listOf(
            savedActivity(1, "Hill repeats"),
            savedActivity(2, "Lake ride", sport = SportType.RIDE),
            savedActivity(3, "Easy jog", description = "hills after work"),
        )
        val vm = viewModel()
        collect(vm)
        vm.selectTab(MeTab.Activities)
        vm.search("hill")
        advanceUntilIdle()
        assertEquals(listOf(1L, 3L), vm.uiState.value.activities.map { it.id }.sorted())
        vm.search("")
        vm.filterActivities(SportType.RIDE)
        advanceUntilIdle()
        val ride = vm.uiState.value.activities.single()
        assertEquals(2L, ride.id)
        assertEquals("8.02 km · 45 m · 41:40", ride.statsLine)
    }

    @Test
    fun `effort labels and times`() {
        val f = UnitFormatter(UnitSystem.METRIC, Locale.UK)
        assertEquals("1 mile", ProgressFormat.effortLabel(1609.344, f))
        assertEquals("Marathon", ProgressFormat.effortLabel(42_195.0, f))
        assertEquals("7.50 km", ProgressFormat.effortLabel(7_500.0, f))
        assertEquals("3:05", ProgressFormat.effortTime(185.4))
        assertEquals("2:00:01", ProgressFormat.effortTime(7_201.0))
    }
}
