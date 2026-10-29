// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.OnboardingRepository
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.model.UserPreferences
import javax.inject.Inject

/** What the activity shows first. */
enum class AppStart {
    /** Stored state not read yet; the window background shows. */
    Loading,

    /** First run: onboarding before the app. */
    Onboarding,

    /** The app with its tabs. */
    Ready,
}

/** Exposes app-wide preferences (theme, reduced motion) and the first-run gate to [MainActivity]. */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    onboardingRepository: OnboardingRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val start: StateFlow<AppStart> = onboardingRepository.isComplete
        .map { done -> if (done) AppStart.Ready else AppStart.Onboarding }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStart.Loading)
}
