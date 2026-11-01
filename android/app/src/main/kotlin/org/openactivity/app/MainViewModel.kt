// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.OnboardingRepository
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.model.UserPreferences
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.LaunchTab
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

/**
 * Exposes app-wide preferences (theme, reduced motion), the first-run gate and the tab to open on to
 * [MainActivity].
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    onboardingRepository: OnboardingRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    /**
     * The tab chosen in settings, read once per process: changing the setting takes effect on the next
     * launch rather than yanking the open app to another tab. Null until read.
     */
    val launchTab: StateFlow<LaunchTab?> = flow { emit(settingsRepository.settings.first().launchTab) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val start: StateFlow<AppStart> = combine(onboardingRepository.isComplete, launchTab) { done, tab ->
        when {
            !done -> AppStart.Onboarding
            tab == null -> AppStart.Loading
            else -> AppStart.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppStart.Loading)
}
