// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.goals

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.testing.FakeGoalRepository
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val goals = FakeGoalRepository()

    private fun viewModel(newGoal: Boolean = false, units: UnitSystem = UnitSystem.METRIC, handle: SavedStateHandle? = null) = GoalsViewModel(
        handle ?: SavedStateHandle(mapOf("newGoal" to newGoal)),
        goals,
        FakePreferencesRepository(UserPreferences(unitSystem = units)),
    )

    private fun TestScope.collect(vm: GoalsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `goals are listed with their progress, status, days left and pace`() = runTest(main.dispatcher) {
        goals.goals.value = listOf(
            Goal(id = 1, sport = SportType.RUN, period = GoalPeriod.WEEK, metric = GoalMetric.DISTANCE, target = 30_000.0),
            Goal(id = 2, sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.COUNT, target = 4.0),
        )
        goals.current.value = mapOf(1L to 24_100.0, 2L to 1.0)
        goals.streak.value = WeeklyStreak(3, activeThisWeek = true)
        val vm = viewModel()
        collect(vm)
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("3 week streak", state.streak?.title)
        assertEquals("Active this week", state.streak?.subtitle)
        val run = state.goals[0]
        assertEquals("Weekly run goal", run.title)
        assertEquals("Distance · this week", run.subtitle)
        assertEquals("24.10 / 30.00 km", run.ofTarget)
        assertEquals(0.8033f, run.fraction, 0.001f)
        assertEquals("5.90 km to go", run.status)
        assertEquals("4 days left", run.daysLeft)
        assertEquals("On pace", run.pace)
        val count = state.goals[1]
        assertEquals("Weekly goal", count.title)
        assertEquals("Behind pace", count.pace)
        assertFalse(count.onPace)
        assertNull(state.editor)
    }

    @Test
    fun `a reached goal shows no pace and a full bar`() = runTest(main.dispatcher) {
        goals.goals.value = listOf(Goal(id = 1, sport = null, period = GoalPeriod.MONTH, metric = GoalMetric.TIME, target = 3_600.0))
        goals.current.value = mapOf(1L to 5_400.0)
        val vm = viewModel()
        collect(vm)
        val goal = vm.uiState.value.goals.single()
        assertTrue(goal.reached)
        assertEquals("Goal reached", goal.status)
        assertNull(goal.pace)
        assertEquals(1f, goal.fraction)
    }

    @Test
    fun `new goal argument opens the editor once`() = runTest(main.dispatcher) {
        val handle = SavedStateHandle(mapOf("newGoal" to true))
        val vm = viewModel(handle = handle)
        collect(vm)
        val editor = vm.uiState.value.editor
        assertNotNull(editor)
        editor!!
        assertTrue(editor.isNew)
        assertEquals(GoalPeriod.WEEK, editor.period)
        assertEquals("km", editor.unit)
        assertFalse(editor.canSave)

        // Recreated (rotation, process death): the flag is spent.
        val again = viewModel(handle = handle)
        collect(again)
        assertNull(again.uiState.value.editor)
        assertNull(viewModel().also { collect(it) }.uiState.value.editor)
    }

    @Test
    fun `target validation`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.addGoal()
        fun editor() = vm.uiState.value.editor!!

        assertNull(editor().error)
        assertFalse(editor().canSave)
        for ((text, error) in listOf("abc" to "Enter a number", "0" to "Enter a target above 0", "-5" to "Enter a target above 0", "Infinity" to "Enter a number")) {
            vm.setTarget(text)
            advanceUntilIdle()
            assertEquals(text, error, editor().error)
            assertFalse(text, editor().canSave)
        }
        vm.setTarget("12,5")
        advanceUntilIdle()
        assertNull(editor().error)
        assertTrue(editor().canSave)

        vm.setMetric(GoalMetric.COUNT)
        vm.setTarget("2.5")
        advanceUntilIdle()
        assertEquals("Use a whole number of activities", editor().error)
        assertFalse(editor().canSave)
        vm.setTarget("3")
        advanceUntilIdle()
        assertTrue(editor().canSave)
        assertEquals("activities", editor().unit)
    }

    @Test
    fun `saving converts the shown target to stored units`() = runTest(main.dispatcher) {
        val vm = viewModel(units = UnitSystem.IMPERIAL)
        collect(vm)
        vm.addGoal()
        vm.setPeriod(GoalPeriod.MONTH)
        vm.setSport(SportType.RIDE)
        vm.setTarget("10")
        advanceUntilIdle()
        assertEquals("mi", vm.uiState.value.editor?.unit)
        vm.save()
        advanceUntilIdle()
        val saved = goals.saved.single()
        assertEquals(16_093.44, saved.target, 1e-6)
        assertEquals(GoalPeriod.MONTH, saved.period)
        assertEquals(SportType.RIDE, saved.sport)
        assertEquals(0L, saved.id)
        assertNull(vm.uiState.value.editor)
        assertEquals("Goal added", vm.uiState.value.message)

        vm.addGoal()
        vm.setMetric(GoalMetric.TIME)
        vm.setTarget("1.5")
        vm.save()
        advanceUntilIdle()
        assertEquals(5_400.0, goals.saved.last().target, 1e-9)
        assertEquals(GoalMetric.TIME, goals.saved.last().metric)
    }

    @Test
    fun `editing keeps an untouched target exact and saves changes`() = runTest(main.dispatcher) {
        goals.goals.value = listOf(Goal(id = 7, sport = null, period = GoalPeriod.YEAR, metric = GoalMetric.DISTANCE, target = 1_000_000.0))
        val vm = viewModel(units = UnitSystem.IMPERIAL)
        collect(vm)
        vm.editGoal(7)
        advanceUntilIdle()
        val editor = vm.uiState.value.editor!!
        assertFalse(editor.isNew)
        assertEquals("621.37", editor.targetText)
        assertEquals(GoalPeriod.YEAR, editor.period)
        vm.save()
        advanceUntilIdle()
        assertEquals(Goal(id = 7, sport = null, period = GoalPeriod.YEAR, metric = GoalMetric.DISTANCE, target = 1_000_000.0), goals.saved.last())
        assertEquals("Goal saved", vm.uiState.value.message)

        vm.messageShown()
        vm.editGoal(7)
        advanceUntilIdle()
        vm.setTarget("500")
        vm.save()
        advanceUntilIdle()
        assertEquals(804_672.0, goals.goals.value.single().target, 1e-6)
    }

    @Test
    fun `activities metric is weekly only and falls back to distance`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.addGoal()
        advanceUntilIdle()
        assertTrue(GoalMetric.COUNT in vm.uiState.value.editor!!.metrics)
        vm.setMetric(GoalMetric.COUNT)
        vm.setTarget("4")
        vm.setPeriod(GoalPeriod.MONTH)
        advanceUntilIdle()
        val editor = vm.uiState.value.editor!!
        assertEquals(GoalMetric.DISTANCE, editor.metric)
        assertEquals("", editor.targetText)
        assertFalse(GoalMetric.COUNT in editor.metrics)

        // Choosing it outside weekly is ignored; other metrics survive a period change.
        vm.setMetric(GoalMetric.COUNT)
        vm.setMetric(GoalMetric.ELEVATION)
        vm.setTarget("1000")
        vm.setPeriod(GoalPeriod.YEAR)
        advanceUntilIdle()
        assertEquals(GoalMetric.ELEVATION, vm.uiState.value.editor!!.metric)
        assertEquals("1000", vm.uiState.value.editor!!.targetText)
    }

    @Test
    fun `delete removes the goal being edited`() = runTest(main.dispatcher) {
        goals.goals.value = listOf(
            Goal(id = 1, sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.COUNT, target = 3.0),
            Goal(id = 2, sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.TIME, target = 7_200.0),
        )
        val vm = viewModel()
        collect(vm)
        vm.editGoal(1)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.uiState.value.goals.map { it.id })
        assertNull(vm.uiState.value.editor)
        assertEquals("Goal deleted", vm.uiState.value.message)
    }

    @Test
    fun `no goals means an empty list, not samples`() = runTest(main.dispatcher) {
        val vm = viewModel()
        collect(vm)
        assertTrue(vm.uiState.value.goals.isEmpty())
        assertEquals("No streak yet", vm.uiState.value.streak?.title)
    }

    @Test
    fun `editable text rounds to two decimals`() {
        assertEquals("30", GoalsViewModel.editableText(30.0))
        assertEquals("18.64", GoalsViewModel.editableText(18.641135))
        assertEquals("2.5", GoalsViewModel.editableText(2.5))
    }
}
