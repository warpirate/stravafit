// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.onboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.data.LocalOnboardingRepository
import org.openactivity.app.data.OnboardingChoices
import org.openactivity.app.data.OnboardingRepository
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.InMemoryPreferencesDataStore
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class Recording : OnboardingRepository {
        val choices = mutableListOf<OnboardingChoices>()
        override val isComplete = MutableStateFlow(false)
        override suspend fun complete(choices: OnboardingChoices) {
            this.choices += choices
            isComplete.value = true
        }
    }

    @Test
    fun `walks through the steps and saves the choices`() = runTest(main.dispatcher) {
        val repo = Recording()
        val vm = OnboardingViewModel(repo)
        assertEquals(UnitSystem.METRIC, vm.uiState.value.units) // UK locale from the rule
        assertFalse(vm.back())
        vm.setName("Asha")
        vm.next()
        vm.setUnits(UnitSystem.IMPERIAL)
        vm.next()
        vm.setSport(SportType.RIDE)
        vm.next()
        assertTrue(vm.uiState.value.isLastStep)
        vm.setVisibility(Visibility.ONLY_ME)
        vm.setHideRadius(400.0)
        assertTrue(vm.back())
        assertEquals(OnboardingStep.Sport, vm.uiState.value.step)
        vm.next()
        vm.next()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        assertEquals(listOf(OnboardingChoices("Asha", UnitSystem.IMPERIAL, SportType.RIDE, Visibility.ONLY_ME, 400.0)), repo.choices)
    }

    @Test
    fun `defaults are privacy first`() {
        val state = OnboardingUiState()
        assertEquals(Visibility.FOLLOWERS, state.visibility)
        assertEquals(200.0, state.hideRadiusMeters, 0.0)
    }

    @Test
    fun `local repository writes settings, profile and the done flag`() = runTest(main.dispatcher) {
        val settings = FakeSettingsRepository()
        val local = LocalPreferences(InMemoryPreferencesDataStore())
        val repo = LocalOnboardingRepository(settings, local)
        assertFalse(repo.isComplete.first())
        repo.complete(OnboardingChoices(" Asha ", UnitSystem.IMPERIAL, SportType.HIKE, Visibility.EVERYONE, 0.0))
        assertTrue(repo.isComplete.first())
        assertEquals("Asha", local.profile.first().name)
        val s = settings.state.value
        assertEquals(UnitSystem.IMPERIAL, s.units)
        assertEquals(SportType.HIKE, s.defaultSport)
        assertEquals(Visibility.EVERYONE, s.defaultVisibility)
        assertEquals(0.0, s.privacy.hideStartMeters, 0.0)
    }
}
