// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openactivity.app.BuildConfig
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.model.UserPreferences
import javax.inject.Inject

/** State of the Settings screen. */
data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val distribution: Distribution,
    val versionName: String,
)

/** Settings: units, appearance, reduced motion, and build information. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    distribution: Distribution,
) : ViewModel() {

    private val initial = SettingsUiState(distribution = distribution, versionName = BuildConfig.VERSION_NAME)

    val uiState: StateFlow<SettingsUiState> = preferencesRepository.preferences
        .map { initial.copy(preferences = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch { preferencesRepository.setUnitSystem(unitSystem) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(themeMode) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReduceMotion(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
