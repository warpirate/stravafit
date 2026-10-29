// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.model.UserPreferences
import javax.inject.Inject

/** Exposes app-wide preferences (theme, reduced motion) to [MainActivity]. */
@HiltViewModel
class MainViewModel @Inject constructor(preferencesRepository: PreferencesRepository) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
}
